package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.Belopp;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;

public class BeloppPropertySerializer extends ValueSerializer<Object> {
    private static final Logger log = LoggerFactory.getLogger(BeloppPropertySerializer.class);

    public static final String MAGIC_WRAPPED_PROPERTY_NAME = "varde";

    private final String valuta;
    private final String skattestatus;
    private final String period;

    // No-arg constructor for Jackson
    public BeloppPropertySerializer() {
        this("", "", "");
    }

    // Constructor for when we have annotation values
    protected BeloppPropertySerializer(
            String valuta,
            String skattestatus,
            String period
    ) {
        this.valuta = valuta;
        this.skattestatus = skattestatus;
        this.period = period;
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
         * pepper the JSON with '@context' information. So we have a specific
         * adapter for abstract classes in our object model.
         *
         * Example:
         *
         * @JsonTypeInfo(
         *   use = JsonTypeInfo.Id.CUSTOM,
         *   include = JsonTypeInfo.As.EXISTING_PROPERTY,
         *   property = "@context",
         *   visible = true
         * )
         * @JsonTypeIdResolver(PersonTypeIdResolver.class)
         * public abstract class Person {
         * }
         */

        // Go ahead and serialise
        serialize(value, gen, ctxt);
    }

    public void serialize(Object value,
                          JsonGenerator gen,
                          SerializationContext serializers
    ) throws JacksonException {

        //--------------------------------------
        // For annotated properties like:
        //
        //    @Belopp
        //    -or-
        //    @Belopp(valuta="valuta:SEK", skattestatus="sfa:skattepliktig", period="sfa:perdag")
        //    @JsonProperty("belopp")
        //    public double belopp;
        //
        // we want to produce JSON like:
        //
        //    "belopp": {
        //       "varde": 223,
        //       "valuta": "valuta:SEK",
        //       "skattestatus": "sfa:skattefri",
        //       "period": "sfa:perdag"
        //    }
        //--------------------------------------

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

        gen.writeStringProperty("valuta", !valuta.isEmpty() ? valuta : null);
        gen.writeStringProperty("skattestatus", !skattestatus.isEmpty() ? skattestatus : null);
        gen.writeStringProperty("period", !period.isEmpty() ? period : null);

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
            Belopp annotation = property.getAnnotation(Belopp.class);
            if (null == annotation) {
                annotation = property.getContextAnnotation(Belopp.class);
            }
            if (null != annotation) {
                // Build a serializer instance configured with the annotation params
                return new BeloppPropertySerializer(
                        annotation.valuta(),
                        annotation.skattestatus(),
                        annotation.period()
                );
            }
        }
        // If there's no annotation, just return 'this' with empty defaults
        return this;
    }
}
