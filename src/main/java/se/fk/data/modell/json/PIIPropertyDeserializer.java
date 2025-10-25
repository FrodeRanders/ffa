package se.fk.data.modell.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static se.fk.data.modell.json.PIIPropertySerializer.MAGIC_WRAPPED_PROPERTY_NAME;

public class PIIPropertyDeserializer extends JsonDeserializer<Object> implements ContextualDeserializer {
    private static final Logger log = LoggerFactory.getLogger(PIIPropertyDeserializer.class);


    private transient String canonicalTypeName = null;

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        JsonNode valNode = node.get(MAGIC_WRAPPED_PROPERTY_NAME);
        if (null != valNode && null != canonicalTypeName) {
            return switch (canonicalTypeName) {
                // Primitive types uses "primitive" names, but not String
                case "double" -> valNode.doubleValue(); // relevant?
                case "int" -> valNode.intValue(); // relevant?
                case "long" -> valNode.longValue(); // relevant?
                case "java.lang.String" -> valNode.textValue();
                case "short" -> valNode.shortValue(); // relevant?
                case "boolean" -> valNode.asBoolean(); // relevant?
                case "float" -> valNode.floatValue(); // relevant?
                default -> throw new IllegalStateException("Unexpected value: " + canonicalTypeName);
            };
        }
        return null;
    }

    @Override
    public JsonDeserializer<?> createContextual(
            DeserializationContext ctxt,
            BeanProperty property
    ) throws JsonMappingException {
        JavaType type = property.getType();
        canonicalTypeName = type.toCanonical(); // say 'java.lang.String'

        return this;
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        // This is called if the JSON property is 'null'.
        // Return whatever the domain logic requires.
        return null;
    }
}