package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.LivscykelHanterad;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;

/**
 * A virtual property that handles "__attention".
 */
public class AttentionPropertyWriter extends VirtualBeanPropertyWriter {
    private static final Logger log = LoggerFactory.getLogger(AttentionPropertyWriter.class);

    public AttentionPropertyWriter() { // Needed for Jackson
        super();
    }

    protected AttentionPropertyWriter(
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
     * This method is called to figure out the value to serialize.
     */
    @Override
    protected Object value(
            Object bean,
            JsonGenerator gen,
            SerializationContext prov
    ) throws Exception {
        if (bean instanceof LivscykelHanterad lhb) {
            Boolean value = lhb.__attention;
            lhb.__attention = null; // reset flag

            return value; // may be null, in which case we ignore this field
        }
        return null;
    }
}
