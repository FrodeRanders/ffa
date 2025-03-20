package se.fk.data.modell.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.NullValueProvider;

import java.io.IOException;

public class PropertyDeserializer
        extends JsonDeserializer<Object>
        implements ContextualDeserializer, NullValueProvider {

    private static final String MAGIC_WRAPPED_PROPERTY_NAME = "value";

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        JsonNode valNode = node.get(MAGIC_WRAPPED_PROPERTY_NAME);
        if (null != valNode) {
            if (valNode.isNumber()) {
                return valNode.doubleValue();
            }
        }
        return null;
    }

    @Override
    public JsonDeserializer<?> createContextual(
            DeserializationContext ctxt,
            BeanProperty property
    ) throws JsonMappingException {
        return this;
    }
}