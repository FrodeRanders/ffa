package se.fk.data.modell.json;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.ValueDeserializerModifier;

public class ClassDeserializerModifier extends ValueDeserializerModifier {

    private final String JSONLD_CONTEXT_NAME = "@context";

    @Override
    public BeanDeserializerBuilder updateBuilder(
            DeserializationConfig config,
            BeanDescription.Supplier beanDesc,
            BeanDeserializerBuilder builder
    ) {
        builder.addIgnorable(JSONLD_CONTEXT_NAME);
        return builder;
    }
}