package se.fk.mimer.runtime;

import se.fk.data.modell.json.DeserializationSnooper;
import se.fk.data.modell.json.Modifiers;
import se.fk.mimer.migration.MimerMigrations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Intern codec med centralt fastställda serialiseringsregler och formatversion. */
public final class ModellCodec {
    private static final ModellCodec INSTANCE = new ModellCodec();
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .addModules(Modifiers.getModules())
            .addHandler(new DeserializationSnooper()).build();

    private ModellCodec() {}

    public static ModellCodec instance() {
        return INSTANCE;
    }

    /** Skriver arbetskopians livscykelmetadata och märker dokumentet med aktuellt JSON-format. */
    public byte[] serialize(Object value) {
        return mapper.writeValueAsBytes(MimerMigrations.stamp(mapper.readTree(mapper.writeValueAsBytes(value))));
    }

    /** Samma skrivregler som serialize, med indentering för inspektion i interna tester. */
    public String serializePretty(Object value) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(serialize(value)));
    }

    /**
     * Binder först efter formatmigrering. Signaturkontrollen utförs av den förvaltade gränsen
     * innan den anropar denna interna codec.
     */
    public <T> T deserialize(byte[] json, Class<T> typ) {
        return mapper.treeToValue(MimerMigrations.migrate(mapper.readTree(json)), typ);
    }

    public <T> T deserialize(String json, Class<T> typ) {
        return deserialize(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), typ);
    }
}
