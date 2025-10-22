package se.fk.data.modell.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.Annotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.Context;

/**
 * A virtual property that writes "@context" using the URI
 * found in the bean's `@TypeContext` annotation.
 */
public class ContextPropertyWriter extends VirtualBeanPropertyWriter {
    private static final Logger log = LoggerFactory.getLogger(ContextPropertyWriter.class);

    private static final long serialVersionUID = 1L;

    public ContextPropertyWriter() { // Needed for Jackson
        super();
    }

    protected ContextPropertyWriter(
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
        return new ContextPropertyWriter(propDef, declaringClass.getAnnotations(), type);
    }

    /**
     * This method is called to figure out the value to serialize.
     * We will read the bean's @TypeContext annotation to get the URI.
     */
    @Override
    protected Object value(
            Object bean,
            JsonGenerator gen,
            SerializerProvider prov
    ) throws Exception {
        // Look for @Context annotation
        Context annotation = bean.getClass().getAnnotation(Context.class);
        if (null != annotation) {
            String contextUri = annotation.value();
            if (!contextUri.isEmpty()) {
                return contextUri;
            }
        }
        return null; // no context
    }
}
