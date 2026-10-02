package se.fk.mimer.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

/** Verifierar vilka objekt, relationer och egenskaper som lämnar dokumentet för grafen. */
class GrafprojektionTest {
    private static final String INPUT = """
        {"@type":"se.fk.data.modell.v1.Yrkande", "id":"y", "version":2,
         "beskrivning":"Hundutställning", "ras":"Collie",
         "person":{"varde":{"personnummer":{"varde":"19121212-1212"}}},
         "beslut":{"@type":"se.fk.data.modell.v1.Beslut", "id":"b", "version":1, "utfall":"BEVILJAT"},
         "producerat_resultat":[{"@type":"se.fk.data.modell.v1.Ersattning", "id":"e", "version":2,
           "typ":"HUNDBIDRAG", "belopp":{"varde":1200.0, "valuta":"iso4217:SEK"}}]}
        """;

    @Test
    void valdaObjektBlirNoderMedRelationerOchPlattaEgenskaper() {
        String cypher = new Grafprojektion().projektera(INPUT.getBytes(StandardCharsets.UTF_8));
        assertEquals(3, cypher.lines().filter(line -> line.startsWith("MERGE (n") && !line.contains("->")).count());
        assertEquals(2, cypher.lines().filter(line -> line.startsWith("MERGE (n") && line.contains("->")).count());
        assertTrue(cypher.contains("SET n0:`Yrkande`"));
        assertTrue(cypher.contains("[:`PRODUCERAT_RESULTAT` {"));
        assertTrue(cypher.contains("[:`BESLUT` {"));
        assertTrue(cypher.contains("`belopp_varde`: 1200.0"));
        assertFalse(cypher.contains("19121212-1212"));
        assertFalse(cypher.contains("Collie"));
        assertEquals(cypher, new Grafprojektion().projektera(INPUT.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void okandaObjekttyperOchDubblaIdentiteterAvvisas() {
        assertThrows(IllegalArgumentException.class, () -> new Grafprojektion().projektera(
                INPUT.replace("se.fk.data.modell.v1.Ersattning", "okand.Modell").getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> new Grafprojektion().projektera(
                INPUT.replace("\"id\":\"e\"", "\"id\":\"y\"").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void textMedCitatteckenOchRadbrytningEscapas() {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var root = (tools.jackson.databind.node.ObjectNode) json.readTree(INPUT);
        root.put("beskrivning", "En \"hund\"\nRad två");
        String cypher = new Grafprojektion().projektera(json.writeValueAsBytes(root));
        assertTrue(cypher.contains("\\\"hund\\\""));
        assertTrue(cypher.contains("\\nRad två"));
    }
}
