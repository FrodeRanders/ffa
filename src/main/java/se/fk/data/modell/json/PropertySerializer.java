package se.fk.data.modell.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.Belopp;

import java.io.IOException;

import static se.fk.data.modell.json.PropertyDeserializer.MAGIC_WRAPPED_PROPERTY_NAME;

public class PropertySerializer extends JsonSerializer<Object> implements ContextualSerializer {
    private static final Logger log = LoggerFactory.getLogger(PropertySerializer.class);

    // TODO Currently hardcoded for @Belopp, should ofcourse be handled dynamically
    private final String valuta;
    private final String skattestatus;
    private final String period;

    // No-arg constructor for Jackson
    public PropertySerializer() {
        this("", "", "");
    }

    // Constructor for when we have annotation values
    protected PropertySerializer(
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
                          SerializerProvider serializers
    ) throws IOException {

        // We want to produce JSON like:
        // "belopp": {
        //   "varde": 223,
        //   "valuta": "valuta:SEK",
        //   "skattestatus": "sfa:skattefri",
        //   "period": "sfa:perdag"
        // }

        gen.writeStartObject();

        if (value instanceof Double d) {
            gen.writeNumberField(MAGIC_WRAPPED_PROPERTY_NAME, d);
        }
        else if (value instanceof Long l) {
            gen.writeNumberField(MAGIC_WRAPPED_PROPERTY_NAME, l);
        }
        else if (value instanceof Integer i) {
            gen.writeNumberField(MAGIC_WRAPPED_PROPERTY_NAME, i);
        }
        else if (value instanceof Boolean b) {
            gen.writeBooleanField(MAGIC_WRAPPED_PROPERTY_NAME, b);
        }
        else if (value instanceof String s) {
            gen.writeStringField(MAGIC_WRAPPED_PROPERTY_NAME, s);
        }
        else if (value instanceof Float f) {
            gen.writeNumberField(MAGIC_WRAPPED_PROPERTY_NAME, f);
        }

        // TODO Currently hardcoded for @Belopp, should ofcourse be handled dynamically
        gen.writeStringField("valuta", !valuta.isEmpty() ? valuta : null);
        gen.writeStringField("skattestatus", !skattestatus.isEmpty() ? skattestatus : null);
        gen.writeStringField("period", !period.isEmpty() ? period : null);

        gen.writeEndObject();
    }

    // This method tells Jackson how to create a serializer
    // for each annotated field, picking up annotation parameters:
    @Override
    public JsonSerializer<?> createContextual(
            SerializerProvider prov,
            BeanProperty property
    ) throws JsonMappingException {

        log.trace("Creating contextual serialiser for property: {}", property);

        if (property != null) {
            // TODO Currently hardcoded for @Belopp, should ofcourse be handled dynamically
            Belopp annotation = property.getAnnotation(Belopp.class);
            if (null == annotation) {
                annotation = property.getContextAnnotation(Belopp.class);
            }
            if (null != annotation) {
                // Build a serializer instance configured with the annotation params
                return new PropertySerializer(
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