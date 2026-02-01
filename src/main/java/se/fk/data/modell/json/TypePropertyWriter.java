package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;

/**
 * A virtual property that writes "@type" using the class name.
 */
public class TypePropertyWriter extends VirtualBeanPropertyWriter {
    private static final Logger log = LoggerFactory.getLogger(TypePropertyWriter.class);

    public TypePropertyWriter() { // Needed for Jackson
        super();
    }

    protected TypePropertyWriter(
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
        return new TypePropertyWriter(propDef, declaringClass.getAnnotations(), type);
    }

    @Override
    protected Object value(
            Object bean,
            JsonGenerator gen,
            SerializationContext prov
    ) {
        return bean.getClass().getName();
    }
}
