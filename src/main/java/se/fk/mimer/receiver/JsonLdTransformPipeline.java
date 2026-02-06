package se.fk.mimer.receiver;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PoC pipeline: raw JSON -> JSON-LD expansion -> optional framing -> RML -> RDF.
 * Optional output: Cypher for app-level import or n10s import helper.
 */
public final class JsonLdTransformPipeline {
    private static final Path DEFAULT_CONTEXT = Path.of("src/main/resources/context/ffa-1.0.jsonld");
    private static final Path DEFAULT_FRAME = Path.of("src/main/resources/frame/ffa-frame.jsonld");
    private static final Path DEFAULT_SDL = Path.of("src/main/resources/schema/ffa.graphqls");

    private JsonLdTransformPipeline() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            System.exit(2);
        }

        Args parsed = Args.parse(args);
        Path rawJson = parsed.rawJson();
        Path context = parsed.context().orElse(DEFAULT_CONTEXT);
        Path frame = parsed.frame().orElse(DEFAULT_FRAME);
        Path output = parsed.output().orElse(Path.of("target/ffa-out.ttl"));
        Path cypherOut = parsed.cypherOut().orElse(Path.of("target/ffa-out.cypher"));

        JsonArray expanded = expandJsonLd(rawJson, context);
        if (DEFAULT_SDL.toFile().exists()) {
            expanded = JsonLdTypeMapper.replaceTypes(expanded, DEFAULT_SDL);
        }
        expanded = JsonLdValueTypeNormalizer.ensureStringTypes(expanded);

        Path workDir = output.toAbsolutePath().getParent();
        if (workDir != null) {
            Files.createDirectories(workDir);
        }

        Path expandedFile = output.resolveSibling("expanded.json");
        writePrettyJson(expanded, expandedFile);

        JsonStructure framed = expanded;
        if (frame.toFile().exists()) {
            framed = frameJsonLd(expandedFile, frame, context);
        }

        framed = normalizeTypeAliases(framed, Map.of("Roll", "roll"));
        validateCompactIris(framed, loadPrefixSet(context));

        Path framedFile = output.resolveSibling("framed.json");
        writePrettyJson(ensureGraphWrapped(framed), framedFile);

        switch (parsed.importMode()) {
            case NEO4J -> writeNeo4jImportHelper(output, parsed.neo4jOpts());
            case CYPHER -> writeCypher(ensureGraphWrapped(framed), cypherOut);
            case NONE -> {}
        }
    }

    private static void usage() {
        System.err.println("Usage: JsonLdTransformPipeline <raw-json> " +
                "[--context path] [--frame path] [--mapping path] [--out path] " +
                "[--import neo4j|cypher|none] [--cypher-out path] [--neo4j-opts key=value,...]");
    }

    private static JsonArray expandJsonLd(Path jsonFile, Path contextFile) throws Exception {
        if (contextFile.toFile().exists()) {
            JsonLdOptions options = new JsonLdOptions();
            options.setDocumentLoader(localOnlyLoader(contextFile));
            return JsonLd.expand(jsonFile.toUri().toString())
                    .options(options)
                    .context(contextFile.toUri().toString())
                    .get();
        }
        JsonLdOptions options = new JsonLdOptions();
        options.setDocumentLoader(localOnlyLoader(null));
        return JsonLd.expand(jsonFile.toUri().toString())
                .options(options)
                .get();
    }

    private static JsonStructure frameJsonLd(Path expandedFile, Path frameFile, Path contextFile)
            throws Exception {
        JsonLdOptions options = new JsonLdOptions();
        options.setDocumentLoader(localOnlyLoader(contextFile.toFile().exists() ? contextFile : null));
        return JsonLd.frame(expandedFile.toUri().toString(), frameFile.toUri().toString())
                .options(options)
                .get();
    }

    private static DocumentLoader localOnlyLoader(Path contextFile) {
        String contextUri = contextFile != null ? contextFile.toUri().toString() : null;
        return (uri, options) -> loadLocalDocument(uri, options, contextUri, contextFile);
    }

    private static com.apicatalog.jsonld.document.Document loadLocalDocument(
            java.net.URI uri,
            DocumentLoaderOptions _options,
            String contextUri,
            Path contextFile
    ) throws JsonLdError {
        String uriString = uri.toString();
        try {
            if (contextUri != null && uriString.equals(contextUri)) {
                return JsonDocument.of(Files.newInputStream(contextFile));
            }
            if (uriString.startsWith("https://data.fk.se/kontext/")) {
                if (contextFile == null) {
                    throw new JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                            "Remote context not allowed and no local context provided: " + uri);
                }
                return JsonDocument.of(Files.newInputStream(contextFile));
            }
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                return JsonDocument.of(Files.newInputStream(Path.of(uri)));
            }
        } catch (IOException e) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED,
                    "Failed to load local document: " + uri, e);
        }
        throw new JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                "Remote context not allowed in demo: " + uri);
    }

    private static JsonStructure ensureGraphWrapped(JsonStructure json) {
        if (json.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject object = json.asJsonObject();
            if (object.containsKey("@graph")) {
                return json;
            }
            JsonArrayBuilder builder = Json.createArrayBuilder();
            builder.add(object);
            JsonObjectBuilder wrapped = Json.createObjectBuilder();
            wrapped.add("@graph", builder);
            return wrapped.build();
        }
        if (json.getValueType() == JsonValue.ValueType.ARRAY) {
            JsonObjectBuilder wrapped = Json.createObjectBuilder();
            wrapped.add("@graph", json);
            return wrapped.build();
        }
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("@graph", json);
        return builder.build();
    }

    private static JsonStructure normalizeTypeAliases(JsonStructure json, Map<String, String> aliases) {
        return (JsonStructure) normalizeTypeValue(json, aliases);
    }

    private static JsonValue normalizeTypeValue(JsonValue value, Map<String, String> aliases) {
        return switch (value.getValueType()) {
            case OBJECT -> {
                JsonObject object = value.asJsonObject();
                JsonObjectBuilder builder = Json.createObjectBuilder();
                for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
                    if ("@type".equals(entry.getKey())) {
                        builder.add(entry.getKey(), normalizeTypeToken(entry.getValue(), aliases));
                    } else {
                        builder.add(entry.getKey(), normalizeTypeValue(entry.getValue(), aliases));
                    }
                }
                yield builder.build();
            }
            case ARRAY -> {
                JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                for (JsonValue item : value.asJsonArray()) {
                    arrayBuilder.add(normalizeTypeValue(item, aliases));
                }
                yield arrayBuilder.build();
            }
            default -> value;
        };
    }

    private static JsonValue normalizeTypeToken(JsonValue value, Map<String, String> aliases) {
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            String raw = ((JsonString) value).getString();
            String mapped = aliases.getOrDefault(raw, raw);
            return Json.createValue(mapped);
        }
        if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            JsonArrayBuilder builder = Json.createArrayBuilder();
            for (JsonValue item : value.asJsonArray()) {
                if (item.getValueType() == JsonValue.ValueType.STRING) {
                    String raw = ((JsonString) item).getString();
                    builder.add(aliases.getOrDefault(raw, raw));
                } else {
                    builder.add(item);
                }
            }
            return builder.build();
        }
        return value;
    }

    private static void validateCompactIris(JsonValue value, Set<String> prefixes) {
        switch (value.getValueType()) {
            case OBJECT -> {
                JsonObject object = value.asJsonObject();
                for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
                    validateKeyPrefix(entry.getKey(), prefixes);
                    validateCompactIris(entry.getValue(), prefixes);
                }
            }
            case ARRAY -> {
                for (JsonValue item : value.asJsonArray()) {
                    validateCompactIris(item, prefixes);
                }
            }
            case STRING -> validateStringPrefix(((JsonString) value).getString(), prefixes);
            default -> {}
        }
    }

    private static void validateKeyPrefix(String key, Set<String> prefixes) {
        if (key.startsWith("@")) {
            return;
        }
        int colon = key.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String prefix = key.substring(0, colon);
        if (!prefixes.contains(prefix)) {
            throw new IllegalArgumentException("Undefined prefix in JSON-LD key: " + key);
        }
    }

    private static void validateStringPrefix(String value, Set<String> prefixes) {
        if (isAbsoluteIri(value)) {
            return;
        }
        if (value.startsWith("_:")) {
            // Blank node identifiers are valid without a prefix declaration.
            return;
        }
        if (value.isEmpty()) {
            return;
        }
        char first = value.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            // Only validate values that look like a CURIE prefix start.
            return;
        }
        int colon = value.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String prefix = value.substring(0, colon);
        if (!prefixes.contains(prefix)) {
            throw new IllegalArgumentException("Undefined prefix in JSON-LD value: " + value);
        }
    }

    private static boolean isAbsoluteIri(String value) {
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("urn:");
    }

    private static Set<String> loadPrefixSet(Path contextFile) {
        if (contextFile == null || !contextFile.toFile().exists()) {
            return Set.of();
        }
        try (var stream = Files.newInputStream(contextFile)) {
            JsonObject root = Json.createReader(stream).readObject();
            JsonValue ctxValue = root.get("@context");
            if (ctxValue == null || ctxValue.getValueType() != JsonValue.ValueType.OBJECT) {
                return Set.of();
            }
            JsonObject context = ctxValue.asJsonObject();
            Set<String> prefixes = new java.util.HashSet<>();
            for (Map.Entry<String, JsonValue> entry : context.entrySet()) {
                String key = entry.getKey();
                JsonValue val = entry.getValue();
                if (val.getValueType() == JsonValue.ValueType.STRING) {
                    String iri = ((JsonString) val).getString();
                    if (iri.contains(":")) {
                        prefixes.add(key);
                    }
                } else if (val.getValueType() == JsonValue.ValueType.OBJECT) {
                    JsonObject obj = val.asJsonObject();
                    JsonValue id = obj.get("@id");
                    if (id != null && id.getValueType() == JsonValue.ValueType.STRING) {
                        String iri = ((JsonString) id).getString();
                        int colon = iri.indexOf(':');
                        if (colon > 0) {
                            prefixes.add(iri.substring(0, colon));
                        }
                    }
                }
            }
            return prefixes;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read context file: " + contextFile, e);
        }
    }

    private static void writePrettyJson(JsonStructure json, Path out) throws IOException {
        Map<String, Object> config = Map.of(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory factory = Json.createWriterFactory(config);
        try (var stream = Files.newOutputStream(out);
             JsonWriter writer = factory.createWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            writer.write(json);
        }
    }

    private static void writeNeo4jImportHelper(Path rdfFile, Map<String, String> opts) throws IOException {
        Path out = rdfFile.resolveSibling("neo4j-import.cypher");
        String rdfUri = rdfFile.toAbsolutePath().toUri().toString();
        Map<String, String> options = new LinkedHashMap<>();
        options.put("commitSize", "10000");
        if (opts != null) {
            options.putAll(opts);
        }
        String cypher = "CALL n10s.rdf.import.fetch(\"" + rdfUri +
                "\",\"Turtle\"," + cypherMapLiteral(options) + ");\n";
        Files.writeString(out, cypher, StandardCharsets.UTF_8);
    }

    private static void writeCypher(JsonStructure framed, Path out) throws IOException {
        JsonArray nodes = extractGraphNodes(framed);
        List<String> statements = new ArrayList<>();
        for (JsonValue value : nodes) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            JsonObject node = value.asJsonObject();
            String id = nodeId(node).orElse(null);
            if (id == null) {
                continue;
            }
            List<String> labels = typeLabels(node);
            Map<String, Object> props = literalProperties(node);

            StringBuilder stmt = new StringBuilder();
            stmt.append("MERGE (n:Resource {id: ").append(cypherValue(id)).append("})\n");
            if (!labels.isEmpty()) {
                stmt.append("SET n:");
                stmt.append(String.join(":", labels));
                stmt.append("\n");
            }
            if (!props.isEmpty()) {
                stmt.append("SET n += ").append(cypherMap(props)).append("\n");
            }
            statements.add(stmt.toString());

            for (Relationship rel : relationships(node)) {
                String relStmt = "MERGE (m:Resource {id: " + cypherValue(rel.targetId()) + "})\n" +
                        "MERGE (n:Resource {id: " + cypherValue(id) + "})\n" +
                        "MERGE (n)-[:" + rel.type() + "]->(m)\n";
                statements.add(relStmt);
            }
        }
        Files.writeString(out, String.join("\n", statements), StandardCharsets.UTF_8);
    }

    private static JsonArray extractGraphNodes(JsonStructure json) {
        if (json.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject object = json.asJsonObject();
            if (object.containsKey("@graph") && object.get("@graph").getValueType() == JsonValue.ValueType.ARRAY) {
                return object.getJsonArray("@graph");
            }
            JsonArrayBuilder builder = Json.createArrayBuilder();
            builder.add(object);
            return builder.build();
        }
        return json.asJsonArray();
    }

    private static Optional<String> nodeId(JsonObject node) {
        JsonValue id = node.get("@id");
        if (id == null || id.getValueType() != JsonValue.ValueType.STRING) {
            return Optional.empty();
        }
        return Optional.of(((JsonString) id).getString());
    }

    private static List<String> typeLabels(JsonObject node) {
        List<String> labels = new ArrayList<>();
        JsonValue type = node.get("@type");
        if (type == null) {
            return labels;
        }
        if (type.getValueType() == JsonValue.ValueType.STRING) {
            labels.add(labelFromIri(((JsonString) type).getString()));
        } else if (type.getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue v : node.getJsonArray("@type")) {
                if (v.getValueType() == JsonValue.ValueType.STRING) {
                    labels.add(labelFromIri(((JsonString) v).getString()));
                }
            }
        }
        return labels;
    }

    private static Map<String, Object> literalProperties(JsonObject node) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : node.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("@")) {
                continue;
            }
            JsonValue value = entry.getValue();
            Object literal = coerceLiteral(value);
            if (literal != null) {
                props.put(propertyKey(key), literal);
            }
        }
        return props;
    }

    private static List<Relationship> relationships(JsonObject node) {
        List<Relationship> rels = new ArrayList<>();
        for (Map.Entry<String, JsonValue> entry : node.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("@")) {
                continue;
            }
            JsonValue value = entry.getValue();
            rels.addAll(extractRelationships(key, value));
        }
        return rels;
    }

    private static List<Relationship> extractRelationships(String predicate, JsonValue value) {
        List<Relationship> rels = new ArrayList<>();
        if (value.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject obj = value.asJsonObject();
            if (obj.containsKey("@id")) {
                nodeId(obj).ifPresent(id -> rels.add(new Relationship(relType(predicate), id)));
            }
        } else if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue item : value.asJsonArray()) {
                rels.addAll(extractRelationships(predicate, item));
            }
        }
        return rels;
    }

    private static Object coerceLiteral(JsonValue value) {
        return switch (value.getValueType()) {
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> ((JsonNumber) value).bigDecimalValue();
            case TRUE -> true;
            case FALSE -> false;
            case OBJECT -> {
                JsonObject obj = value.asJsonObject();
                if (obj.containsKey("@value")) {
                    JsonValue raw = obj.get("@value");
                    yield coerceLiteral(raw);
                }
                yield null;
            }
            case ARRAY -> {
                List<Object> list = new ArrayList<>();
                for (JsonValue item : value.asJsonArray()) {
                    Object literal = coerceLiteral(item);
                    if (literal != null) {
                        list.add(literal);
                    }
                }
                yield list.isEmpty() ? null : list;
            }
            default -> null;
        };
    }

    private static String cypherMap(Map<String, Object> props) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            parts.add("`" + key + "`: " + cypherValue(value));
        }
        return "{" + String.join(", ", parts) + "}";
    }

    private static String cypherMapLiteral(Map<String, String> props) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            parts.add(key + ": " + cypherLiteral(value));
        }
        return "{" + String.join(", ", parts) + "}";
    }

    private static String cypherValue(Object value) {
        if (value instanceof String) {
            return "\"" + escapeCypher((String) value) + "\"";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                items.add(cypherValue(item));
            }
            return "[" + String.join(", ", items) + "]";
        }
        return value.toString();
    }

    private static String cypherLiteral(String value) {
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            return trimmed;
        }
        return "\"" + escapeCypher(trimmed) + "\"";
    }

    private static String escapeCypher(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String labelFromIri(String iri) {
        return sanitizeLabel(localName(iri));
    }

    private static String propertyKey(String iri) {
        String local = localName(iri);
        return local.replace('-', '_');
    }

    private static String relType(String iri) {
        return sanitizeLabel(localName(iri)).toUpperCase(Locale.ROOT);
    }

    private static String localName(String iri) {
        int hash = iri.lastIndexOf('#');
        if (hash >= 0 && hash + 1 < iri.length()) {
            return iri.substring(hash + 1);
        }
        int slash = iri.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < iri.length()) {
            return iri.substring(slash + 1);
        }
        return iri;
    }

    private static String sanitizeLabel(String label) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        if (sb.length() == 0) {
            return "Resource";
        }
        if (!Character.isLetter(sb.charAt(0))) {
            sb.insert(0, 'N');
        }
        return sb.toString();
    }

    private record Relationship(String type, String targetId) {}

    private enum ImportMode {NEO4J, CYPHER, NONE}

    private record Args(
            Path rawJson,
            Optional<Path> context,
            Optional<Path> frame,
            Optional<Path> mapping,
            Optional<Path> output,
            Optional<Path> cypherOut,
            ImportMode importMode,
            Map<String, String> neo4jOpts
    ) {
        static Args parse(String[] args) {
            Path rawJson = Path.of(args[0]);
            Optional<Path> context = Optional.empty();
            Optional<Path> frame = Optional.empty();
            Optional<Path> mapping = Optional.empty();
            Optional<Path> output = Optional.empty();
            Optional<Path> cypherOut = Optional.empty();
            ImportMode importMode = ImportMode.NEO4J;
            Map<String, String> neo4jOpts = new LinkedHashMap<>();

            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--context" -> context = Optional.of(Path.of(args[++i]));
                    case "--frame" -> frame = Optional.of(Path.of(args[++i]));
                    case "--mapping" -> mapping = Optional.of(Path.of(args[++i]));
                    case "--out" -> output = Optional.of(Path.of(args[++i]));
                    case "--cypher-out" -> cypherOut = Optional.of(Path.of(args[++i]));
                    case "--import" -> importMode = ImportMode.valueOf(args[++i].toUpperCase(Locale.ROOT));
                    case "--neo4j-opts" -> neo4jOpts.putAll(parseOptions(args[++i]));
                    default -> throw new IllegalArgumentException("Unknown arg: " + arg);
                }
            }
            return new Args(rawJson, context, frame, mapping, output, cypherOut, importMode, neo4jOpts);
        }
    }

    private static Map<String, String> parseOptions(String input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input == null || input.isBlank()) {
            return result;
        }
        String[] parts = input.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                result.put(trimmed, "true");
                continue;
            }
            result.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
        }
        return result;
    }
}
