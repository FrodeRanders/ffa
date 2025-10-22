package se.fk.data.modell.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class LifecycleAwareDeserializer<T> extends com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer {
    private static final Logger log = LoggerFactory.getLogger(LifecycleAwareDeserializer.class);

    private final Class<T> type;
    private final ObjectMapper canonicalMapper;

    public LifecycleAwareDeserializer(
            JsonDeserializer<?> delegate,
            Class<T> type,
            ObjectMapper canonicalMapper
    ) {
        super(delegate);
        this.type = type;
        this.canonicalMapper = canonicalMapper;

        log.debug("Created for {}", type.getCanonicalName());
    }

    @Override protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegate) {
        return new LifecycleAwareDeserializer<>(newDelegate, type, canonicalMapper);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        Object bean = super.deserialize(p, ctxt);
        log.debug("Deserialized bean {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));

        byte[] pristine = DigestUtils.computeDigest(bean, canonicalMapper);
        MutationSemantics.of(bean).resetTo(pristine);
        return bean;
    }
}
