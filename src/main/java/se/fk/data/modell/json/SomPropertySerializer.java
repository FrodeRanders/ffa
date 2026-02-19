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

public class SomPropertySerializer extends ValueSerializer<Object> {
    private static final Logger log = LoggerFactory.getLogger(SomPropertySerializer.class);

    public static final String MAGIC_WRAPPED_PROPERTY_NAME = "varde";

    private final String roll;

    // No-arg constructor for Jackson
    public SomPropertySerializer() {
        this("");
    }

    // Constructor for when we have annotation values
    protected SomPropertySerializer(String roll) {
        this.roll = roll;
    }

    public void serializeWithType(
            Object value,
            JsonGenerator gen,
            SerializationContext ctxt,
            TypeSerializer _typeSer
    ) throws JacksonException {
        /*
         * This is triggered when a bean stores instances of specialised classes
         * instead of the base class, e.g. having an abstract 'Person' in
         * Yrkande but assigning FysiskPerson or JuridiskPerson as value.
         *
         * During serialisation, Jackson discovers this fact and correctly
         * understands that this will be a problem upon deserialisation, since
         * it will need information about what exact class to instantiate and
         * that information is not available from type information readily
         * available.
         *
         * We have another way of handling this, utilising the fact that we
         * pepper the JSON with '@type' information. So we have a specific
         * adapter for abstract classes in our object model.
         *
         * Example:
         *
         * @JsonTypeInfo(
         *   use = JsonTypeInfo.Id.CUSTOM,
         *   include = JsonTypeInfo.As.EXISTING_PROPERTY,
         *   property = "@type",
         *   visible = true
         * )
         * @JsonTypeIdResolver(PersonTypeIdResolver.class)
         * public abstract class Person {
         * }
         */

        // Go ahead and serialise
        serialize(value, gen, ctxt);
    }

    public void serialize(
            Object value,
            JsonGenerator gen,
            SerializationContext _serializers
    ) throws JacksonException {

        //--------------------------------------
        // For annotated properties like:
        //
        //    @Som(roll="ffa:yrkanden")
        //    @JsonProperty("person")
        //    public FysiskPerson person;
        //
        // we want to produce JSON like:
        //
        //    "person": {
        //       "varde": "...",
        //       "roll": "ffa:yrkanden"
        //    }
        //--------------------------------------

        gen.writeStartObject();

        gen.writePOJOProperty(MAGIC_WRAPPED_PROPERTY_NAME, value);

        gen.writeStringProperty("roll", !roll.isEmpty() ? roll : null);

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
                return new SomPropertySerializer(annotation.roll());
            }
        }
        // If there's no annotation, just return 'this' with empty defaults
        return this;
    }
}
