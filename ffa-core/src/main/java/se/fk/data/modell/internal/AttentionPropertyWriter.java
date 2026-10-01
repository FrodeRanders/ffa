package se.fk.data.modell.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.Livscykelhanterad;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;

/**
 * Skriver arbetskopians ändringsflagga som en virtuell JSON-egenskap.
 */
public class AttentionPropertyWriter extends VirtualBeanPropertyWriter {
    private static final Logger log = LoggerFactory.getLogger(AttentionPropertyWriter.class);

    public AttentionPropertyWriter() { // Behövs när Jackson instansierar egenskapsskrivaren.
        super();
    }

    public AttentionPropertyWriter(
            BeanPropertyDefinition propDef,
            Annotations contextAnnotations,
            JavaType declaredType
    ) {
        super(propDef, contextAnnotations, declaredType);
    }

    @Override
    public VirtualBeanPropertyWriter withConfig(
            MapperConfig<?> config,
            AnnotatedClass declaringClass,
            BeanPropertyDefinition propDef,
            JavaType type
    ) {
        return new AttentionPropertyWriter(propDef, declaringClass.getAnnotations(), type);
    }

    /**
     * Flaggan förbrukas vid skrivning så att den inte följer med nästa oförändrade serialisering.
     */
    @Override
    protected Object value(
            Object bean,
            JsonGenerator gen,
            SerializationContext prov
    ) throws Exception {
        if (bean instanceof Livscykelhanterad lhb) {
            return ((LifecycleState) lhb).consumeAttention(); // null innebär att flaggan utelämnas.
        }
        return null;
    }
}
