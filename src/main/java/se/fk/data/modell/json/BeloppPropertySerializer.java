package se.fk.data.modell.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.Belopp;

import java.io.IOException;

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
