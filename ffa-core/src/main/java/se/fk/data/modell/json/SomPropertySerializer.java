package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.Som;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;

/** Skriver ett värde och dess roll enligt @Som, exempelvis en person som yrkande part. */
public class SomPropertySerializer extends ValueSerializer<Object> {
    private static final Logger log = LoggerFactory.getLogger(SomPropertySerializer.class);

    public static final String MAGIC_WRAPPED_PROPERTY_NAME = "varde";

    private final String roll;

    // Jackson behöver en konstruktor utan annoteringsparametrar.
    public SomPropertySerializer() {
        this("");
    }

    // Skapar en instans med det aktuella fältets annoteringsparametrar.
    protected SomPropertySerializer(String roll) {
        this.roll = roll;
    }

    public void serializeWithType(
            Object value,
            JsonGenerator gen,
            SerializationContext ctxt,
            TypeSerializer _typeSer
    ) throws JacksonException {
        // Omslaget skrivs här; värdets egen modellrepresentation bär eventuell @type.
        serialize(value, gen, ctxt);
    }

    public void serialize(
            Object value,
            JsonGenerator gen,
            SerializationContext _serializers
    ) throws JacksonException {

        // Transportform: {"varde": modellobjekt, "roll": organisatorisk roll}.

        gen.writeStartObject();

        gen.writePOJOProperty(MAGIC_WRAPPED_PROPERTY_NAME, value);

        gen.writeStringProperty("roll", !roll.isEmpty() ? roll : null);

        gen.writeEndObject();
    }

    // Jackson anropar detta för att anpassa skrivaren till det aktuella fältet.
    @Override
    public ValueSerializer<?> createContextual(
            SerializationContext prov,
            BeanProperty property
    ) {
        log.trace("Creating contextual serialiser for property: {}", property);

        if (property != null) {
            Som annotation = property.getAnnotation(Som.class);
            if (null == annotation) {
                annotation = property.getContextAnnotation(Som.class);
            }
            if (null != annotation) {
                // Varje fält får en skrivare med sina egna annoteringsparametrar.
                return new SomPropertySerializer(annotation.roll());
            }
        }
        // Utan annotering används instansens standardvärden.
        return this;
    }
}
