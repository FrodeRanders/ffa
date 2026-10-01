package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.jsontype.TypeDeserializer;

/** Läser värdet ur rollomslaget. Värdets egen @type avgör eventuell undertyp. */
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
    public Object deserializeWithType(
            JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer
    ) throws JacksonException {
        // Läs omslaget först. Jackson binder sedan det inre värdet till rätt undertyp.
        return deserialize(p, ctxt);
    }

    @Override
    public Object deserializeWithType(
            JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer, Object intoValue
    ) throws JacksonException {
        return deserializeWithType(p, ctxt, typeDeserializer);
    }

    @Override
    public Object deserialize(
            JsonParser p,
            DeserializationContext ctxt
    ) throws JacksonException {
        JsonNode node = p.readValueAsTree();

        JsonNode valNode = node.get(PIIPropertySerializer.MAGIC_WRAPPED_PROPERTY_NAME); // "varde"
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
        // Spara fältets deklarerade typ för bindningen av varde.
        return new SomPropertyDeserializer(property.getType());
    }
}
