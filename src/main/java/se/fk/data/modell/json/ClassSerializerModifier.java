package se.fk.data.modell.json;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ser.*;
import tools.jackson.databind.introspect.*;
import tools.jackson.databind.util.SimpleBeanPropertyDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.Context;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ClassSerializerModifier extends ValueSerializerModifier {
    private static final Logger log = LoggerFactory.getLogger(ClassSerializerModifier.class);

    private final String CONTEXT_NAME = "@context";
    private final PropertyName contextPropertyName = PropertyName.construct(CONTEXT_NAME);

    private final String ATTENTION_FLAG_NAME = "__attention";
    private final PropertyName attentionFlagPropertyName = PropertyName.construct(ATTENTION_FLAG_NAME);

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription.Supplier beanDesc,
            List<BeanPropertyWriter> beanProperties
    ) {
        List<BeanPropertyWriter> newProps = new ArrayList<>(beanProperties);

        // Check if we have an attention flag "__attention".
        try {
            Field attentionFlag = beanDesc.getBeanClass().getField(ATTENTION_FLAG_NAME);

            // If we are still here, the named field exists
            JavaType boolType = config.getTypeFactory().constructType(Boolean.class);

            // Create a BeanPropertyDefinition for the virtual property
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

        } catch (NoSuchFieldException ignore) {}

        // Check if we have a @Context annotation
        Context contextAnnotation = beanDesc.getClassAnnotations().get(Context.class);
        if (null != contextAnnotation) {
            JavaType strType = config.getTypeFactory().constructType(String.class);

            // Create a BeanPropertyDefinition for the virtual property
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

            newProps.addFirst(propertyWriter); // place "@context" first
        }
        return newProps;
    }
}
