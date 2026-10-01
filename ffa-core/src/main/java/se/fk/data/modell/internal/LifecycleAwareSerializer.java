package se.fk.data.modell.internal;

import se.fk.data.modell.utils.DigestUtils;
import se.fk.data.modell.v1.Livscykelhanterad;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.std.StdSerializer;

/** Identifierar ändrade objekt och skriver livscykelmetadata i arbetskopian. */
public final class LifecycleAwareSerializer<T extends Livscykelhanterad> extends StdSerializer<T> {
    private final ValueSerializer<Object> delegate;
    private final ObjectMapper canonicalMapper;

    public LifecycleAwareSerializer(ValueSerializer<Object> delegate, Class<T> type, ObjectMapper canonicalMapper) {
        super(type);
        this.delegate = delegate;
        this.canonicalMapper = canonicalMapper;
    }

    @Override
    public void serialize(T bean, JsonGenerator gen, SerializationContext context) {
        write(bean, gen, context, null);
    }

    @Override
    public void serializeWithType(T bean, JsonGenerator gen, SerializationContext context, TypeSerializer type) {
        write(bean, gen, context, type);
    }

    private void write(T bean, JsonGenerator gen, SerializationContext context, TypeSerializer type) {
        LifecycleState state = bean;

        // Kontrollsummemappern saknar livscykelkrokar och kan därför läsa utan att stega versioner.
        byte[] current = DigestUtils.computeDigest(bean, canonicalMapper);
        if (state.digest() == null || !state.compareDigest(current))
            state.stepVersion();

        // Låt Jacksons vanliga skrivare behålla modellens form och eventuell polymorfism.
        if (type == null)
            delegate.serialize(bean, gen, context);
        else
            delegate.serializeWithType(bean, gen, context, type);

        // Underobjekten kan ha fått nya versioner under serialiseringen.
        state.resetDigest(DigestUtils.computeDigest(bean, canonicalMapper));
    }
}
