package se.fk.data.modell.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.ArrayList;
import java.util.List;

public class Modifiers {

    private Modifiers() {}

    public static final SimpleModule ANNOTATED_CLASSES_MODULE =
            new SimpleModule()
                    .setSerializerModifier(new ClassSerializerModifier())
                    .setDeserializerModifier(new ClassDeserializerModifier());

    public static final SimpleModule ANNOTATED_PROPERTIES_MODULE =
            new SimpleModule()
                    .setSerializerModifier(new PropertySerializerModifier())
                    .setDeserializerModifier(new PropertyDeserializerModifier());

    private static JsonMapper setupCanonicalMapper() {
        //
        // We need to ensure the canonical mapper sorts properties and orders
        // map entries so digests are stable.
        //
        // Thus, the ORDER_MAP_ENTRIES_BY_KEYS below.
        //
        JsonMapper canonicalMapper = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();

        return canonicalMapper;
    }

    public static Iterable<SimpleModule> getModules() {
        List<SimpleModule> modules = new ArrayList<>();

        // post-deserialization hashing
        ObjectMapper canonicalMapper = setupCanonicalMapper();
        modules.add(new LifecycleAwareDeserializerModule(canonicalMapper));

        // pre-serialization compare/skip
        modules.add(new LifecycleAwareSerializerModule(canonicalMapper));

        // @Context annotation handling
        modules.add(ANNOTATED_CLASSES_MODULE);

        // @PII, @Som, @Belopp, ... property expansion
        modules.add(ANNOTATED_PROPERTIES_MODULE);

        return modules;
    }
}
