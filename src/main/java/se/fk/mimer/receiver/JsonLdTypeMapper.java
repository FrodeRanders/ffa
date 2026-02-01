package se.fk.mimer.receiver;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps @type values from Java class names to domain IRIs using the GraphQL SDL.
 */
public final class JsonLdTypeMapper {
    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile("(?s)([A-Za-z_][A-Za-z0-9_]*)\\s+@attribute\\((.*?)\\)");
    private static final Pattern TYPE_RECORD_PATTERN =
            Pattern.compile("type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+@record\\(attribute:\\s*([A-Za-z_][A-Za-z0-9_]*)\\)");
    private static final Pattern URI_PATTERN =
            Pattern.compile("uri\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private JsonLdTypeMapper() {}

    public static JsonArray replaceTypes(JsonArray expanded, Path sdlPath) throws IOException {
        Map<String, String> typeToUri = loadTypeUriMap(sdlPath);
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (JsonValue value : expanded) {
            builder.add(remapValue(value, typeToUri));
        }
        return builder.build();
    }

    private static JsonValue remapValue(JsonValue value, Map<String, String> typeToUri) {
        return switch (value.getValueType()) {
            case OBJECT -> remapObject(value.asJsonObject(), typeToUri);
            case ARRAY -> remapArray(value.asJsonArray(), typeToUri);
            default -> value;
        };
    }

    private static JsonArray remapArray(JsonArray array, Map<String, String> typeToUri) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (JsonValue value : array) {
            builder.add(remapValue(value, typeToUri));
        }
        return builder.build();
    }

    private static JsonObject remapObject(JsonObject object, Map<String, String> typeToUri) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            String key = entry.getKey();
            JsonValue value = entry.getValue();
            if ("@type".equals(key)) {
                builder.add(key, remapTypeValue(value, typeToUri));
            } else {
                builder.add(key, remapValue(value, typeToUri));
            }
        }
        return builder.build();
    }

    private static JsonValue remapTypeValue(JsonValue value, Map<String, String> typeToUri) {
        return switch (value.getValueType()) {
            case STRING -> Json.createValue(mapType(((JsonString) value).getString(), typeToUri));
            case ARRAY -> {
                JsonArrayBuilder builder = Json.createArrayBuilder();
                for (JsonValue v : value.asJsonArray()) {
                    if (v.getValueType() == JsonValue.ValueType.STRING) {
                        builder.add(mapType(((JsonString) v).getString(), typeToUri));
                    } else {
                        builder.add(v);
                    }
                }
                yield builder.build();
            }
            default -> value;
        };
    }

    private static String mapType(String typeValue, Map<String, String> typeToUri) {
        if (isIri(typeValue)) {
            return typeValue;
        }
        String simpleName = simpleClassName(typeValue);
        String mapped = typeToUri.get(simpleName);
        if (mapped != null) {
            return mapped;
        }
        String fallbackKey = toSnakeCase(simpleName);
        mapped = typeToUri.get(fallbackKey);
        return mapped != null ? mapped : typeValue;
    }

    private static boolean isIri(String value) {
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("urn:");
    }

    private static String simpleClassName(String value) {
        int lastDot = value.lastIndexOf('.');
        String name = lastDot >= 0 ? value.substring(lastDot + 1) : value;
        int lastDollar = name.lastIndexOf('$');
        return lastDollar >= 0 ? name.substring(lastDollar + 1) : name;
    }

    private static String toSnakeCase(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static Map<String, String> loadTypeUriMap(Path sdlPath) throws IOException {
        String text = Files.readString(sdlPath, StandardCharsets.UTF_8);
        Map<String, String> attributeToUri = new HashMap<>();
        Map<String, String> typeToAttribute = new HashMap<>();

        Matcher attrMatcher = ATTRIBUTE_PATTERN.matcher(text);
        while (attrMatcher.find()) {
            String attribute = attrMatcher.group(1);
            String body = attrMatcher.group(2);
            Matcher uriMatcher = URI_PATTERN.matcher(body);
            if (uriMatcher.find()) {
                attributeToUri.put(attribute, uriMatcher.group(1));
            }
        }

        Matcher typeMatcher = TYPE_RECORD_PATTERN.matcher(text);
        while (typeMatcher.find()) {
            typeToAttribute.put(typeMatcher.group(1), typeMatcher.group(2));
        }

        Map<String, String> typeToUri = new HashMap<>();
        for (Map.Entry<String, String> entry : typeToAttribute.entrySet()) {
            String uri = attributeToUri.get(entry.getValue());
            if (uri != null) {
                typeToUri.put(entry.getKey(), uri);
            }
        }
        for (Map.Entry<String, String> entry : attributeToUri.entrySet()) {
            typeToUri.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return typeToUri;
    }
}
