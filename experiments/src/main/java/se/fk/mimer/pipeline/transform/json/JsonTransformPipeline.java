package se.fk.mimer.pipeline.transform.json;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.apicatalog.rdf.io.nquad.NQuadsWriter;
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
import se.fk.mimer.pipeline.transform.jsonld.JsonLdTypeMapper;
import se.fk.mimer.pipeline.transform.jsonld.JsonLdValueTypeNormalizer;
import tools.jackson.databind.ObjectMapper;
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
public final class JsonTransformPipeline {
    private static final Path DEFAULT_CONTEXT = Path.of("src/main/resources/context/ffa-1.0.jsonld");
    private static final Path DEFAULT_FRAME = Path.of("src/main/resources/frame/ffa-frame.jsonld");
    private static final Path DEFAULT_SDL = Path.of("src/main/resources/schema/ffa.graphqls");

    private JsonTransformPipeline() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {

            usage();
            System.exit(2);
        }

        Args parsed = Args.parse(args);
        Path rawJson = parsed.rawJson();
        Path context = parsed.context().orElse(DEFAULT_CONTEXT);
        Path frame = parsed.frame().orElse(DEFAULT_FRAME);
        Path output = parsed.output().orElse(Path.of("target/ffa-out.nq"));
        Path cypherOut = parsed.cypherOut().orElse(Path.of("target/ffa-out.cypher"));
        Map<String, String> prefixMap = loadPrefixMap(context);
        Path mappingPath = parsed.mapping().orElseGet(JsonTransformPipeline::defaultMappingPath);
        MappingConfig mapping = loadMapping(mappingPath, prefixMap);

        Path workDir = output.toAbsolutePath().getParent();
        if (workDir != null) {
            Files.createDirectories(workDir);
        }

        if (parsed.migrate()) {
            JsonMigrator.Result migrated = new JsonMigrator(new ObjectMapper())
                    .migrateIfNeeded(rawJson, workDir != null ? workDir : Path.of("target"));
            rawJson = migrated.path();
        }

        JsonArray expanded = expandJsonLd(rawJson, context);
        if (DEFAULT_SDL.toFile().exists()) {
            expanded = JsonLdTypeMapper.replaceTypes(expanded, DEFAULT_SDL);
        }
        expanded = JsonLdValueTypeNormalizer.ensureStringTypes(expanded);

        Path expandedFile = output.resolveSibling("expanded.json");
        writePrettyJson(expanded, expandedFile);

        if (parsed.writeRdf()) {
            writeRdfNQuads(expandedFile, output);
        }

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
            case CYPHER -> writeCypher(ensureGraphWrapped(expanded), cypherOut, prefixMap, mapping);
            case NONE -> {}
        }
    }

    private static void usage() {
        System.err.println("Usage: JsonTransformPipeline <raw-json> " +
                "[--context path] [--frame path] [--mapping path] [--out path] " +
                "[--import neo4j|cypher|none] [--cypher-out path] [--neo4j-opts key=value,...] " +
                "[--no-migrate] [--no-rdf]");
    }

    private static Path defaultMappingPath() {
        Path path = Path.of("src/main/resources/mapping/graph-mapping.json");
        return path.toFile().exists() ? path : null;
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

    private static Map<String, String> loadPrefixMap(Path contextFile) {
        if (contextFile == null || !contextFile.toFile().exists()) {
            return Map.of();
        }
        try (var stream = Files.newInputStream(contextFile)) {
            JsonObject root = Json.createReader(stream).readObject();
            JsonValue ctxValue = root.get("@context");
            if (ctxValue == null || ctxValue.getValueType() != JsonValue.ValueType.OBJECT) {
                return Map.of();
            }
            JsonObject context = ctxValue.asJsonObject();
            Map<String, String> prefixes = new LinkedHashMap<>();
            for (Map.Entry<String, JsonValue> entry : context.entrySet()) {
                String key = entry.getKey();
                JsonValue val = entry.getValue();
                if (val.getValueType() == JsonValue.ValueType.STRING) {
                    String iri = ((JsonString) val).getString();
                    if (iri.contains(":")) {
                        prefixes.put(key, iri);
                    }
                } else if (val.getValueType() == JsonValue.ValueType.OBJECT) {
                    JsonObject obj = val.asJsonObject();
                    JsonValue id = obj.get("@id");
                    if (id != null && id.getValueType() == JsonValue.ValueType.STRING) {
                        String iri = ((JsonString) id).getString();
                        if (iri.contains(":")) {
                            prefixes.put(key, iri);
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

    private static void writeRdfNQuads(Path expandedFile, Path out) throws IOException, JsonLdError {
        var dataset = JsonLd.toRdf(expandedFile.toUri().toString()).get();
        try (var stream = Files.newOutputStream(out);
             var outWriter = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            NQuadsWriter writer = new NQuadsWriter(outWriter);
            writer.write(dataset);
            outWriter.flush();
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
                "\",\"N-Quads\"," + cypherMapLiteral(options) + ");\n";
        Files.writeString(out, cypher, StandardCharsets.UTF_8);
    }

    private static int writeCypher(
            JsonStructure framed,
            Path out,
            Map<String, String> prefixMap,
            MappingConfig mapping
    ) throws IOException {
        Map<String, JsonObject> nodesById = new LinkedHashMap<>();
        collectNodes(framed, nodesById, mapping, prefixMap, null);

        Map<String, List<String>> labelsById = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : nodesById.entrySet()) {
            labelsById.put(entry.getKey(), typeLabels(entry.getValue(), prefixMap));
        }

        List<String> statements = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : nodesById.entrySet()) {
            String id = entry.getKey();
            JsonObject node = entry.getValue();

            List<String> labels = labelsById.getOrDefault(id, List.of());
            Map<String, Object> props = literalProperties(node, prefixMap, mapping);

            StringBuilder stmt = new StringBuilder();
            String labelSet = labelsToCypher(labels);
            stmt.append("MERGE (n:").append(labelSet).append(" {id: ")
                    .append(cypherValue(id)).append("})\n");
            if (!props.isEmpty()) {
                stmt.append("SET n += ").append(cypherMap(props)).append("\n");
            }
            statements.add(stmt.toString().trim() + ";\n");

            for (Relationship rel : relationships(node, id, prefixMap, mapping, nodesById)) {
                String targetLabels = labelsToCypher(labelsById.getOrDefault(rel.targetId(), List.of()));
                String relStmt = "MERGE (m:" + targetLabels + " {id: " + cypherValue(rel.targetId()) + "})\n" +
                        "MERGE (n {id: " + cypherValue(id) + "})\n" +
                        "MERGE (n)-[:" + backtick(rel.type()) + "]->(m)\n";
                statements.add(relStmt.trim() + ";\n");
            }
        }
        Files.writeString(out, String.join("\n", statements), StandardCharsets.UTF_8);
        return statements.size();
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

    private static List<String> typeLabels(JsonObject node, Map<String, String> prefixMap) {
        List<String> labels = new ArrayList<>();
        JsonValue type = node.get("@type");
        if (type == null) {
            return labels;
        }
        if (type.getValueType() == JsonValue.ValueType.STRING) {
            labels.add(labelFromIri(((JsonString) type).getString(), prefixMap));
        } else if (type.getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue v : node.getJsonArray("@type")) {
                if (v.getValueType() == JsonValue.ValueType.STRING) {
                    labels.add(labelFromIri(((JsonString) v).getString(), prefixMap));
                }
            }
        }
        return labels;
    }

    private static Map<String, Object> literalProperties(
            JsonObject node,
            Map<String, String> prefixMap,
            MappingConfig mapping
    ) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<String, JsonValue> entry : node.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("@")) {
                continue;
            }
            if (mapping.shouldPromote(key)) {
                continue;
            }
            collectProperties(props, key, entry.getValue(), prefixMap);
        }
        return props;
    }

    private static List<Relationship> relationships(
            JsonObject node,
            String parentId,
            Map<String, String> prefixMap,
            MappingConfig mapping,
            Map<String, JsonObject> nodesById
    ) {
        List<Relationship> rels = new ArrayList<>();
        for (Map.Entry<String, JsonValue> entry : node.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("@")) {
                continue;
            }
            JsonValue value = entry.getValue();
            if (mapping.shouldFlatten(key)) {
                continue;
            }
            if (mapping.shouldPromote(key)) {
                rels.addAll(extractPromotedRelationships(key, value, parentId, prefixMap, nodesById));
                continue;
            }
            rels.addAll(extractRelationships(key, value, prefixMap));
        }
        return rels;
    }

    private static List<Relationship> extractRelationships(String predicate, JsonValue value, Map<String, String> prefixMap) {
        List<Relationship> rels = new ArrayList<>();
        if (value.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject obj = value.asJsonObject();
            if (obj.containsKey("@id")) {
                nodeId(obj).ifPresent(id -> rels.add(new Relationship(relType(predicate, prefixMap), id)));
            }
        } else if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue item : value.asJsonArray()) {
                rels.addAll(extractRelationships(predicate, item, prefixMap));
            }
        }
        return rels;
    }

    private static List<Relationship> extractPromotedRelationships(
            String predicate,
            JsonValue value,
            String parentId,
            Map<String, String> prefixMap,
            Map<String, JsonObject> nodesById
    ) {
        List<Relationship> rels = new ArrayList<>();
        if (parentId == null) {
            return rels;
        }
        if (value.getValueType() == JsonValue.ValueType.OBJECT) {
            JsonObject obj = value.asJsonObject();
            String targetId = ensurePromotedNode(obj, predicate, parentId, 0, prefixMap, nodesById);
            if (targetId != null) {
                rels.add(new Relationship(relType(predicate, prefixMap), targetId));
            }
        } else if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            int idx = 0;
            for (JsonValue item : value.asJsonArray()) {
                if (item.getValueType() == JsonValue.ValueType.OBJECT) {
                    String targetId = ensurePromotedNode(item.asJsonObject(), predicate, parentId, idx, prefixMap, nodesById);
                    if (targetId != null) {
                        rels.add(new Relationship(relType(predicate, prefixMap), targetId));
                    }
                }
                idx++;
            }
        }
        return rels;
    }

    private static void collectProperties(Map<String, Object> props, String predicateIri, JsonValue value, Map<String, String> prefixMap) {
        String prefix = propertyKey(predicateIri, prefixMap);
        collectPropertiesWithPrefix(props, prefix, value, false);
    }

    private static void collectPropertiesWithPrefix(
            Map<String, Object> props,
            String prefix,
            JsonValue value,
            boolean allowIdLiteral
    ) {
        switch (value.getValueType()) {
            case ARRAY -> {
                for (JsonValue item : value.asJsonArray()) {
                    collectPropertiesWithPrefix(props, prefix, item, allowIdLiteral);
                }
            }
            case OBJECT -> {
                JsonObject obj = value.asJsonObject();
                if (obj.containsKey("@id")) {
                    JsonValue idValue = obj.get("@id");
                    if (allowIdLiteral && idValue.getValueType() == JsonValue.ValueType.STRING) {
                        addProp(props, prefix, ((JsonString) idValue).getString());
                    } else if (!allowIdLiteral) {
                        return;
                    }
                }
                if (obj.containsKey("@value")) {
                    Object literal = coerceLiteral(obj);
                    if (literal != null) {
                        addProp(props, prefix, literal);
                    }
                    return;
                }
                for (Map.Entry<String, JsonValue> entry : obj.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("@")) {
                        continue;
                    }
                    String nestedPrefix = prefix + "." + propertyKey(key, null);
                    collectPropertiesWithPrefix(props, nestedPrefix, entry.getValue(), true);
                }
            }
            default -> {
                Object literal = coerceLiteral(value);
                if (literal != null) {
                    addProp(props, prefix, literal);
                }
            }
        }
    }

    private static void addProp(Map<String, Object> props, String key, Object value) {
        Object existing = props.get(key);
        if (existing == null) {
            props.put(key, value);
            return;
        }
        if (existing instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> mutable = (List<Object>) list;
            mutable.add(value);
            return;
        }
        List<Object> list = new ArrayList<>();
        list.add(existing);
        list.add(value);
        props.put(key, list);
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
                if (list.isEmpty()) {
                    yield null;
                }
                if (list.size() == 1) {
                    yield list.get(0);
                }
                yield list;
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

    private static String labelFromIri(String iri, Map<String, String> prefixMap) {
        return sanitizeLabelForCypher(compactIri(iri, prefixMap));
    }

    private static String propertyKey(String iri, Map<String, String> prefixMap) {
        String compact = compactIri(iri, prefixMap);
        return compact.replace('-', '_');
    }

    private static String relType(String iri, Map<String, String> prefixMap) {
        return sanitizeLabelForCypher(compactIri(iri, prefixMap));
    }

    private static String compactIri(String iri, Map<String, String> prefixMap) {
        if (prefixMap != null) {
            for (Map.Entry<String, String> entry : prefixMap.entrySet()) {
                String prefix = entry.getKey();
                String ns = entry.getValue();
                if (iri.startsWith(ns)) {
                    return prefix + ":" + iri.substring(ns.length());
                }
            }
        }
        return localName(iri);
    }

    private static void collectNodes(
            JsonValue value,
            Map<String, JsonObject> nodesById,
            MappingConfig mapping,
            Map<String, String> prefixMap,
            String currentId
    ) {
        if (value == null) {
            return;
        }
        switch (value.getValueType()) {
            case ARRAY -> {
                for (JsonValue item : value.asJsonArray()) {
                    collectNodes(item, nodesById, mapping, prefixMap, currentId);
                }
            }
            case OBJECT -> {
                JsonObject obj = value.asJsonObject();
                if (obj.containsKey("@graph") && obj.get("@graph").getValueType() == JsonValue.ValueType.ARRAY) {
                    collectNodes(obj.get("@graph"), nodesById, mapping, prefixMap, currentId);
                    return;
                }
                String objId = nodeId(obj).orElse(null);
                String nextId = objId != null ? objId : currentId;
                if (objId != null && obj.containsKey("@type")) {
                    nodesById.putIfAbsent(objId, obj);
                }
                for (Map.Entry<String, JsonValue> entry : obj.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("@")) {
                        continue;
                    }
                    if (mapping.shouldFlatten(key)) {
                        continue;
                    }
                    if (mapping.shouldPromote(key)) {
                        ensurePromotedNodes(entry.getValue(), key, nextId, prefixMap, nodesById);
                        continue;
                    }
                    collectNodes(entry.getValue(), nodesById, mapping, prefixMap, nextId);
                }
            }
            default -> {
            }
        }
    }

    private static void ensurePromotedNodes(
            JsonValue value,
            String predicate,
            String parentId,
            Map<String, String> prefixMap,
            Map<String, JsonObject> nodesById
    ) {
        if (parentId == null || value == null) {
            return;
        }
        if (value.getValueType() == JsonValue.ValueType.OBJECT) {
            ensurePromotedNode(value.asJsonObject(), predicate, parentId, 0, prefixMap, nodesById);
        } else if (value.getValueType() == JsonValue.ValueType.ARRAY) {
            int idx = 0;
            for (JsonValue item : value.asJsonArray()) {
                if (item.getValueType() == JsonValue.ValueType.OBJECT) {
                    ensurePromotedNode(item.asJsonObject(), predicate, parentId, idx, prefixMap, nodesById);
                }
                idx++;
            }
        }
    }

    private static String ensurePromotedNode(
            JsonObject obj,
            String predicate,
            String parentId,
            int index,
            Map<String, String> prefixMap,
            Map<String, JsonObject> nodesById
    ) {
        if (obj == null) {
            return null;
        }
        String existingId = nodeId(obj).orElse(null);
        if (existingId != null) {
            nodesById.putIfAbsent(existingId, obj);
            return existingId;
        }
        String suffix = compactIri(predicate, prefixMap);
        String mintedId = parentId + ":" + suffix + (index > 0 ? ":" + index : "");
        if (nodesById.containsKey(mintedId)) {
            return mintedId;
        }
        JsonObjectBuilder builder = Json.createObjectBuilder();
        boolean hasType = obj.containsKey("@type");
        for (Map.Entry<String, JsonValue> entry : obj.entrySet()) {
            builder.add(entry.getKey(), entry.getValue());
        }
        builder.add("@id", mintedId);
        if (!hasType) {
            builder.add("@type", predicate);
        }
        JsonObject promoted = builder.build();
        nodesById.put(mintedId, promoted);
        return mintedId;
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

    private static String sanitizeLabelForCypher(String label) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == ':') {
                sb.append(c);
            } else if (c == '`') {
                sb.append('_');
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

    private static String backtick(String label) {
        return "`" + label + "`";
    }

    private static String labelsToCypher(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return backtick("Resource");
        }
        return labels.stream().map(JsonTransformPipeline::backtick)
                .collect(java.util.stream.Collectors.joining(":"));
    }

    private record MappingConfig(Set<String> flatten, Set<String> promote) {
        boolean shouldFlatten(String predicateIri) {
            return flatten.contains(predicateIri);
        }

        boolean shouldPromote(String predicateIri) {
            return promote.contains(predicateIri);
        }
    }

    private static MappingConfig loadMapping(Path mappingPath, Map<String, String> prefixMap) {
        Set<String> defaultFlatten = Set.of(
                normalizePredicate("ffa:belopp", prefixMap),
                normalizePredicate("ffa:period", prefixMap),
                normalizePredicate("ffa:giltighetsperiod", prefixMap)
        );
        if (mappingPath == null || !mappingPath.toFile().exists()) {
            return new MappingConfig(defaultFlatten, Set.of());
        }
        try (var stream = Files.newInputStream(mappingPath)) {
            JsonObject root = Json.createReader(stream).readObject();
            Set<String> flatten = readPredicateSet(root, "flatten", prefixMap);
            Set<String> promote = readPredicateSet(root, "promote", prefixMap);
            return new MappingConfig(
                    flatten.isEmpty() ? defaultFlatten : flatten,
                    promote
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read mapping file: " + mappingPath, e);
        }
    }

    private static Set<String> readPredicateSet(JsonObject root, String key, Map<String, String> prefixMap) {
        JsonValue val = root.get(key);
        if (val == null || val.getValueType() != JsonValue.ValueType.ARRAY) {
            return Set.of();
        }
        Set<String> result = new java.util.LinkedHashSet<>();
        for (JsonValue item : val.asJsonArray()) {
            if (item.getValueType() == JsonValue.ValueType.STRING) {
                result.add(normalizePredicate(((JsonString) item).getString(), prefixMap));
            }
        }
        return result;
    }

    private static String normalizePredicate(String value, Map<String, String> prefixMap) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("urn:")) {
            return value;
        }
        int colon = value.indexOf(':');
        if (colon > 0 && prefixMap != null) {
            String prefix = value.substring(0, colon);
            String local = value.substring(colon + 1);
            String ns = prefixMap.get(prefix);
            if (ns != null) {
                return ns + local;
            }
        }
        return value;
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
            Map<String, String> neo4jOpts,
            boolean migrate,
            boolean writeRdf
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
            boolean migrate = true;
            boolean writeRdf = true;

            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--context" -> context = Optional.of(Path.of(args[++i]));
                    case "--frame" -> frame = Optional.of(Path.of(args[++i]));
                    case "--mapping" -> mapping = Optional.of(Path.of(args[++i]));
                    case "--out" -> output = Optional.of(Path.of(args[++i]));
                    case "--cypher-out" -> {
                        cypherOut = Optional.of(Path.of(args[++i]));
                        importMode = ImportMode.CYPHER;
                    }
                    case "--import" -> importMode = ImportMode.valueOf(args[++i].toUpperCase(Locale.ROOT));
                    case "--neo4j-opts" -> neo4jOpts.putAll(parseOptions(args[++i]));
                    case "--no-migrate" -> migrate = false;
                    case "--no-rdf" -> writeRdf = false;
                    default -> throw new IllegalArgumentException("Unknown arg: " + arg);
                }
            }
            return new Args(rawJson, context, frame, mapping, output, cypherOut, importMode, neo4jOpts, migrate, writeRdf);
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
