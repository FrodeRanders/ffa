package se.fk.mimer.graph;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Utför projektionen i Neo4j; strängjämförelser kan inte bevisa dess transaktionsbeteende. */
@EnabledIfSystemProperty(named = "ffa.integration", matches = "true")
class GrafprojektionIntegrationTest {
    private final String id = "ffa-test-" + UUID.randomUUID();
    private Driver driver;

    @BeforeEach
    void connect() {
        driver = GraphDatabase.driver(env("FFA_NEO4J", "bolt://localhost:17687"),
                AuthTokens.basic(env("FFA_NEO4J_USER", "neo4j"), env("FFA_NEO4J_PASSWORD", "ffa-demo-password")));
        driver.verifyConnectivity();
        try (var session = driver.session()) { session.run(Grafprojektion.schema()).consume(); }
    }

    @AfterEach
    void cleanup() {
        if (driver != null) {
            try (var session = driver.session()) {
                session.run("MATCH (n:FfaObjekt) WHERE n.id IN $ids DETACH DELETE n",
                        Map.of("ids", List.of(id, id + "-beslut"))).consume();
            } finally { driver.close(); }
        }
    }

    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }

    private String projection(int version, boolean beslut) {
        String child = beslut ? ", \"beslut\":{\"@type\":\"se.fk.data.modell.v1.Beslut\",\"id\":\""
                + id + "-beslut\",\"version\":" + version + ",\"utfall\":\"BEVILJAT\"}" : "";
        String json = "{\"@type\":\"se.fk.data.modell.v1.Yrkande\",\"id\":\"" + id
                + "\",\"version\":" + version + ",\"beskrivning\":\"Version " + version + "\"" + child + "}";
        return new Grafprojektion().projektera(json.getBytes(StandardCharsets.UTF_8));
    }

    private ResultSummaryMarker apply(String query) {
        try (var session = driver.session()) {
            var summary = session.executeWrite(tx -> tx.run(query).consume());
            return new ResultSummaryMarker(summary.counters().relationshipsCreated(), summary.counters().relationshipsDeleted());
        }
    }

    private record ResultSummaryMarker(int created, int deleted) {}

    private void assertState(int version, int relations) {
        try (var session = driver.session()) {
            var row = session.run("MATCH (n:FfaObjekt {id:$id}) OPTIONAL MATCH (n)-[r]->() "
                    + "RETURN n.version AS version, n.beskrivning AS text, count(r) AS relations", Map.of("id", id)).single();
            assertEquals(version, row.get("version").asInt());
            assertEquals("Version " + version, row.get("text").asString());
            assertEquals(relations, row.get("relations").asInt());
        }
    }

    @Test
    void dubletterOchAldreTillstandAndrarInteGrafenOchBorttagnaRelationerAterkommerInte() {
        apply(projection(1, true));
        apply(projection(2, true));
        var duplicate = apply(projection(2, true));
        assertEquals(new ResultSummaryMarker(0, 0), duplicate);
        apply(projection(1, true));
        assertState(2, 1);
        apply(projection(3, false));
        assertState(3, 0);
        apply(projection(2, true));
        assertState(3, 0);
    }

    @Test
    void samtidigaProjektionerBehallerDenNyasteVersionen() throws Exception {
        try (var workers = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<?>>();
            for (int version : List.of(2, 4, 1, 3)) {
                String query = projection(version, version < 4);
                futures.add(workers.submit(() -> { start.await(); apply(query); return null; }));
            }
            start.countDown();
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        }
        assertState(4, 0);
    }
}
