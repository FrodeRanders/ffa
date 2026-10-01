package se.fk.mimer.migration;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.mimer.runtime.ModellCodec;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static se.fk.mimer.migration.MigrationEngine.*;

/** Verifierar att officiella JsonPath kan välja och förändra Jackson 3-träd. */
class MigrationEngineTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MigrationEngine engine = new MigrationEngine();

    @Test
    void efterfoljandeReglerSerNamnbyteOchAllaArrayelement() {
        var root = mapper.readTree("""
                {"producerade_resultat": [
                    {"datum": "2025-01-01T00:00:00Z"},
                    {"datum": "2025-02-01T00:00:00Z"}
                ]}
                """);
        var migration = new Migration("v0 till v1", 0, 1, List.of(
                renameField("resultat", "$.producerade_resultat", "producerat_resultat"),
                normalizeInstantZToDate("datum", "$.producerat_resultat[*].datum")));

        var result = engine.applyUpToCurrent(root, List.of(migration), 1);

        assertSame(root, result.root);
        assertFalse(root.has("producerade_resultat"));
        assertEquals("2025-01-01", root.at("/producerat_resultat/0/datum").asString());
        assertEquals("2025-02-01", root.at("/producerat_resultat/1/datum").asString());
        assertEquals(1, readSchemaVersion(root));
        assertEquals(4, result.audit.size());
        assertTrue(engine.applyUpToCurrent(root, List.of(migration), 1).audit.isEmpty());
    }

    @Test
    void saknadeSokvagarGerIngaAndringar() {
        var root = mapper.readTree("{\"bevarat\": 42}");
        var migration = new Migration("valfritt fält", 0, 1, List.of(
                renameField("saknat", "$.saknat", "nytt")));

        var result = engine.applyUpToCurrent(root, List.of(migration), 1);

        assertEquals(mapper.readTree("{\"bevarat\":42,\"mimer:schemaVersion\":1}"), result.root);
        assertEquals(1, result.audit.size());
        assertEquals("set", result.audit.getFirst().action);
    }

    @Test
    void kedjanUtgarFranDokumentetsVersionOchRegistreringsordningenSaknarBetydelse() {
        var first = new Migration("första", 0, 1, List.of(renameField("a till b", "$.a", "b")));
        var second = new Migration("andra", 1, 2, List.of(renameField("b till c", "$.b", "c")));
        var steps = List.of(second, first);
        var old = mapper.readTree("{\"a\":42}");
        var intermediate = mapper.readTree("{\"b\":42,\"mimer:schemaVersion\":1}");

        var result = engine.applyUpToCurrent(old, steps, 2);
        var resumed = engine.applyUpToCurrent(intermediate, steps, 2);

        assertEquals(mapper.readTree("{\"c\":42,\"mimer:schemaVersion\":2}"), result.root);
        assertEquals(result.root, resumed.root);
        assertEquals(List.of("första", "andra"), result.audit.stream()
                .filter(entry -> entry.action.equals("set")).map(entry -> entry.ruleName).toList());
        assertEquals(List.of("b till c", "andra"), resumed.audit.stream().map(entry -> entry.ruleName).toList());
    }

    @Test
    void ofullstandigEllerTvetydigKedjaAvvisasInnanDokumentetAndras() {
        var first = new Migration("första", 0, 1, List.of(renameField("a till b", "$.a", "b")));
        var last = new Migration("sista", 2, 3, List.of());
        var original = mapper.readTree("{\"a\":42}");
        var root = original.deepCopy();

        assertThrows(IllegalArgumentException.class, () -> engine.applyUpToCurrent(root, List.of(first, last), 3));
        assertEquals(original, root);
        assertThrows(IllegalArgumentException.class, () -> engine.applyUpToCurrent(root, List.of(first, first), 1));
        assertEquals(original, root);
        assertThrows(IllegalArgumentException.class, () -> engine.applyUpToCurrent(root,
                List.of(new Migration("cykel", 0, 0, List.of())), 1));
        assertThrows(IllegalArgumentException.class, () -> engine.applyUpToCurrent(root,
                List.of(new Migration("överskjutning", 0, 2, List.of())), 1));
    }

    @Test
    void framtidaOchOgiltigaVersionerAvvisas() {
        for (String version : List.of("99", "-1", "null", "\"1\"", "1.5")) {
            var root = mapper.readTree("{\"mimer:schemaVersion\":" + version + "}");
            assertThrows(IllegalArgumentException.class, () -> engine.applyUpToCurrent(root, MimerMigrations.all(), 2));
        }
    }

    @Test
    void regelFelFarInteStamplaStegetSomGenomfort() {
        var root = mapper.readTree("{\"a\":42,\"b\":43}");
        var migration = new Migration("namnbyte", 0, 1, List.of(renameField("a till b", "$.a", "b")));

        assertThrows(IllegalArgumentException.class, () -> engine.applyUpToCurrent(root, List.of(migration), 1));
        assertEquals(mapper.readTree("{\"a\":42,\"b\":43}"), root);
    }

    @Test
    void fleraFormatstegAndrarStrukturOchInbaddadeVarden() {
        var root = mapper.readTree("{\"producerade_resultat\":{\"belopp\":{\"summa\":1000}}}");

        var migrated = MimerMigrations.migrate(root);

        assertEquals(mapper.readTree("{\"producerat_resultat\":[{\"belopp\":{\"varde\":1000}}],"
                + "\"mimer:schemaVersion\":2}"), migrated);
        assertEquals(migrated, MimerMigrations.migrate(migrated.deepCopy()));
    }

    public record Dokument(@JsonProperty("producerat_resultat") List<Resultat> resultat,
                           @JsonProperty("mimer:schemaVersion") int format) {}
    public record Resultat(Belopp belopp) {}
    public record Belopp(int varde) {}

    @Test
    void codecMigrerarInnanHistoriskStrukturBindsTillJava() {
        String historic = "{\"producerade_resultat\":{\"belopp\":{\"summa\":1000}}}";

        // Direkt bindning tappar resultatet eftersom det historiska fältnamnet är okänt.
        assertNull(mapper.readValue(historic, Dokument.class).resultat());

        var document = ModellCodec.instance().deserialize(historic, Dokument.class);

        assertEquals(2, document.format());
        assertEquals(1000, document.resultat().getFirst().belopp().varde());
    }
}
