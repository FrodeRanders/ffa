package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;

public class ContextDeserializerModifier extends BeanDeserializerModifier {

    private final String CONTEXT_NAME = "@context";

    @Override
    public BeanDeserializerBuilder updateBuilder(
            DeserializationConfig config,
            BeanDescription beanDesc,
            BeanDeserializerBuilder builder
    ) {
        builder.addIgnorable(CONTEXT_NAME);
        return builder;
    }
}