package se.fk.data.modell.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.ArrayList;
import java.util.List;

public class Modifiers {
    private Modifiers() {}

    public static final SimpleModule CONTEXT_MODULE =
            new SimpleModule()
                    .setSerializerModifier(new ContextSerializerModifier())
                    .setDeserializerModifier(new ContextDeserializerModifier());

    public static final SimpleModule MULTIDIMENSIONAL_PROPERTY_MODULE =
            new SimpleModule()
                    .setSerializerModifier(new PropertySerializerModifier())
                    .setDeserializerModifier(new PropertyDeserializerModifier());

    private static ObjectMapper setupCanonicalMapper() {
        ObjectMapper canonicalMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS) // JsonInclude.Include.NON_NULL
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        modules.add(CONTEXT_MODULE);

        // @Belopp, ... property expansion
        modules.add(MULTIDIMENSIONAL_PROPERTY_MODULE);

        return modules;
    }
}
