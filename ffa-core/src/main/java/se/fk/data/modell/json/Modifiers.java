package se.fk.data.modell.json;

import se.fk.data.modell.internal.LifecycleAwareDeserializer;

import se.fk.data.modell.internal.LifecycleAwareSerializer;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.util.ArrayList;

import java.util.List;

/** Samlar Jackson-modulerna för modellens gemensamma representations- och livscykelregler. */
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
        // Separat mapper utan livscykelkrokar: beräkning av en kontrollsumma får inte
        // i sin tur ändra versionen eller starta en ny livscykelserialisering.
        // Thus, the ORDER_MAP_ENTRIES_BY_KEYS below.
        //
        JsonMapper canonicalMapper = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();

        return canonicalMapper;
    }

    public static Iterable<SimpleModule> getModules() {
        List<SimpleModule> modules = new ArrayList<>();

        // Återlästa objekt får en kontrollsumma som beskriver deras ursprungstillstånd.
        JsonMapper canonicalMapper = setupCanonicalMapper();
        modules.add(new LifecycleAwareDeserializerModule(canonicalMapper));

        // Inför skrivning jämförs innehållet och ändrade objekts versioner stegas.
        modules.add(new LifecycleAwareSerializerModule(canonicalMapper));

        // Klassen bidrar med kontext, typ och eventuell ändringsflagga.
        modules.add(ANNOTATED_CLASSES_MODULE);

        // Fältannoteringarna styr omslag för personuppgifter, roller och belopp.
        modules.add(ANNOTATED_PROPERTIES_MODULE);

        return modules;
    }
}
