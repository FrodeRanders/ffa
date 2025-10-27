package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;

public class SomPropertyDeserializer extends ValueDeserializer<Object> {
    private static final Logger log = LoggerFactory.getLogger(SomPropertyDeserializer.class);

    private final JavaType valueType; // target type of the property, e.g. String, long, Double

    public SomPropertyDeserializer() {
        this(null);
    }

    private SomPropertyDeserializer(JavaType valueType) {
        this.valueType = valueType;
    }

    @Override
    public Object deserialize(
            JsonParser p,
            DeserializationContext ctxt
    ) throws JacksonException {
        JsonNode node = p.readValueAsTree();

        JsonNode valNode = node.get(PiiPropertySerializer.MAGIC_WRAPPED_PROPERTY_NAME); // "varde"
        if (valNode == null || valNode.isNull()) {
            return null;
        }

        return ctxt.readTreeAsValue(valNode, valueType);
    }

    @Override
    public ValueDeserializer<?> createContextual(
            DeserializationContext ctxt,
            BeanProperty property
    ) {
        // Capture the property's declared JavaType for use above
        return new SomPropertyDeserializer(property.getType());
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return null;
    }
}
