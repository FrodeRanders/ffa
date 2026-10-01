package se.fk.mimer.graph;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * En projektion, inte ett nytt lagringsformat. Valda livscykelobjekt blir noder;
 * deras inbäddning blir relationer. Endast centralt valda egenskaper blir sökbara.
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
     * Noder skrivs före relationer så att alla relationers ändpunkter finns vid import.
     */
    public String projektera(byte[] json) {
        JsonNode root = JSON.readTree(json);
        if (!root.isObject())
            throw new IllegalArgumentException("Underlaget måste vara ett objekt");

        List<String> noder = new ArrayList<>();
        List<String> relationer = new ArrayList<>();
        Set<String> ids = new HashSet<>();

        collect(root, null, null, noder, relationer, ids);

        return "// FFA:s härledda graf. Kör i en separat databas för demonstrationen.\n"
                + String.join("\n", noder) + "\n" + String.join("\n", relationer) + "\n";
    }

    /** Följer inbäddningen och knyter varje livscykelnod till närmaste ägarnod. */
    private void collect(JsonNode node, String parent, String relation,
                        List<String> nodes, List<String> relations, Set<String> ids) {
        if (node.isArray()) {
            for (JsonNode child : node)
                collect(child, parent, relation, nodes, relations, ids);
            return;
        }
        if (!node.isObject())
            return;

        // Värdeobjekt saknar egen nodidentitet; deras barn fortsätter med samma ägare.
        String owner = parent;
        if (node.has("id") && node.has("version")) {
            String type = node.path("@type").asString();
            JsonNode rule = mapping.get(type);
            if (rule == null)
                throw new IllegalArgumentException("Ingen grafmappning för " + type);
            String id = node.path("id").asString();
            if (id.isBlank() || !ids.add(id))
                throw new IllegalArgumentException("Tom eller dubblerad nodidentitet: " + id);

            // Endast egenskaper som valts i den gemensamma mappningen blir sökbara.
            Map<String, Object> properties = new LinkedHashMap<>();
            for (JsonNode field : rule.path("properties")) {
                String key = field.asString();
                flatten(key, node.get(key), properties);
            }
            properties.put("version", node.path("version").intValue());

            String props = properties.entrySet().stream()
                    .map(e -> identifier(e.getKey()) + ": " + JSON.writeValueAsString(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(", "));
            nodes.add("MERGE (n:FfaObjekt:" + identifier(rule.path("label").asString())
                    + " {id: " + JSON.writeValueAsString(id) + "}) SET n = {id: "
                    + JSON.writeValueAsString(id) + ", " + props + "};");

            if (parent != null)
                relations.add("MATCH (a:FfaObjekt {id: " + JSON.writeValueAsString(parent)
                    + "}), (b:FfaObjekt {id: " + JSON.writeValueAsString(id) + "}) MERGE (a)-[:"
                    + identifier(relation.toUpperCase(Locale.ROOT)) + "]->(b);");
            owner = id;
        }

        for (var entry : node.properties()) {
            collect(entry.getValue(), owner, entry.getKey(), nodes, relations, ids);
        }
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

        String cypher = new Grafprojektion().projektera(Files.readAllBytes(Path.of(args[0])));
        Path out = Path.of(args[1]);
        if (out.toAbsolutePath().getParent() != null)
            Files.createDirectories(out.toAbsolutePath().getParent());

        Files.writeString(out, cypher);
        System.out.println("Separat grafprojektion skapad: " + out);
    }
}
