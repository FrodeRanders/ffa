package se.fk.data.modell.json;

import tools.jackson.databind.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import se.fk.data.modell.v1.LivscykelHanterad;

public class LifecycleAwareSerializerModule extends SimpleModule {
    private final ObjectMapper canonicalMapper;

    public LifecycleAwareSerializerModule(ObjectMapper canonicalMapper) {
        this.canonicalMapper = canonicalMapper;
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializerModifier(new ValueSerializerModifier() {
            @Override
            public ValueSerializer<?> modifySerializer(
                    SerializationConfig config,
                    BeanDescription.Supplier beanDesc,
                    ValueSerializer<?> serializer
            ) {
                Class<?> beanClass = beanDesc.getBeanClass();
                if (MutationPredicates.isLifeCycleHandled(beanClass)) {
                    return new LifecycleAwareSerializer<>(
                            (ValueSerializer<Object>) serializer,
                            (Class<LivscykelHanterad>) beanClass,
                            canonicalMapper
                    );
                }
                return serializer;
            }
        });
        super.setupModule(context);
    }
}
