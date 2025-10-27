package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.PII;
import se.fk.data.modell.ffa.Som;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class SomPropertySerializer extends ValueSerializer<Object> {
    private static final Logger log = LoggerFactory.getLogger(SomPropertySerializer.class);

    public static final String MAGIC_WRAPPED_PROPERTY_NAME = "varde";

    private final String typ;

    // No-arg constructor for Jackson
    public SomPropertySerializer() {
        this("");
    }

    // Constructor for when we have annotation values
    protected SomPropertySerializer(
            String typ
    ) {
        this.typ = typ;
    }

    public void serialize(
            Object value,
            JsonGenerator gen,
            SerializationContext serializers
    ) throws JacksonException {

        //--------------------------------------
        // For annotated properties like:
        //
        //    @Som(typ="ffa:yrkande")
        //    @JsonProperty("person")
        //    public FysiskPerson person;
        //
        // we want to produce JSON like:
        //
        //    "person": {
        //       TODO
        //       "varde": "...",
        //       "typ": "ffa:yrkande"
        //    }
        //--------------------------------------

        gen.writeStartObject();

        gen.writePOJOProperty(MAGIC_WRAPPED_PROPERTY_NAME, value);

        gen.writeStringProperty("typ", !typ.isEmpty() ? typ : null);

        gen.writeEndObject();
    }

    // This method tells Jackson how to create a serializer
    // for each annotated field, picking up annotation parameters:
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
                // Build a serializer instance configured with the annotation params
                return new SomPropertySerializer(
                        annotation.typ()
                );
            }
        }
        // If there's no annotation, just return 'this' with empty defaults
        return this;
    }
}
