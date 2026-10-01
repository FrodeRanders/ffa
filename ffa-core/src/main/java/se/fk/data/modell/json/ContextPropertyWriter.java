package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.Context;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;

/** Skriver @context från modellklassens @Context-annotering. */
public class ContextPropertyWriter extends VirtualBeanPropertyWriter {
    private static final Logger log = LoggerFactory.getLogger(ContextPropertyWriter.class);

    public ContextPropertyWriter() { // Behövs när Jackson instansierar egenskapsskrivaren.
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
     * Returnerar den kontext som modellen anger, eller null om kontext saknas.
     */
    @Override
    protected Object value(
            Object bean,
            JsonGenerator gen,
            SerializationContext prov
    ) throws Exception {
        // Kontexten hämtas från modellklassen, inte från förmånskoden.
        Context annotation = bean.getClass().getAnnotation(Context.class);
        if (null != annotation) {
            String contextUri = annotation.value();
            if (!contextUri.isEmpty()) {
                return contextUri;
            }
        }
        return null; // Klassen har ingen kontext att skriva.
    }
}
