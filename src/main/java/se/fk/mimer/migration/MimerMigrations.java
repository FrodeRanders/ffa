package se.fk.mimer.migration;

import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static se.fk.mimer.migration.MigrationEngine.*;

public final class MimerMigrations {

    public static final int CURRENT = 2;

    public static List<Migration> all() {
        Migration v0to1 = new Migration("kundbehov->yrkan", 0, 1, List.of(
                // Run-once root updates: selector "$" matches root path "$"
                new Rule("set @context", "$", (root, match, audit) -> {
                    if (root instanceof ObjectNode obj) {
                        obj.put("@context", "https://data.fk.se/kontext/hundbidrag/yrkan/1.0");
                        audit.add(new AuditEntry("set @context", "$", match.matchedPath, "/@context", "set", "updated @context"));
                    }
                }),
                new Rule("set @type", "$", (root, match, audit) -> {
                    if (root instanceof ObjectNode obj) {
                        obj.put("@type", "se.fk.hundbidrag.modell.Yrkan");
                        audit.add(new AuditEntry("set @type", "$", match.matchedPath, "/@type", "set", "updated @type"));
                    }
                }),

                moveFieldToSibling("rename person.typ->roll", "$.person.typ", "roll"),

                normalizeInstantZToDate("normalize beslut.datum", "$.beslut.datum"),
                normalizeInstantZToDate("normalize period.from", "$.producerade_resultat[*].period.from"),
                normalizeInstantZToDate("normalize period.tom",  "$.producerade_resultat[*].period.tom")
        ));

        // Illustrative future step 1 -> 2
        Migration v1to2 = new Migration("example future changes", 1, 2, List.of(
                // Ensure a default field exists
                setDefaultStringIfMissing("default person.land", "$.person", "land", "SE"),

                // Ensure something becomes an array
                ensureArray("ensure producerade_resultat is array", "$.producerade_resultat"),

                // Map enum values
                mapStringValues("map person.roll vocab", "$.person.roll",
                        Map.of("ffa:yrkande", "mimer:yrkande")),

                // Coerce numeric strings
                coerceStringToInt("coerce beslut.id -> int", "$.beslut.id"),

                // Dedupe results by id if you ever get duplicates
                dedupeArrayByKey("dedupe producerade_resultat by id",
                        "$.producerade_resultat", "id")
        ));

        return List.of(v0to1, v1to2);
    }
}
