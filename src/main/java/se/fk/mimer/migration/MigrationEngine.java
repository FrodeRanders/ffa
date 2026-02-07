package se.fk.mimer.migration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MigrationEngine {

    private static final String SCHEMA_VERSION_FIELD = "mimer:schemaVersion";

    // Configure Jayway to use Jackson JsonNode
    private static final Configuration JSONPATH_CONF =
            Configuration.builder()
                    .jsonProvider(new JacksonJsonNodeJsonProvider())
                    .mappingProvider(new JacksonMappingProvider())
                    // SUPPRESS_EXCEPTIONS => "missing path" => empty matches rather than throwing
                    .options(Option.AS_PATH_LIST, Option.SUPPRESS_EXCEPTIONS)
                    .build();

    public static final class AuditEntry {
        public final String ruleName;
        public final String jsonPath;
        public final String matchedPath;   // Jayway "$['a'][0]['b']" style
        public final String pointer;       // RFC6901-ish "/a/0/b"
        public final String action;        // e.g., "rename", "replace", "delete"
        public final String details;       // free-form

        public AuditEntry(String ruleName, String jsonPath, String matchedPath, String pointer,
                          String action, String details) {
            this.ruleName = ruleName;
            this.jsonPath = jsonPath;
            this.matchedPath = matchedPath;
            this.pointer = pointer;
            this.action = action;
            this.details = details;
        }

        @Override public String toString() {
            return ruleName + " " + action + " @ " + pointer + " (" + matchedPath + " via " + jsonPath + "): " + details;
        }
    }

    @FunctionalInterface
    public interface Mutator {
        void apply(JsonNode root, Match match, List<AuditEntry> audit);
    }

    public static final class Rule {
        public final String name;
        public final String jsonPath;
        public final Mutator mutator;

        public Rule(String name, String jsonPath, Mutator mutator) {
            this.name = name;
            this.jsonPath = jsonPath;
            this.mutator = mutator;
        }
    }

    public static final class Migration {
        public final String name;
        public final int fromVersion;  // inclusive
        public final int toVersion;    // inclusive (usually from+1)
        public final List<Rule> rules;

        public Migration(String name, int fromVersion, int toVersion, List<Rule> rules) {
            this.name = name;
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.rules = rules;
        }
    }

    public static final class Match {
        public final String ruleName;
        public final String jsonPath;     // selector
        public final String matchedPath;  // Jayway path for this match
        public final String pointer;      // converted to RFC6901 pointer
        public final JsonNode value;      // current value at match

        public Match(String ruleName, String jsonPath, String matchedPath, String pointer, JsonNode value) {
            this.ruleName = ruleName;
            this.jsonPath = jsonPath;
            this.matchedPath = matchedPath;
            this.pointer = pointer;
            this.value = value;
        }
    }

    public static final class Result {
        public final JsonNode root;
        public final List<AuditEntry> audit;

        public Result(JsonNode root, List<AuditEntry> audit) {
            this.root = root;
            this.audit = audit;
        }
    }

    public static int readSchemaVersion(JsonNode root) {
        JsonNode v = root.get(SCHEMA_VERSION_FIELD);
        if (v == null || v.isNull()) return 0;
        if (v.isInt() || v.isLong()) return v.asInt();
        if (v.isString()) {
            try { return Integer.parseInt(v.asString().trim()); } catch (Exception ignored) {}
        }
        return 0;
    }

    public static void writeSchemaVersion(JsonNode root, int version) {
        if (root instanceof ObjectNode obj) obj.put(SCHEMA_VERSION_FIELD, version);
    }

    /**
     * Apply only relevant migrations, in order (up to current schema version).
     * @param root
     * @param migrations
     * @param currentVersion
     * @return
     */
    public Result applyUpToCurrent(JsonNode root, List<Migration> migrations, final int currentVersion) {
        Objects.requireNonNull(root, "root");

        // ensure sorted by fromVersion (defensive copy in case list is immutable)
        List<Migration> ordered = new ArrayList<>(migrations);
        ordered.sort(Comparator.comparingInt(m -> m.fromVersion));

        List<AuditEntry> audit = new ArrayList<>();
        int version = readSchemaVersion(root);

        // parse once for JSONPath selections; note: selections will see mutations since root is mutated in-place
        DocumentContext ctx = JsonPath.using(JSONPATH_CONF).parse(root);

        while (version < currentVersion) {
            final int v = version;
            Migration next = ordered.stream()
                    .filter(m -> m.fromVersion == v)
                    .findFirst()
                    .orElse(null);

            if (next == null) {
                // no migration step defined; stop rather than guessing
                audit.add(new AuditEntry("engine", "$", "$", "/" + SCHEMA_VERSION_FIELD,
                        "halt", "No migration defined from version " + version));
                break;
            }

            for (Rule rule : next.rules) {
                @SuppressWarnings("unchecked")
                List<String> paths = ctx.read(rule.jsonPath, List.class);
                if (paths == null || paths.isEmpty()) continue;

                for (String matchedPath : paths) {
                    String pointer = jaywayPathToPointer(matchedPath);
                    JsonNode value = root.at(pointer);
                    Match match = new Match(rule.name, rule.jsonPath, matchedPath, pointer, value);
                    rule.mutator.apply(root, match, audit);
                }
            }

            version = next.toVersion;
            writeSchemaVersion(root, version);
            audit.add(new AuditEntry(next.name, "$", "$", "/" + SCHEMA_VERSION_FIELD,
                    "set", "Upgraded to version " + version));
        }

        return new Result(root, audit);
    }

    public Result applyAll(JsonNode root, List<Migration> migrations) {
        Objects.requireNonNull(root, "root");
        List<AuditEntry> audit = new ArrayList<>();

        DocumentContext ctx = JsonPath.using(JSONPATH_CONF).parse(root);

        for (Migration mig : migrations) {
            for (Rule rule : mig.rules) {
                // AS_PATH_LIST => returns list of matched paths as strings
                @SuppressWarnings("unchecked")
                List<String> paths = ctx.read(rule.jsonPath, List.class);
                if (paths == null || paths.isEmpty()) continue;

                for (String matchedPath : paths) {
                    String pointer = jaywayPathToPointer(matchedPath);

                    JsonNode v = root.at(pointer);
                    // Note: root.at("/missing") returns MissingNode, still safe
                    Match m = new Match(rule.name, rule.jsonPath, matchedPath, pointer, v);
                    rule.mutator.apply(root, m, audit);
                }
            }
        }
        return new Result(root, audit);
    }

    /**
     * Converts Jayway path like: $['producerade_resultat'][0]['period']['from']
     * to JSON Pointer: /producerade_resultat/0/period/from
     */
    public static String jaywayPathToPointer(String jaywayPath) {
        if (jaywayPath == null || jaywayPath.isBlank() || "$".equals(jaywayPath)) return "";

        // Very small parser: extracts bracket segments ['x'] and [0]
        StringBuilder ptr = new StringBuilder();
        int i = 0;
        while (i < jaywayPath.length()) {
            char c = jaywayPath.charAt(i);
            if (c == '[') {
                int end = jaywayPath.indexOf(']', i);
                if (end < 0) break;
                String inside = jaywayPath.substring(i + 1, end).trim();
                // inside may be: 'field'  OR  0
                String token;
                if (inside.startsWith("'") && inside.endsWith("'") && inside.length() >= 2) {
                    token = inside.substring(1, inside.length() - 1);
                } else {
                    token = inside; // index
                }
                // JSON Pointer escaping: "~"->"~0", "/"->"~1"
                token = token.replace("~", "~0").replace("/", "~1");
                ptr.append('/').append(token);
                i = end + 1;
            } else {
                i++;
            }
        }
        return ptr.toString();
    }

    // ---- Helper: locate parent container and key/index for a pointer ----

    public static final class ParentRef {
        public final JsonNode parent;
        public final String lastToken; // field name or array index string
        public ParentRef(JsonNode parent, String lastToken) {
            this.parent = parent;
            this.lastToken = lastToken;
        }
    }

    public static ParentRef parentRef(JsonNode root, String pointer) {
        if (pointer == null || pointer.isEmpty() || "/".equals(pointer)) {
            return new ParentRef(null, null);
        }
        int lastSlash = pointer.lastIndexOf('/');
        String parentPtr = (lastSlash <= 0) ? "" : pointer.substring(0, lastSlash);
        String last = pointer.substring(lastSlash + 1);
        // unescape pointer tokens
        last = last.replace("~1", "/").replace("~0", "~");
        JsonNode parent = root.at(parentPtr.isEmpty() ? "" : parentPtr);
        return new ParentRef(parent, last);
    }

    // ---- Rule factories (reusable operators) ----

    /**
     * Ensures default if missing / null.
     * @param name
     * @param parentSelector
     * @param field
     * @param defaultValue
     * @return
     */
    public static Rule setDefaultStringIfMissing(String name, String parentSelector, String field, String defaultValue) {
        // parentSelector should match parent objects, e.g. "$.person" or "$..person"
        return new Rule(name, parentSelector, (root, match, audit) -> {
            JsonNode parent = root.at(match.pointer);
            if (parent instanceof ObjectNode obj) {
                JsonNode cur = obj.get(field);
                if (cur == null || cur.isNull()) {
                    obj.put(field, defaultValue);
                    audit.add(new AuditEntry(name, parentSelector, match.matchedPath, match.pointer + "/" + field,
                            "setDefault", "set '" + field + "' to '" + defaultValue + "'"));
                }
            }
        });
    }

    public static Rule setString(String name, String jsonPathSelect, String pointerToSet, String value) {
        return new Rule(name, jsonPathSelect, (root, match, audit) -> {
            if (!(root instanceof ObjectNode)) return;
            // pointerToSet is absolute pointer to field; we set into its parent
            ParentRef pr = parentRef(root, pointerToSet);
            if (pr.parent instanceof ObjectNode obj) {
                obj.put(pr.lastToken, value);
                audit.add(new AuditEntry(name, jsonPathSelect, match.matchedPath, pointerToSet,
                        "set", "set string to '" + value + "'"));
            }
        });
    }

    public static Rule renameField(String name, String jsonPathSelect, String newFieldName) {
        // jsonPathSelect should point to the *field value* (e.g. "$.person.typ")
        return new Rule(name, jsonPathSelect, (root, match, audit) -> {
            ParentRef pr = parentRef(root, match.pointer);
            if (pr.parent instanceof ObjectNode obj) {
                JsonNode oldValue = obj.get(pr.lastToken);
                if (oldValue == null) return;
                obj.set(newFieldName, oldValue);
                obj.remove(pr.lastToken);
                audit.add(new AuditEntry(name, jsonPathSelect, match.matchedPath, match.pointer,
                        "rename", pr.lastToken + " -> " + newFieldName));
            }
        });
    }

    /**
     * Ensures field is an array (wrap scalar/object into [x]).
     * @param name
     * @param selectField
     * @return
     */
    public static Rule ensureArray(String name, String selectField) {
        // selectField points to the field itself, e.g. "$.addresses"
        return new Rule(name, selectField, (root, match, audit) -> {
            ParentRef pr = parentRef(root, match.pointer);
            if (!(pr.parent instanceof ObjectNode obj)) return;

            JsonNode v = obj.get(pr.lastToken);
            if (v == null || v.isNull()) return;
            if (v.isArray()) return;

            var arr = obj.arrayNode();
            arr.add(v);
            obj.set(pr.lastToken, arr);

            audit.add(new AuditEntry(name, selectField, match.matchedPath, match.pointer,
                    "ensureArray", "wrapped non-array into array"));
        });
    }

    /**
     * Ensures field is an object (wrap scalar into { "value": x }).
     * @param name
     * @param selectField
     * @param valueKey
     * @return
     */
    public static Rule ensureObjectWithValueKey(String name, String selectField, String valueKey) {
        return new Rule(name, selectField, (root, match, audit) -> {
            ParentRef pr = parentRef(root, match.pointer);
            if (!(pr.parent instanceof ObjectNode obj)) return;

            JsonNode v = obj.get(pr.lastToken);
            if (v == null || v.isNull()) return;
            if (v.isObject()) return;

            ObjectNode o = obj.objectNode();
            o.set(valueKey, v);
            obj.set(pr.lastToken, o);

            audit.add(new AuditEntry(name, selectField, match.matchedPath, match.pointer,
                    "ensureObject", "wrapped into object with key '" + valueKey + "'"));
        });
    }

    /**
     * Normalize date-time string to date.
     * @param name
     * @param jsonPathSelect
     * @return
     */
    public static Rule normalizeInstantZToDate(String name, String jsonPathSelect) {
        Pattern p = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T.*Z$");
        return new Rule(name, jsonPathSelect, (root, match, audit) -> {
            if (!match.value.isString()) return;
            String s = match.value.asString();
            if (!p.matcher(s).matches() || s.length() < 10) return;

            ParentRef pr = parentRef(root, match.pointer);
            if (pr.parent instanceof ObjectNode obj) {
                String d = s.substring(0, 10);
                obj.put(pr.lastToken, d);
                audit.add(new AuditEntry(name, jsonPathSelect, match.matchedPath, match.pointer,
                        "replace", "instantZ -> date '" + d + "'"));
            }
        });
    }

    public static Rule delete(String name, String jsonPathSelect) {
        return new Rule(name, jsonPathSelect, (root, match, audit) -> {
            ParentRef pr = parentRef(root, match.pointer);
            if (pr.parent instanceof ObjectNode obj) {
                obj.remove(pr.lastToken);
                audit.add(new AuditEntry(name, jsonPathSelect, match.matchedPath, match.pointer,
                        "delete", "removed field"));
            } else if (pr.parent instanceof tools.jackson.databind.node.ArrayNode arr) {
                // if pointer points to an array element
                try {
                    int idx = Integer.parseInt(pr.lastToken);
                    if (idx >= 0 && idx < arr.size()) {
                        arr.remove(idx);
                        audit.add(new AuditEntry(name, jsonPathSelect, match.matchedPath, match.pointer,
                                "delete", "removed array element"));
                    }
                } catch (NumberFormatException ignored) { }
            }
        });
    }

    /**
     * Moves/renames by pointer math (field-level move).
     * This is “rename” plus “move between objects” when the match is a field value.
     * @param name
     * @param selectFieldValue
     * @param targetFieldName
     * @return
     */
    public static Rule moveFieldToSibling(String name, String selectFieldValue, String targetFieldName) {
        // example: select "$.person.typ" and move to "$.person.roll"
        return new Rule(name, selectFieldValue, (root, match, audit) -> {
            ParentRef pr = parentRef(root, match.pointer);
            if (!(pr.parent instanceof ObjectNode obj)) return;

            JsonNode val = obj.get(pr.lastToken);
            if (val == null) return;

            obj.set(targetFieldName, val);
            obj.remove(pr.lastToken);

            audit.add(new AuditEntry(name, selectFieldValue, match.matchedPath, match.pointer,
                    "moveField", pr.lastToken + " -> " + targetFieldName));
        });
    }

    /**
     * Copies field (and keeps original).
     * @param name
     * @param selectFieldValue
     * @param targetFieldName
     * @return
     */
    public static Rule copyFieldToSibling(String name, String selectFieldValue, String targetFieldName) {
        return new Rule(name, selectFieldValue, (root, match, audit) -> {
            ParentRef pr = parentRef(root, match.pointer);
            if (!(pr.parent instanceof ObjectNode obj)) return;

            JsonNode val = obj.get(pr.lastToken);
            if (val == null) return;

            obj.set(targetFieldName, val.deepCopy());
            audit.add(new AuditEntry(name, selectFieldValue, match.matchedPath, match.pointer,
                    "copyField", pr.lastToken + " -> " + targetFieldName));
        });
    }

    /**
     * Coerce type: string to int
     * @param name
     * @param selectField
     * @return
     */
    public static Rule coerceStringToInt(String name, String selectField) {
        return new Rule(name, selectField, (root, match, audit) -> {
            if (!match.value.isString()) return;
            String s = match.value.asString().trim();
            try {
                int x = Integer.parseInt(s);
                ParentRef pr = parentRef(root, match.pointer);
                if (pr.parent instanceof ObjectNode obj) {
                    obj.put(pr.lastToken, x);
                    audit.add(new AuditEntry(name, selectField, match.matchedPath, match.pointer,
                            "coerce", "string -> int (" + x + ")"));
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Coerce type: string to boolean
     * @param name
     * @param selectField
     * @return
     */
    public static Rule coerceStringToBoolean(String name, String selectField) {
        return new Rule(name, selectField, (root, match, audit) -> {
            if (!match.value.isString()) return;
            String s = match.value.asString().trim().toLowerCase(Locale.ROOT);
            Boolean b = null;
            if (s.equals("true") || s.equals("yes") || s.equals("1")) b = true;
            if (s.equals("false") || s.equals("no") || s.equals("0")) b = false;
            if (b == null) return;

            ParentRef pr = parentRef(root, match.pointer);
            if (pr.parent instanceof ObjectNode obj) {
                obj.put(pr.lastToken, b);
                audit.add(new AuditEntry(name, selectField, match.matchedPath, match.pointer,
                        "coerce", "string -> boolean (" + b + ")"));
            }
        });
    }

    /**
     * Maps values (enum rename / vocabulary migration).
     * @param name
     * @param selectField
     * @param mapping
     * @return
     */
    public static Rule mapStringValues(String name, String selectField, Map<String,String> mapping) {
        return new Rule(name, selectField, (root, match, audit) -> {
            if (!match.value.isString()) return;
            String s = match.value.asString();
            String repl = mapping.get(s);
            if (repl == null) return;

            ParentRef pr = parentRef(root, match.pointer);
            if (pr.parent instanceof ObjectNode obj) {
                obj.put(pr.lastToken, repl);
                audit.add(new AuditEntry(name, selectField, match.matchedPath, match.pointer,
                        "mapValue", "'" + s + "' -> '" + repl + "'"));
            }
        });
    }


    /**
     * Splits one field into two (regex capture).
     * Example: "belopp": "123 SEK" -> { "varde": 123, "valuta":"SEK" } (actually "iso4217:SEK")
     * @param name
     * @param selectField
     * @param pattern
     * @param group1Key
     * @param group2Key
     * @return
     */
    public static Rule splitStringToObject(String name, String selectField, Pattern pattern,
                                           String group1Key, String group2Key) {
        // pattern should have at least 2 capture groups
        return new Rule(name, selectField, (root, match, audit) -> {
            if (!match.value.isString()) return;
            String s = match.value.asString();
            Matcher m = pattern.matcher(s);
            if (!m.matches()) return;

            ObjectNode o = ((ObjectNode) root).objectNode();
            o.put(group1Key, m.group(1));
            o.put(group2Key, m.group(2));

            ParentRef pr = parentRef(root, match.pointer);
            if (pr.parent instanceof ObjectNode obj) {
                obj.set(pr.lastToken, o);
                audit.add(new AuditEntry(name, selectField, match.matchedPath, match.pointer,
                        "split", "split into object {" + group1Key + "," + group2Key + "}"));
            }
        });
    }

    /**
     * Merges fields into one (template).
     * @param name
     * @param parentSelector
     * @param outField
     * @param f1
     * @param f2
     * @param separator
     * @param removeInputs
     * @return
     */
    public static Rule mergeFieldsToString(String name, String parentSelector,
                                           String outField, String f1, String f2, String separator,
                                           boolean removeInputs) {
        return new Rule(name, parentSelector, (root, match, audit) -> {
            JsonNode parent = root.at(match.pointer);
            if (!(parent instanceof ObjectNode obj)) return;

            JsonNode a = obj.get(f1);
            JsonNode b = obj.get(f2);
            if (a == null || b == null || !a.isString() || !b.isString()) return;

            String merged = a.asString() + separator + b.asString();
            obj.put(outField, merged);
            if (removeInputs) { obj.remove(f1); obj.remove(f2); }

            audit.add(new AuditEntry(name, parentSelector, match.matchedPath, match.pointer + "/" + outField,
                    "merge", "merged " + f1 + " and " + f2));
        });
    }

    /**
     * De-duplicates array elements (by a key field).
     * @param name
     * @param selectArrayField
     * @param keyField
     * @return
     */
    public static Rule dedupeArrayByKey(String name, String selectArrayField, String keyField) {
        return new Rule(name, selectArrayField, (root, match, audit) -> {
            ParentRef pr = parentRef(root, match.pointer);
            if (!(pr.parent instanceof ObjectNode obj)) return;

            JsonNode arrN = obj.get(pr.lastToken);
            if (arrN == null || !arrN.isArray()) return;

            var arr = (tools.jackson.databind.node.ArrayNode) arrN;
            Set<String> seen = new HashSet<>();
            tools.jackson.databind.node.ArrayNode out = obj.arrayNode();

            for (JsonNode e : arr) {
                if (e != null && e.isObject()) {
                    JsonNode k = e.get(keyField);
                    String ks = (k != null && k.isValueNode()) ? k.asString() : null;
                    if (ks != null) {
                        if (seen.add(ks)) out.add(e);
                        continue;
                    }
                }
                // if no key, keep as-is
                out.add(e);
            }

            obj.set(pr.lastToken, out);
            audit.add(new AuditEntry(name, selectArrayField, match.matchedPath, match.pointer,
                    "dedupe", "deduped array by key '" + keyField + "'"));
        });
    }
}
