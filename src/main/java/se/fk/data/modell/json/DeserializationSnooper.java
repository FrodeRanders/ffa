package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.jsontype.TypeIdResolver;

public class DeserializationSnooper extends DeserializationProblemHandler {
    private static final Logger log = LoggerFactory.getLogger(DeserializationSnooper.class);
    private static final java.util.Set<String> IGNORED_PROPERTIES = java.util.Set.of(
            "@type", "@context", "__attention", "mimer:schemaVersion"
    );

    public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p, ValueDeserializer<?> deserializer, Object beanOrClass, String propertyName) throws JacksonException {
        if (IGNORED_PROPERTIES.contains(propertyName)) {
            p.skipChildren();
            return true;
        }
        log.warn("Unknown property '{}' encountered in class {}", propertyName, beanOrClass);
        p.skipChildren();
        return false;
    }

    @Override
    public Object handleWeirdKey(DeserializationContext ctxt, Class<?> rawKeyType, String keyValue, String failureMsg) throws JacksonException {
        log.warn("Weird key encountered in class {}: {}", rawKeyType, failureMsg);
        return super.handleWeirdKey(ctxt, rawKeyType, keyValue, failureMsg);
    }

    @Override
    public Object handleWeirdStringValue(DeserializationContext ctxt, Class<?> targetType, String valueToConvert, String failureMsg) throws JacksonException {
        log.warn("Weird string value encountered in class {}: {}", targetType, failureMsg);
        return super.handleWeirdStringValue(ctxt, targetType, valueToConvert, failureMsg);
    }

    @Override
    public Object handleWeirdNumberValue(DeserializationContext ctxt, Class<?> targetType, Number valueToConvert, String failureMsg) throws JacksonException {
        log.warn("Weird number value encountered in class {}: {}", targetType, failureMsg);
        return super.handleWeirdNumberValue(ctxt, targetType, valueToConvert, failureMsg);
    }

    @Override
    public Object handleWeirdNativeValue(DeserializationContext ctxt, JavaType targetType, Object valueToConvert, JsonParser p) throws JacksonException {
        log.warn("Weird native value encountered in class {}: {}", targetType, valueToConvert);
        return super.handleWeirdNativeValue(ctxt, targetType, valueToConvert, p);
    }

    @Override
    public Object handleUnexpectedToken(DeserializationContext ctxt, JavaType targetType, JsonToken t, JsonParser p, String failureMsg) throws JacksonException {
        log.warn("Unexpected token '{}' encountered in class {}: {}", t, targetType, failureMsg);
        return super.handleUnexpectedToken(ctxt, targetType, t, p, failureMsg);
    }

    @Override
    public Object handleInstantiationProblem(DeserializationContext ctxt, Class<?> instClass, Object argument, Throwable t) throws JacksonException {
        log.warn("Instantiation problem encountered in class {}: {}", instClass, argument, t);
        return super.handleInstantiationProblem(ctxt, instClass, argument, t);
    }

    @Override
    public Object handleMissingInstantiator(DeserializationContext ctxt, Class<?> instClass, ValueInstantiator valueInsta, JsonParser p, String msg) throws JacksonException {
        log.warn("Missing instantiator encountered in class {}: {}", instClass, msg);
        return super.handleMissingInstantiator(ctxt, instClass, valueInsta, p, msg);
    }

    @Override
    public JavaType handleUnknownTypeId(DeserializationContext ctxt, JavaType baseType, String subTypeId, TypeIdResolver idResolver, String failureMsg) throws JacksonException {
        log.warn("Unknown type id encountered in class {}: {}", baseType, failureMsg);
        return null;
    }

    @Override
    public JavaType handleMissingTypeId(DeserializationContext ctxt, JavaType baseType, TypeIdResolver idResolver, String failureMsg) throws JacksonException {
        log.warn("Missing type id encountered in class {}: {}", baseType, failureMsg);
        return null;
    }
}
