package se.fk.mimer.pipeline.transform.jsonld;

import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * Example-only graph packaging after JSON-LD expansion.
 */
public final class JsonLdGraphPackagingExample {
    private JsonLdGraphPackagingExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            System.err.println("Usage: JsonLdGraphPackagingExample <raw-json> [context.jsonld] [schema.graphqls]");
            System.exit(2);
        }

        Path jsonFile = Path.of(args[0]);
        Path contextFile = args.length >= 2
                ? Path.of(args[1])
                : Path.of("src/main/resources/context/ffa-1.0.jsonld");
        Path sdlPath = args.length == 3
                ? Path.of(args[2])
                : Path.of("src/main/resources/schema/ffa.graphqls");

        var expanded = contextFile.toFile().exists()
                ? JsonLdExpansionExample.expandWithLocalContext(jsonFile, contextFile)
                : JsonLdExpansionExample.expandWithoutRemote(jsonFile);
        if (sdlPath.toFile().exists()) {
            expanded = JsonLdTypeMapper.replaceTypes(expanded, sdlPath);
        }
        expanded = JsonLdValueTypeNormalizer.ensureStringTypes(expanded);

        String contextUri = contextFile.toFile().exists() ? contextFile.toUri().toString() : null;
        JsonObject graph = JsonLdGraphBuilder.buildGraph(expanded, sdlPath, contextUri);
        writePrettyJson(graph);
    }

    private static void writePrettyJson(JsonObject graph) {
        Map<String, Object> config = Map.of(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory factory = jakarta.json.Json.createWriterFactory(config);
        try (JsonWriter writer = factory.createWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            writer.write(graph);
        }
    }
}
