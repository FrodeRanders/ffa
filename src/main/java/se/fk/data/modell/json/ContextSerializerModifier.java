package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.*;
import com.fasterxml.jackson.databind.introspect.*;
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition;
import se.fk.data.modell.ffa.Context;

import java.util.ArrayList;
import java.util.List;

public class ContextSerializerModifier extends BeanSerializerModifier {

    private final String CONTEXT_NAME = "@context";
    private final PropertyName contextPropertyName = PropertyName.construct(CONTEXT_NAME);

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties) {

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
