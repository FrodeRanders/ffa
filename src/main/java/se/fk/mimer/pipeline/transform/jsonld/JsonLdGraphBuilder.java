package se.fk.mimer.pipeline.transform.jsonld;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds a JSON-LD graph document by selecting known record types.
 */
public final class JsonLdGraphBuilder {
    private JsonLdGraphBuilder() {}

    public static JsonObject buildGraph(JsonArray expanded, Path sdlPath, String contextUri) throws Exception {
        Map<String, String> typeToUri = JsonLdTypeMapper.loadTypeUriMap(sdlPath);
        Set<String> recordUris = new HashSet<>(typeToUri.values());

        JsonArrayBuilder graph = Json.createArrayBuilder();
        Set<String> seenIds = new HashSet<>();
        Set<Integer> seenAnonymous = new HashSet<>();
        for (JsonValue value : expanded) {
            collectNodes(value, recordUris, graph, seenIds, seenAnonymous);
        }

        JsonObjectBuilder out = Json.createObjectBuilder();
        if (contextUri != null && !contextUri.isEmpty()) {
            out.add("@context", contextUri);
        }
        out.add("@graph", graph);
        return out.build();
    }

    private static boolean hasRecordType(JsonObject node, Set<String> recordUris) {
        if (!node.containsKey("@type")) {
            return false;
        }
        JsonValue typeVal = node.get("@type");
        if (typeVal.getValueType() == JsonValue.ValueType.STRING) {
            return recordUris.contains(((JsonString) typeVal).getString());
        }
        if (typeVal.getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue v : typeVal.asJsonArray()) {
                if (v.getValueType() == JsonValue.ValueType.STRING
                        && recordUris.contains(((JsonString) v).getString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectNodes(
            JsonValue value,
            Set<String> recordUris,
            JsonArrayBuilder graph,
            Set<String> seenIds,
            Set<Integer> seenAnonymous
    ) {
        switch (value.getValueType()) {
            case OBJECT -> {
                JsonObject node = value.asJsonObject();
                if (hasRecordType(node, recordUris)) {
                    String id = node.containsKey("@id") ? node.getString("@id") : null;
                    if (id != null) {
                        if (seenIds.add(id)) {
                            graph.add(node);
                        }
                    } else {
                        int identity = System.identityHashCode(node);
                        if (seenAnonymous.add(identity)) {
                            graph.add(node);
                        }
                    }
                }
                for (JsonValue child : node.values()) {
                    collectNodes(child, recordUris, graph, seenIds, seenAnonymous);
                }
            }
            case ARRAY -> {
                for (JsonValue child : value.asJsonArray()) {
                    collectNodes(child, recordUris, graph, seenIds, seenAnonymous);
                }
            }
            default -> {}
        }
    }
}
