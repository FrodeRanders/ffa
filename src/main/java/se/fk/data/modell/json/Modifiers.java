package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.module.SimpleModule;
import se.fk.data.modell.ffa.Context;

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

    public static Iterable<SimpleModule> getModules() {
        List<SimpleModule> modules = new ArrayList<>();
        modules.add(CONTEXT_MODULE);
        modules.add(MULTIDIMENSIONAL_PROPERTY_MODULE);
        return modules;
    }
}
