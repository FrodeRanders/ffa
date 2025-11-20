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

import java.util.ArrayList;
import java.util.List;

public class ClassSerializerModifier extends ValueSerializerModifier {
    private static final Logger log = LoggerFactory.getLogger(ClassSerializerModifier.class);

    private final String CONTEXT_NAME = "@context";
    private final PropertyName contextPropertyName = PropertyName.construct(CONTEXT_NAME);

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription.Supplier beanDesc,
            List<BeanPropertyWriter> beanProperties
    ) {

        // Check if we have a @Context annotation
        Context annotation = beanDesc.getClassAnnotations().get(Context.class);
        if (null == annotation) {
            return beanProperties;
        }

        // Add a new VirtualBeanPropertyWriter for "@context"
        List<BeanPropertyWriter> newProps = new ArrayList<>(beanProperties);
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

        ContextPropertyWriter writer = new ContextPropertyWriter(
                propDef,
                beanDesc.getClassAnnotations(),
                config.getTypeFactory().constructType(String.class)
        );

        newProps.add(0, writer); // place "@context" first
        return newProps;
    }
}
