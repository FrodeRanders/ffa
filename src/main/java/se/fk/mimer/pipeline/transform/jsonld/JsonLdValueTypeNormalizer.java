package se.fk.mimer.pipeline.transform.jsonld;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import java.util.Map;

/**
 * Ensures string @value entries carry an explicit @type (xsd:string).
 */
public final class JsonLdValueTypeNormalizer {
    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";
    private static final String XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean";
    private static final String XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer";
    private static final String XSD_DOUBLE = "http://www.w3.org/2001/XMLSchema#double";

    private JsonLdValueTypeNormalizer() {}

    public static JsonArray ensureStringTypes(JsonArray expanded) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (JsonValue value : expanded) {
            builder.add(normalize(value));
        }
        return builder.build();
    }

    private static JsonValue normalize(JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> normalizeObject(value.asJsonObject());
            case ARRAY -> normalizeArray(value.asJsonArray());
            default -> value;
        };
    }

    private static JsonArray normalizeArray(JsonArray array) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (JsonValue value : array) {
            builder.add(normalize(value));
        }
        return builder.build();
    }

    private static JsonObject normalizeObject(JsonObject object) {
        if (object.containsKey("@value") && !object.containsKey("@type")) {
            JsonValue value = object.get("@value");
            String type = valueType(value);
            if (type != null) {
                JsonObjectBuilder builder = Json.createObjectBuilder();
                for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
                    builder.add(entry.getKey(), entry.getValue());
                }
                builder.add("@type", Json.createValue(type));
                return builder.build();
            }
        }
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, JsonValue> entry : object.entrySet()) {
            builder.add(entry.getKey(), normalize(entry.getValue()));
        }
        return builder.build();
    }

    private static String valueType(JsonValue value) {
        return switch (value.getValueType()) {
            case STRING -> XSD_STRING;
            case TRUE, FALSE -> XSD_BOOLEAN;
            case NUMBER -> isInteger(value) ? XSD_INTEGER : XSD_DOUBLE;
            default -> null;
        };
    }

    private static boolean isInteger(JsonValue value) {
        if (value.getValueType() != JsonValue.ValueType.NUMBER) {
            return false;
        }
        jakarta.json.JsonNumber num = (jakarta.json.JsonNumber) value;
        String lexical = num.toString();
        if (lexical.contains(".") || lexical.contains("e") || lexical.contains("E")) {
            return false;
        }
        try {
            num.longValueExact();
            return true;
        } catch (ArithmeticException ex) {
            return false;
        }
    }
}
