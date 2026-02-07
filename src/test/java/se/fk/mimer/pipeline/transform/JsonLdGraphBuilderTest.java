package se.fk.mimer.pipeline.transform;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonLdGraphBuilderTest {
    @Test
    void buildsGraphWithRecordNodes() throws Exception {
        Path rawJson = Path.of("src/test/resources/fixtures/yrkan-full.json");
        Path context = Path.of("src/main/resources/context/ffa-1.0.jsonld");
        Path sdl = Path.of("src/main/resources/schema/ffa.graphqls");

        JsonArray expanded = JsonLdExpansionExample.expandWithLocalContext(rawJson, context);
        expanded = JsonLdTypeMapper.replaceTypes(expanded, sdl);
        expanded = JsonLdValueTypeNormalizer.ensureStringTypes(expanded);

        JsonObject graph = JsonLdGraphBuilder.buildGraph(expanded, sdl, context.toUri().toString());
        assertTrue(graph.containsKey("@graph"), "Expected @graph in output");
        assertTrue(graph.get("@graph").getValueType() == JsonValue.ValueType.ARRAY, "Expected @graph array");
        assertTrue(graph.getJsonArray("@graph").size() > 1, "Expected multiple record nodes in @graph");
    }
}
