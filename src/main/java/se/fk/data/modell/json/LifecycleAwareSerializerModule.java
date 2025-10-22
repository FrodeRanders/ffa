package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

public class LifecycleAwareSerializerModule extends SimpleModule {
    private final ObjectMapper canonicalMapper;

    public LifecycleAwareSerializerModule(ObjectMapper canonicalMapper) {
        this.canonicalMapper = canonicalMapper;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addBeanSerializerModifier(new BeanSerializerModifier() {
            @Override
            public JsonSerializer<?> modifySerializer(
                    SerializationConfig config,
                    BeanDescription beanDesc,
                    JsonSerializer<?> serializer
            ) {
                if (MutationPredicates.isTrackedClass(beanDesc.getBeanClass())) {
                    return new LifecycleAwareSerializer<>((JsonSerializer<Object>) serializer, beanDesc.getBeanClass(), canonicalMapper);
                }
                return serializer;
            }
        });
        super.setupModule(context);
    }
}
