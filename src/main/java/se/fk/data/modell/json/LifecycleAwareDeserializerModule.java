package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.Livscykelhanterad;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;

public class LifecycleAwareDeserializerModule extends SimpleModule {
    private static final Logger log = LoggerFactory.getLogger(LifecycleAwareDeserializerModule.class);

    private final ObjectMapper canonicalMapper;

    public LifecycleAwareDeserializerModule(ObjectMapper canonicalMapper) {
        this.canonicalMapper = canonicalMapper;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public ValueDeserializer<?> modifyDeserializer(
                    DeserializationConfig config,
                    BeanDescription.Supplier beanDesc,
                    ValueDeserializer<?> deserializer
            ) {
                Class<?> beanClass = beanDesc.getBeanClass();
                if (MutationPredicates.isLifeCycleHandled(beanClass)) {
                    return new LifecycleAwareDeserializer<>(deserializer, (Class<Livscykelhanterad>) beanClass, canonicalMapper);
                }
                return deserializer;
            }
        });
        super.setupModule(context);
    }
}
