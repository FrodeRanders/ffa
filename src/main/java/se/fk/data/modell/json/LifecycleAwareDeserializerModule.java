package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.v1.LivscykelHanterad;

public class LifecycleAwareDeserializerModule extends SimpleModule {
    private static final Logger log = LoggerFactory.getLogger(LifecycleAwareDeserializerModule.class);

    private final ObjectMapper canonicalMapper;

    public LifecycleAwareDeserializerModule(ObjectMapper canonicalMapper) {
        this.canonicalMapper = canonicalMapper;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addBeanDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyDeserializer(
                    DeserializationConfig config,
                    BeanDescription beanDesc,
                    JsonDeserializer<?> deserializer
            ) {
                Class<?> beanClass = beanDesc.getBeanClass();
                if (MutationPredicates.isLifeCycleHandled(beanClass)) {
                    return new LifecycleAwareDeserializer<>(deserializer, (Class<LivscykelHanterad>) beanClass, canonicalMapper);
                }
                return deserializer;
            }
        });
        super.setupModule(context);
    }
}
