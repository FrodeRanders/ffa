package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.Livscykelhanterad;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;

public final class LifecycleAwareDeserializer<T extends Livscykelhanterad> extends tools.jackson.databind.deser.std.DelegatingDeserializer {
    private static final Logger log = LoggerFactory.getLogger(LifecycleAwareDeserializer.class);

    private final Class<T> type;
    private final ObjectMapper canonicalMapper;

    public LifecycleAwareDeserializer(
            ValueDeserializer<?> delegate,
            Class<T> type,
            ObjectMapper canonicalMapper
    ) {
        super(delegate);
        this.type = type;
        this.canonicalMapper = canonicalMapper;

        log.debug("Created for {}", type.getCanonicalName());
    }

    @Override protected ValueDeserializer<?> newDelegatingInstance(ValueDeserializer<?> newDelegate) {
        return new LifecycleAwareDeserializer<>(newDelegate, type, canonicalMapper);
    }

    @Override
    public Object deserialize(
            JsonParser p,
            DeserializationContext ctxt
    ) throws JacksonException {

        T bean = (T) super.deserialize(p, ctxt);
        log.debug("Deserialized bean {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));

        byte[] pristine = DigestUtils.computeDigest(bean, canonicalMapper);
        bean.resetDigest(pristine);
        return bean;
    }
}
