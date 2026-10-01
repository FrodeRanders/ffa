package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.PII;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;

/**
 * Skriver ett personrelaterat värde tillsammans med dess klassificering enligt @PII.
 * Klassificeringen är metadata; den krypterar eller maskerar inte värdet.
 */
public class PIIPropertySerializer extends ValueSerializer<Object> {
    private static final Logger log = LoggerFactory.getLogger(PIIPropertySerializer.class);

    public static final String MAGIC_WRAPPED_PROPERTY_NAME = "varde";

    private final String typ;

    // Jackson behöver en konstruktor utan annoteringsparametrar.
    public PIIPropertySerializer() {
        this("");
    }

    // Skapar en instans med det aktuella fältets annoteringsparametrar.
    protected PIIPropertySerializer(
            String typ
    ) {
        this.typ = typ;
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
            SerializationContext serializers
    ) throws JacksonException {

        // Transportform: {"varde": personnummer, "typ": klassificering}.

        gen.writeStartObject();

        if (value instanceof Double d) {
            gen.writeNumberProperty(MAGIC_WRAPPED_PROPERTY_NAME, d);
        }
        else if (value instanceof Long l) {
            gen.writeNumberProperty(MAGIC_WRAPPED_PROPERTY_NAME, l);
        }
        else if (value instanceof Integer i) {
            gen.writeNumberProperty(MAGIC_WRAPPED_PROPERTY_NAME, i);
        }
        else if (value instanceof Boolean b) {
            gen.writeBooleanProperty(MAGIC_WRAPPED_PROPERTY_NAME, b);
        }
        else if (value instanceof String s) {
            gen.writeStringProperty(MAGIC_WRAPPED_PROPERTY_NAME, s);
        }
        else if (value instanceof Float f) {
            gen.writeNumberProperty(MAGIC_WRAPPED_PROPERTY_NAME, f);
        }

        gen.writeStringProperty("typ", !typ.isEmpty() ? typ : null);

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
            PII annotation = property.getAnnotation(PII.class);
            if (null == annotation) {
                annotation = property.getContextAnnotation(PII.class);
            }
            if (null != annotation) {
                // Varje fält får en skrivare med sina egna annoteringsparametrar.
                return new PIIPropertySerializer(
                        annotation.typ()
                );
            }
        }
        // Utan annotering används instansens standardvärden.
        return this;
    }
}
