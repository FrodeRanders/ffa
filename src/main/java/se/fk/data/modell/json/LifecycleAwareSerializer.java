package se.fk.data.modell.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.LivscykelHanterad;

import java.io.IOException;

public final class LifecycleAwareSerializer<T> extends StdSerializer<T> {
    private static final Logger log = LoggerFactory.getLogger(LifecycleAwareSerializer.class);

    private final JsonSerializer<Object> defaultSerializer;
    private final ObjectMapper canonicalMapper;

    LifecycleAwareSerializer(
            JsonSerializer<Object> defaultSerializer,
            Class<T> type,
            ObjectMapper canonicalMapper
    ) {
        super(type);
        this.defaultSerializer = defaultSerializer;
        this.canonicalMapper = canonicalMapper;

        log.debug("Created for {}", type.getCanonicalName());
    }

    @Override
    public void serialize(T bean, JsonGenerator gen, SerializerProvider provider) throws IOException {
        MutationSemantics.BeanState beanState = MutationSemantics.of(bean);
        byte[] current = DigestUtils.computeDigest(bean, canonicalMapper);
        byte[] stored  = beanState.digest;

        boolean isNew = null == stored;
        boolean isModified = !beanState.compareWith(current);

        if (isNew) {
            log.trace("** New bean: {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
        } else if (isModified) {
            log.trace("** Modified bean: {}#{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
        }

        // Auto-increment version if bean is new or modified
        if (isNew || isModified) {
            if (bean instanceof LivscykelHanterad lifeCycledBean) {
                log.trace("Stepping version of bean: {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
                lifeCycledBean.stepVersion();

                // Recalculate digest (after having modified version)
                current = DigestUtils.computeDigest(bean, canonicalMapper);
            }
        }

        beanState.resetTo(current);

        // Delegate the actual JSON structure
        defaultSerializer.serialize(bean, gen, provider);

        log.debug("Serialized bean {}@{}", bean.getClass().getCanonicalName(), String.format("%08x", bean.hashCode()));
    }
}
