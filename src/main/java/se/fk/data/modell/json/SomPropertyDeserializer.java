package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.jsontype.TypeDeserializer;

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
        /*
         * This is triggered when a bean stores instances of specialised classes
         * instead of the base class, e.g. having an abstract 'Person' in
         * Kundbehov but assigning FysiskPerson or JuridiskPerson as value.
         *
         * During deserialisation, Jackson discovers this fact; it will need
         * information about what exact class to instantiate and that information
         * is not readily available from type information.
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

        // Go ahead and deserialise
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
        // Capture the property's declared JavaType for use above
        return new SomPropertyDeserializer(property.getType());
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return null;
    }
}
