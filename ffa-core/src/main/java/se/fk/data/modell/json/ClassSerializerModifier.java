package se.fk.data.modell.json;

import se.fk.data.modell.internal.AttentionPropertyWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.Context;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.VirtualAnnotatedMember;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.util.SimpleBeanPropertyDefinition;

import se.fk.data.modell.v1.Livscykelhanterad;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lägger till @context, @type och __attention som virtuella JSON-egenskaper.
 * Dessa transportfält behöver inga motsvarande verksamhetsfält i modellen.
 */
public class ClassSerializerModifier extends ValueSerializerModifier {
    private static final Logger log = LoggerFactory.getLogger(ClassSerializerModifier.class);
    private static final Set<Class<?>> WARNED_MISSING_CONTEXT = ConcurrentHashMap.newKeySet();

    private final String CONTEXT_NAME = "@context";
    private final PropertyName contextPropertyName = PropertyName.construct(CONTEXT_NAME);

    private final String TYPE_NAME = "@type";
    private final PropertyName typePropertyName = PropertyName.construct(TYPE_NAME);

    private final String ATTENTION_FLAG_NAME = "__attention";
    private final PropertyName attentionFlagPropertyName = PropertyName.construct(ATTENTION_FLAG_NAME);

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription.Supplier beanDesc,
            List<BeanPropertyWriter> beanProperties
    ) {
        List<BeanPropertyWriter> newProps = new ArrayList<>(beanProperties);

        // Endast livscykelobjekt får transportens ändringsflagga.
        if (Livscykelhanterad.class.isAssignableFrom(beanDesc.getBeanClass())) {

            // Flaggan skrivs virtuellt och finns inte som ett offentligt modellfält.
            JavaType boolType = config.getTypeFactory().constructType(Boolean.class);

            // Definiera transportfältet utan att lägga till ett verksamhetsfält.
            BeanPropertyDefinition propDef = SimpleBeanPropertyDefinition
                    .construct(
                            config,
                            new VirtualAnnotatedMember(
                                    beanDesc.getClassInfo(),
                                    beanDesc.getBeanClass(),
                                    ATTENTION_FLAG_NAME,
                                    boolType
                            ),
                            attentionFlagPropertyName
                    );

            AttentionPropertyWriter propertyWriter = new AttentionPropertyWriter(
                    propDef,
                    beanDesc.getClassAnnotations(),
                    config.getTypeFactory().constructType(String.class)
            );

            newProps.addFirst(propertyWriter);

        }

        // Modellannoteringen anger den gemensamma kontexten för representationen.
        Context contextAnnotation = beanDesc.getClassAnnotations().get(Context.class);
        if (null != contextAnnotation) {
            JavaType strType = config.getTypeFactory().constructType(String.class);

            // Definiera typfältet som en virtuell egenskap.
            BeanPropertyDefinition typePropDef = SimpleBeanPropertyDefinition
                    .construct(
                            config,
                            new VirtualAnnotatedMember(
                                    beanDesc.getClassInfo(),
                                    beanDesc.getBeanClass(),
                                    TYPE_NAME,
                                    strType
                            ),
                            typePropertyName
                    );

            TypePropertyWriter typePropertyWriter = new TypePropertyWriter(
                    typePropDef,
                    beanDesc.getClassAnnotations(),
                    config.getTypeFactory().constructType(String.class)
            );

            newProps.addFirst(typePropertyWriter);

            // Definiera transportfältet utan att lägga till ett verksamhetsfält.
            BeanPropertyDefinition propDef = SimpleBeanPropertyDefinition
                    .construct(
                            config,
                            new VirtualAnnotatedMember(
                                    beanDesc.getClassInfo(),
                                    beanDesc.getBeanClass(),
                                    CONTEXT_NAME,
                                    strType
                            ),
                            contextPropertyName
                    );

            ContextPropertyWriter propertyWriter = new ContextPropertyWriter(
                    propDef,
                    beanDesc.getClassAnnotations(),
                    config.getTypeFactory().constructType(String.class)
            );

            newProps.addFirst(propertyWriter); // Placera kontexten före typfältet.
        } else {
            Class<?> beanClass = beanDesc.getBeanClass();
            if (WARNED_MISSING_CONTEXT.add(beanClass)) {
                log.warn("Missing @Context on {}. JSON will omit @context, treat as private domain data.", beanClass.getName());
            }
        }
        return newProps;
    }
}
