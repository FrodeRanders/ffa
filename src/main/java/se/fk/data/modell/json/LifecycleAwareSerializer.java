package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.Livscykelhanterad;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

public final class LifecycleAwareSerializer<T extends Livscykelhanterad> extends StdSerializer<T> {
    private static final Logger log = LoggerFactory.getLogger(LifecycleAwareSerializer.class);

    private final ValueSerializer<Object> defaultSerializer;
    private final ObjectMapper canonicalMapper;

    LifecycleAwareSerializer(
            ValueSerializer<Object> defaultSerializer,
            Class<T> type,
            ObjectMapper canonicalMapper
    ) {
        super(type);
        this.defaultSerializer = defaultSerializer;
        this.canonicalMapper = canonicalMapper;

        log.debug("Created for {}", type.getCanonicalName());
    }

    @Override
    public void serialize(
            T bean,
            JsonGenerator gen,
            SerializationContext provider
    ) throws JacksonException {
        byte[] current = DigestUtils.computeDigest(bean, canonicalMapper);
        byte[] stored  = bean.getDigest();

        boolean isNew = null == stored;
        boolean isModified = !bean.compareDigest(current);
        if (isNew) {
            log.trace("** New bean: {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
        } else if (isModified) {
            log.trace("** Modified bean: {}#{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
        }

        // Auto-increment version if bean is new or modified
        if (isNew || isModified) {
            log.trace("Stepping version of bean: {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
            bean.stepVersion();
            bean.__attention = Boolean.TRUE;

            // Recalculate digest (after having modified version)
            current = DigestUtils.computeDigest(bean, canonicalMapper);

        }

        // Delegate the actual JSON structure
        defaultSerializer.serialize(bean, gen, provider);

        // Recompute after serialization in case serializers mutate bean state.
        current = DigestUtils.computeDigest(bean, canonicalMapper);
        bean.resetDigest(current);

        log.debug("Serialized bean {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
    }
}
