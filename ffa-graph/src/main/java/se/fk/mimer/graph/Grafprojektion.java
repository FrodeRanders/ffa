package se.fk.mimer.graph;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * En projektion, där valda livscykelobjekt blir noder och deras inbäddning
 * blir relationer. Endast centralt valda egenskaper blir sökbara.
 */
public final class Grafprojektion {
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private final JsonNode mapping;

    public Grafprojektion() {
        try (var input = Grafprojektion.class.getResourceAsStream("/graph-mapping.json")) {
            if (input == null)
                throw new IllegalStateException("Grafmappning saknas");
            mapping = JSON.readTree(input);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Grafmappning kunde inte läsas", e);
        }
    }

    /**
     * Skapar Cypher från ett underlag som infrastrukturen redan har verifierat.
     * Hela snapshoten tillämpas i en fråga efter låsning och versionskontroll av roten.
     * Endast rotens egna projekterade relationer ersätts; övergivna noder behålls.
     */
    public String projektera(byte[] json) {
        JsonNode root = JSON.readTree(json);
        if (!root.isObject())
            throw new IllegalArgumentException("Underlaget måste vara ett objekt");

        List<Nod> noder = new ArrayList<>();
        List<Kant> relationer = new ArrayList<>();
        collect(root, null, null, noder, relationer, new HashSet<>());
        if (noder.isEmpty() || !root.has("id") || !root.has("version"))
            throw new IllegalArgumentException("Roten måste vara ett livscykelobjekt");

        var rot = noder.getFirst();
        StringBuilder cypher = new StringBuilder("// Kör hela projektionen som en enda Cypher-fråga.\n");
        cypher.append("MERGE (root:FfaObjekt {id: ").append(literal(rot.id())).append("})\n")
                // Explicit skrivlås före versionsläsningen; hålls till transaktionens slut.
                .append("SET root._ffaLock = true REMOVE root._ffaLock\n")
                .append("WITH root WHERE coalesce(root.version, -1) < ").append(rot.version()).append("\n")
                .append("OPTIONAL MATCH ()-[old]->() WHERE old.ffaProjectionOwner = ")
                .append(literal(rot.id())).append("\nDELETE old\nWITH DISTINCT root\n");
        for (int i = 0; i < noder.size(); i++) {
            var nod = noder.get(i);
            String variable = "n" + i;
            cypher.append("MERGE (").append(variable).append(":FfaObjekt {id: ")
                    .append(literal(nod.id())).append("})\n")
                    .append("SET ").append(variable).append(":").append(identifier(nod.label())).append("\n")
                    .append("FOREACH (_ IN CASE WHEN coalesce(").append(variable)
                    .append(".version, -1) < ").append(nod.version()).append(" THEN [1] ELSE [] END | SET ")
                    .append(variable).append(" = ").append(nod.properties()).append(")\n");
        }
        for (var kant : relationer) {
            int from = index(noder, kant.fran());
            int to = index(noder, kant.till());
            cypher.append("MERGE (n").append(from).append(")-[:").append(identifier(kant.typ()))
                    .append(" {ffaProjectionOwner: ").append(literal(rot.id())).append("}]->(n")
                    .append(to).append(")\n");
        }
        return cypher.append("RETURN root.id AS objektId, root.version AS version;\n").toString();
    }

    /** Krävs vid import för att samtidiga MERGE-anrop inte ska skapa samma identitet två gånger. */
    public static String schema() {
        return "CREATE CONSTRAINT ffa_objekt_id IF NOT EXISTS FOR (n:FfaObjekt) REQUIRE n.id IS UNIQUE";
    }

    private record Nod(String id, int version, String label, String properties) {}
    private record Kant(String fran, String till, String typ) {}

    private static int index(List<Nod> noder, String id) {
        for (int i = 0; i < noder.size(); i++) if (noder.get(i).id().equals(id)) return i;
        throw new IllegalArgumentException("Relationens ändpunkt saknas");
    }

    private static String literal(Object value) { return JSON.writeValueAsString(value); }

    /** Följer inbäddningen och knyter varje livscykelnod till närmaste ägarnod. */
    private void collect(JsonNode node, String parent, String relation,
                        List<Nod> nodes, List<Kant> relations, Set<String> ids) {
        if (node.isArray()) {
            for (JsonNode child : node) collect(child, parent, relation, nodes, relations, ids);
            return;
        }
        if (!node.isObject()) return;

        String owner = parent;
        if (node.has("id") && node.has("version")) {
            String type = node.path("@type").asString();
            JsonNode rule = mapping.get(type);
            if (rule == null) throw new IllegalArgumentException("Ingen grafmappning för " + type);
            String id = node.path("id").asString();
            if (id.isBlank() || !ids.add(id))
                throw new IllegalArgumentException("Tom eller dubblerad nodidentitet: " + id);
            var version = node.get("version");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() < 0)
                throw new IllegalArgumentException("Ogiltig objektversion: " + id);

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("id", id);
            for (JsonNode field : rule.path("properties")) {
                String key = field.asString();
                flatten(key, node.get(key), properties);
            }
            properties.put("version", version.intValue());
            String props = properties.entrySet().stream()
                    .map(e -> identifier(e.getKey()) + ": " + literal(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(", ", "{", "}"));
            nodes.add(new Nod(id, version.intValue(), rule.path("label").asString(), props));
            if (parent != null) relations.add(new Kant(parent, id, relation.toUpperCase(Locale.ROOT)));
            owner = id;
        }
        for (var entry : node.properties())
            collect(entry.getValue(), owner, entry.getKey(), nodes, relations, ids);
    }

    /** Plattar ut valda värdeobjekt till skalära nodegenskaper, exempelvis belopp_varde. */
    private static void flatten(String key, JsonNode value, Map<String, Object> properties) {
        if (value == null || value.isNull())
            return;
        if (value.isObject()) {
            for (var entry : value.properties()) {
                if (!entry.getKey().startsWith("@"))
                    flatten(key + "_" + entry.getKey(), entry.getValue(), properties);
            }
        } else if (value.isValueNode()) {
            properties.put(key, JSON.treeToValue(value, Object.class));
        }
    }

    // Skydda Cypher-identifierare; JSON-skrivaren hanterar motsvarande escaping av värden.
    private static String identifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Användning: Grafprojektion <verifierad-json> <cypher-ut>");

        String cypher = schema() + ";\n" + new Grafprojektion().projektera(Files.readAllBytes(Path.of(args[0])));
        Path out = Path.of(args[1]);
        if (out.toAbsolutePath().getParent() != null)
            Files.createDirectories(out.toAbsolutePath().getParent());

        Files.writeString(out, cypher);
        System.out.println("Separat grafprojektion skapad: " + out);
    }
}
