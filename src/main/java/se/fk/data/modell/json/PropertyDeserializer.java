package se.fk.data.modell.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PropertyDeserializer extends JsonDeserializer<Object> implements ContextualDeserializer {
    private static final Logger log = LoggerFactory.getLogger(PropertyDeserializer.class);

    public static final String MAGIC_WRAPPED_PROPERTY_NAME = "varde";

    private transient String canonicalTypeName = null;

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // TODO Currently hardcoded for @Belopp, should ofcourse be handled dynamically
        JsonNode node = p.getCodec().readTree(p);
        JsonNode valNode = node.get(MAGIC_WRAPPED_PROPERTY_NAME);
        if (null != valNode && null != canonicalTypeName) {
            return switch (canonicalTypeName) {
                case "double" -> valNode.doubleValue();
                case "int" -> valNode.intValue();
                case "long" -> valNode.longValue();
                case "string" -> valNode.textValue();
                case "short" -> valNode.shortValue();
                case "boolean" -> valNode.asBoolean();
                case "float" -> valNode.floatValue();
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
        canonicalTypeName = type.toCanonical(); // say "double"

        return this;
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        // This is called if the JSON property is `null`.
        // Return whatever the domain logic requires.
        return null;
    }
}