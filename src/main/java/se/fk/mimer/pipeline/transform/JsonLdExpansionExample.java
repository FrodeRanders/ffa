package se.fk.mimer.pipeline.transform;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Example-only JSON-LD expansion using Titanium JSON-LD.
 * Operates on raw JSON without changing the original serialization format.
 */
public final class JsonLdExpansionExample {
    private JsonLdExpansionExample() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: JsonLdExpansionExample <raw-json> [context.jsonld]");
            System.exit(2);
        }

        Path jsonFile = Path.of(args[0]);
        Path contextFile = args.length == 2
                ? Path.of(args[1])
                : Path.of("src/main/resources/context/ffa-1.0.jsonld");

        JsonArray expanded = contextFile.toFile().exists()
                ? expandWithLocalContext(jsonFile, contextFile)
                : expandWithoutRemote(jsonFile);
        Path sdlPath = Path.of("src/main/resources/schema/ffa.graphqls");
        if (sdlPath.toFile().exists()) {
            expanded = JsonLdTypeMapper.replaceTypes(expanded, sdlPath);
        }
        expanded = JsonLdValueTypeNormalizer.ensureStringTypes(expanded);
        writePrettyJson(expanded);
    }

    public static JsonArray expandWithLocalContext(Path jsonFile, Path contextFile) throws Exception {
        JsonLdOptions options = new JsonLdOptions();
        options.setDocumentLoader(localOnlyLoader(contextFile));
        return JsonLd.expand(jsonFile.toUri().toString())
                .options(options)
                .context(contextFile.toUri().toString())
                .get();
    }

    public static JsonArray expandWithoutRemote(Path jsonFile) throws Exception {
        JsonLdOptions options = new JsonLdOptions();
        options.setDocumentLoader(localOnlyLoader(null));
        return JsonLd.expand(jsonFile.toUri().toString())
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
            if (uriString.equals(contextUri)) {
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
        } catch (java.io.IOException e) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_DOCUMENT_FAILED,
                    "Failed to load local document: " + uri, e);
        }
        throw new JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                "Remote context not allowed in demo: " + uri);
    }

    private static void writePrettyJson(JsonArray expanded) {
        Map<String, Object> config = Map.of(JsonGenerator.PRETTY_PRINTING, true);
        JsonWriterFactory factory = Json.createWriterFactory(config);
        try (JsonWriter writer = factory.createWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            writer.write(expanded);
        }
    }
}
