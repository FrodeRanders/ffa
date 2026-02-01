package se.fk.data.modell.json;

import se.fk.data.modell.v1.Livscykelhanterad;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;

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
                            (Class<Livscykelhanterad>) beanClass,
                            canonicalMapper
                    );
                }
                return serializer;
            }
        });
        super.setupModule(context);
    }
}
