package se.fk.data.modell.json;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.ValueDeserializerModifier;

public class ClassDeserializerModifier extends ValueDeserializerModifier {

    private final String CONTEXT_NAME = "@context";
    private final String ATTENTION_FLAG_NAME = "__attention";

    @Override
    public BeanDeserializerBuilder updateBuilder(
            DeserializationConfig config,
            BeanDescription.Supplier beanDesc,
            BeanDeserializerBuilder builder
    ) {
        builder.addIgnorable(CONTEXT_NAME);
        builder.addIgnorable(ATTENTION_FLAG_NAME);
        return builder;
    }
}