package se.fk.mimer.migration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static se.fk.mimer.migration.MigrationEngine.*;

/** Förvaltad historik över JSON-format. Befintliga steg behålls när nya versioner tillkommer. */
public final class MimerMigrations {
    public static final int CURRENT = 2;
    public static final String VERSION_FIELD = "mimer:schemaVersion";
    private static final MigrationEngine ENGINE = new MigrationEngine();

    private MimerMigrations() {}

    /** Lägg till nästa steg här och höj CURRENT; tidigare steg ska ligga kvar. */
    public static List<Migration> all() {
        return List.of(
                new Migration("Resultatstruktur", 0, 1, List.of(
                        renameField("Resultatets namn", "$.producerade_resultat", "producerat_resultat"),
                        ensureArray("Resultat som lista", "$.producerat_resultat"))),
                new Migration("Beloppets värdefält", 1, 2, List.of(
                        renameField("Summa blir värde", "$.producerat_resultat[*].belopp.summa", "varde"))));
    }

    /** Uppgraderar arbetsdokumentet i minnet innan någon Java-modell instansieras. */
    public static ObjectNode migrate(JsonNode input) {
        int version = readSchemaVersion(input);
        if (version >= 1 && input.has("producerade_resultat")) {
            throw new IllegalArgumentException("Äldre fältnamn i ett dokument med senare formatversion");
        }
        if (version == CURRENT && input.path("producerat_resultat").isArray()) {
            for (JsonNode result : input.path("producerat_resultat")) {
                if (result.path("belopp").has("summa")) {
                    throw new IllegalArgumentException("Äldre beloppsfält i ett dokument med aktuell formatversion");
                }
            }
        }
        return (ObjectNode) ENGINE.applyUpToCurrent(input, all(), CURRENT).root;
    }

    /** Nyskrivna dokument använder alltid det aktuella formatet. */
    public static ObjectNode stamp(JsonNode input) {
        if (!(input instanceof ObjectNode root)) {
            throw new IllegalArgumentException("Ett lagrat dokument måste vara ett JSON-objekt");
        }
        root.put(VERSION_FIELD, CURRENT);
        return root;
    }
}
