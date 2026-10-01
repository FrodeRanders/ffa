package se.fk.mimer.migration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Ett verkligt formatbyte: det äldre fältnamnet producerade_resultat blir producerat_resultat. */
public final class MimerMigrations {
    public static final int CURRENT = 1;
    public static final String VERSION_FIELD = "mimer:schemaVersion";

    private MimerMigrations() {}

    /** Uppgraderar ett arbetsdokument i minnet; den lagrade signerade originalrepresentationen berörs inte. */
    public static ObjectNode migrate(JsonNode input) {
        if (!(input instanceof ObjectNode root)) {
            throw new IllegalArgumentException("Ett lagrat dokument måste vara ett JSON-objekt");
        }

        // Formatversionen är separat från verksamhetsobjektens innehållsversioner.
        JsonNode field = root.get(VERSION_FIELD);
        int version = 0; // Dokument utan formatversion tillhör det äldre formatet.
        if (field != null) {
            if (!field.isInt() || field.intValue() < 0 || field.intValue() > CURRENT) {
                throw new IllegalArgumentException("Okänd eller ogiltig formatversion: " + field);
            }
            version = field.intValue();
        }

        // Behåll innehållet och byt enbart det äldre fältnamnet. Två namn skulle vara tvetydiga.
        if (version == 0 && root.has("producerade_resultat")) {
            if (root.has("producerat_resultat")) {
                throw new IllegalArgumentException("Dokumentet innehåller båda namnen för producerat resultat");
            }
            root.set("producerat_resultat", root.remove("producerade_resultat"));
        }

        if (version == CURRENT && root.has("producerade_resultat")) {
            throw new IllegalArgumentException("Äldre fältnamn i ett dokument med aktuell formatversion");
        }

        root.put(VERSION_FIELD, CURRENT);
        return root;
    }

    /** Märker en nyskriven representation med den formatversion som denna implementation äger. */
    public static ObjectNode stamp(JsonNode input) {
        if (!(input instanceof ObjectNode root)) {
            throw new IllegalArgumentException("Ett lagrat dokument måste vara ett JSON-objekt");
        }
        root.put(VERSION_FIELD, CURRENT);
        return root;
    }
}
