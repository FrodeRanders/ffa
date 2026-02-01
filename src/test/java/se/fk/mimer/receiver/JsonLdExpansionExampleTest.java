package se.fk.mimer.receiver;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonLdExpansionExampleTest {
    @Test
    void expandsWithContextAndFindsKnownTerms() throws Exception {
        Path rawJson = Path.of("src/test/resources/fixtures/yrkan-full.json");
        Path context = Path.of("src/main/resources/context/ffa-1.0.jsonld");

        JsonArray expanded = JsonLdExpansionExample.expandWithLocalContext(rawJson, context);
        JsonArray remapped = JsonLdTypeMapper.replaceTypes(expanded, Path.of("src/main/resources/schema/ffa.graphqls"));

        assertTrue(containsKey(remapped, "https://data.sfa.se/termer/1.0/ersattningstyp"),
                "Expected expanded JSON-LD to include ersattningstyp term");
        assertTrue(containsType(remapped, "https://data.sfa.se/termer/1.0/ersattning"),
                "Expected @type to be mapped to domain IRI");
    }

    private static boolean containsKey(JsonValue value, String key) {
        return switch (value.getValueType()) {
            case OBJECT -> {
                JsonObject obj = value.asJsonObject();
                if (obj.containsKey(key)) {
                    yield true;
                }
                boolean found = false;
                for (JsonValue child : obj.values()) {
                    if (containsKey(child, key)) {
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case ARRAY -> {
                for (JsonValue child : value.asJsonArray()) {
                    if (containsKey(child, key)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean containsType(JsonValue value, String expectedType) {
        return switch (value.getValueType()) {
            case OBJECT -> {
                JsonObject obj = value.asJsonObject();
                if (obj.containsKey("@type")) {
                    JsonValue typeVal = obj.get("@type");
                    if (typeVal.getValueType() == JsonValue.ValueType.STRING) {
                        if (expectedType.equals(typeVal.toString().replace("\"", ""))) {
                            yield true;
                        }
                    } else if (typeVal.getValueType() == JsonValue.ValueType.ARRAY) {
                        for (JsonValue v : typeVal.asJsonArray()) {
                            if (v.getValueType() == JsonValue.ValueType.STRING
                                    && expectedType.equals(v.toString().replace("\"", ""))) {
                                yield true;
                            }
                        }
                    }
                }
                boolean found = false;
                for (JsonValue child : obj.values()) {
                    if (containsType(child, expectedType)) {
                        found = true;
                        break;
                    }
                }
                yield found;
            }
            case ARRAY -> {
                for (JsonValue child : value.asJsonArray()) {
                    if (containsType(child, expectedType)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> false;
        };
    }
}
