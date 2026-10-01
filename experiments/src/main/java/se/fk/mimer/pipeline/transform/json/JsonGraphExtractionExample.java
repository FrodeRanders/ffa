package se.fk.mimer.pipeline.transform.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Example-only extractor that reads raw JSON and plucks central nodes.
 */
public final class JsonGraphExtractionExample {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JsonGraphExtractionExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: JsonGraphExtractionExample <path-to-raw-json>");
            System.exit(2);
        }

        JsonNode root = MAPPER.readTree(Files.readString(Path.of(args[0])));
        List<JsonNode> ersattningar = extractErsattningar(root);

        System.out.println("Ersattningar: " + ersattningar.size());
        for (JsonNode node : ersattningar) {
            System.out.println(node.toPrettyString());
        }
    }

    /**
     * Minimal extraction without JSON-LD expansion.
     * This is intentionally conservative and only uses structural hints
     * that already exist in the raw JSON.
     */
    public static List<JsonNode> extractErsattningar(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode results = root.get("producerade_resultat");
        if (results == null || !results.isArray()) {
            return out;
        }

        for (JsonNode node : results) {
            if (node.has("belopp")) {
                out.add(node);
            }
        }
        return out;
    }
}
