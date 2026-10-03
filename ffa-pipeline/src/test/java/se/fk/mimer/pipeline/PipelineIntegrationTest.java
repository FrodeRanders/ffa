package se.fk.mimer.pipeline;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.neo4j.driver.*;
import se.fk.data.modell.v1.*;
import se.fk.data.modell.utils.SignatureUtils;
import se.fk.mimer.persistence.*;
import se.fk.mimer.runtime.*;

import java.security.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Verklig N−2 → Kafka → PostgreSQL/RustFS → Kafka → Neo4j, med isolerade topics och bucket. */
@EnabledIfSystemProperty(named = "ffa.pipeline", matches = "true")
class PipelineIntegrationTest {
    private static KeyPair keys;
    private final String topic = "ffa.pipeline." + UUID.randomUUID();
    private final String graphTopic = topic + ".graph";
    private final String group = topic + ".backend";
    private final String broker = env("FFA_KAFKA", "localhost:19092");
    private final String jdbc = env("FFA_JDBC", "jdbc:postgresql://localhost:15432/ffa");
    private final String user = env("FFA_DB_USER", "ffa");
    private final String password = env("FFA_DB_PASSWORD", "ffa-demo");
    private final Set<UUID> deliveries = new HashSet<>();
    private final Set<String> objectIds = new HashSet<>();
    private Admin admin;
    private Driver driver;
    private Objektlager objects;
    private Radatalager backend;
    private BackendRestServer rest;
    private RestDokumentkalla remote;
    private Kvittenskonsument receipts;
    private PostgresKafkaLager cache;
    private KafkaPublicerare publisher;
    private ForvaltadeYrkanden<Yrkande> repository;

    private static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @BeforeEach
    void start() throws Exception {
        admin = Admin.create(Map.of("bootstrap.servers", broker));
        admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1), new NewTopic(graphTopic, 1, (short) 1), new NewTopic(topic + ".kvitton", 1, (short) 1)))
                .all().get(20, TimeUnit.SECONDS);
        driver = GraphDatabase.driver(env("FFA_NEO4J", "bolt://localhost:17687"),
                AuthTokens.basic(env("FFA_NEO4J_USER", "neo4j"), env("FFA_NEO4J_PASSWORD", "ffa-demo-password")));
        driver.verifyConnectivity();
        objects = new Objektlager(env("FFA_S3", "http://localhost:19000"), env("FFA_S3_ACCESS_KEY", "ffa-demo"),
                env("FFA_S3_SECRET_KEY", "ffa-demo-object-store"), "ffa-test-" + UUID.randomUUID());
        backend = new Radatalager(jdbc, user, password, topic, objects, keys.getPublic());
        backend.initiera();
        publisher = new KafkaPublicerare(broker);
        cache = new PostgresKafkaLager(jdbc, user, password, topic, Leveranslage.STRIKT, publisher);
        cache.initiera();
        rest = new BackendRestServer(new java.net.InetSocketAddress("127.0.0.1", 0), backend);
        remote = new RestDokumentkalla(rest.uri());
        receipts = new Kvittenskonsument(broker, topic + ".kvitton", topic + ".cache", cache);
        repository = new ForvaltadeYrkanden<>(Yrkande.class, cache, remote, keys.getPrivate(), keys.getPublic());
    }

    @AfterEach
    void cleanup() throws Exception {
        if (receipts != null) receipts.close();
        if (remote != null) remote.close();
        if (rest != null) rest.close();
        if (publisher != null) publisher.close();
        if (objects != null) {
            try {
                for (var id : deliveries) objects.radera(id);
                objects.raderaTomBucket();
            } finally { objects.close(); }
        }
        if (driver != null) {
            try (var session = driver.session()) {
                session.run("MATCH (n:FfaObjekt) WHERE n.id IN $ids DETACH DELETE n", Map.of("ids", objectIds)).consume();
            } finally { driver.close(); }
        }
        try (var connection = connect()) {
            for (var table : List.of("ffa_backend.dataleverans", "ffa_dataleverans", "ffa_kvittens")) {
                try (var delete = connection.prepareStatement("DELETE FROM " + table + " WHERE topic = ?")) {
                    delete.setString(1, topic);
                    delete.executeUpdate();
                }
            }
        }
        if (admin != null) {
            try { admin.deleteTopics(List.of(topic, graphTopic, topic + ".kvitton")).all().get(20, TimeUnit.SECONDS); }
            finally { admin.close(); }
        }
    }

    private Connection connect() throws SQLException { return DriverManager.getConnection(jdbc, user, password); }

    private Yrkande initial() {
        var claim = new Yrkande("Version 1");
        claim.setPerson(new FysiskPerson("19121212-1212"));
        objectIds.add(claim.getId());
        return claim;
    }

    private Dataleverans delivery() {
        var d = cache.lasProcess("process");
        deliveries.add(d.id());
        return d;
    }

    private Dataleverans await(Supplier<Dataleverans> poll) {
        var deadline = Instant.now().plusSeconds(25);
        while (Instant.now().isBefore(deadline)) {
            var d = poll.get();
            if (d != null) return d;
        }
        throw new AssertionError("Ingen leverans inom 25 sekunder");
    }

    private void bekrafta(Dataleverans d, Leveranssteg steg) {
        var deadline = Instant.now().plusSeconds(25);
        while (Instant.now().isBefore(deadline)) {
            var receipt = receipts.behandla(Duration.ofMillis(500));
            if (receipt != null && receipt.dataleveransId().equals(d.id()) && receipt.steg() == steg) return;
        }
        fail("Kvittensen uteblev: " + d.id());
    }

    private long backendCount() throws SQLException {
        try (var connection = connect(); var query = connection.prepareStatement(
                "SELECT count(*) FROM ffa_backend.dataleverans WHERE topic = ?")) {
            query.setString(1, topic);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private void graphVersion(String id, int version) {
        try (var session = driver.session()) {
            var rows = session.run("MATCH (n:FfaObjekt {id:$id}) RETURN n.version AS version, n.beskrivning AS text", Map.of("id", id));
            if (version == 0) assertFalse(rows.hasNext());
            else {
                var row = rows.single();
                assertEquals(version, row.get("version").asInt());
                assertEquals("Version " + version, row.get("text").asString());
            }
        }
    }

    @Test
    void helaKedjanBevararJsonHanterarDubletterOchAldreVersionerOchAterstallerCache() throws Exception {
        try (var raw = new Radatasteg(broker, topic, graphTopic, group, backend, keys.getPublic());
             var graph = new Grafsteg(broker, graphTopic, topic + ".graph-reader", driver, keys.getPublic())) {
            var claim = repository.lagra("process", initial());
            var first = delivery();
            assertEquals(Leveranssteg.KAFKA_PUBLICERAD, cache.lasStatus(first.id()).steg());
            assertNull(backend.lasProcess("process"));
            assertEquals(first.id(), await(() -> raw.behandla(Duration.ofMillis(500))).id());
            var stored = backend.lasLeverans(first.id());
            assertTrue(Leveransformat.samma(first, stored));
            assertArrayEquals(first.dokument().json(), objects.las(first.id()).dokument().json());
            bekrafta(stored, Leveranssteg.RADATA_LAGRADE);
            assertEquals(Leveranssteg.RADATA_LAGRADE, cache.lasStatus(first.id()).steg());
            graphVersion(claim.getId(), 0);
            bekrafta(await(() -> graph.behandla(Duration.ofMillis(500))), Leveranssteg.GRAFBEHANDLAD);
            graphVersion(claim.getId(), 1);

            claim.beskrivning = "Version 2";
            claim = repository.lagra("process", claim);
            var second = delivery();
            claim.beskrivning = "Version 3";
            claim = repository.lagra("process", claim);
            var third = delivery();
            // En gammal originalleverans kommer efter version 3, med samma id och samma data.
            publisher.publicera(topic, first);
            for (var expected : List.of(second, third, first)) {
                var d = await(() -> raw.behandla(Duration.ofMillis(500)));
                assertEquals(expected.id(), d.id());
                bekrafta(d, Leveranssteg.RADATA_LAGRADE);
            }
            assertEquals(3, backendCount());
            assertEquals(third.id(), backend.lasProcess("process").id());
            for (var expected : List.of(second, third, first)) {
                var d = await(() -> graph.behandla(Duration.ofMillis(500)));
                assertEquals(expected.id(), d.id());
                bekrafta(d, Leveranssteg.GRAFBEHANDLAD);
            }
            graphVersion(claim.getId(), 3);

            // Ett nytt anrop med oförändrat verksamhetsinnehåll får nytt leverans-id men samma version.
            claim = repository.lagra("process", claim);
            var duplicateState = delivery();
            assertNotEquals(third.id(), duplicateState.id());
            assertEquals(3, duplicateState.objektVersion());
            bekrafta(await(() -> raw.behandla(Duration.ofMillis(500))), Leveranssteg.RADATA_LAGRADE);
            bekrafta(await(() -> graph.behandla(Duration.ofMillis(500))), Leveranssteg.GRAFBEHANDLAD);
            graphVersion(claim.getId(), 3);
            assertEquals(4, backendCount());

            // Simulerad förlust av enbart den lokala, redan publicerade cachen.
            try (var connection = connect(); var delete = connection.prepareStatement(
                    "DELETE FROM ffa_dataleverans WHERE topic = ?")) {
                delete.setString(1, topic);
                delete.executeUpdate();
            }
            var recovered = repository.lasProcess("process");
            assertEquals(3, recovered.getVersion());
            assertEquals("Version 3", recovered.beskrivning);
            assertEquals(duplicateState.id(), cache.lasProcess("process").id());
            assertEquals(Leveranssteg.GRAFBEHANDLAD, cache.lasStatus(duplicateState.id()).steg());
            assertNull(raw.behandla(Duration.ofSeconds(1))); // Återställning skapar ingen leverans.
        }
    }

    @Test
    void avbrottEfterRadataCommitAterlevererarUtanOffsetforlustEllerNyObjektnyckel() throws Exception {
        repository.lagra("process", initial());
        var delivery = delivery();
        var failure = new AtomicBoolean(true);
        try (var raw = new Radatasteg(broker, topic, graphTopic, group, backend, keys.getPublic(), () -> {
                 if (failure.getAndSet(false)) throw new IllegalStateException("Avbrott efter rådatacommit");
             });
             var graph = new Grafsteg(broker, graphTopic, topic + ".graph-reader", driver, keys.getPublic())) {
            assertThrows(IllegalStateException.class, () -> await(() -> raw.behandla(Duration.ofMillis(500))));
            assertTrue(Leveransformat.samma(delivery, backend.lasLeverans(delivery.id())));
            var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertFalse(offsets.containsKey(new TopicPartition(topic, 0)));
            assertNull(graph.behandla(Duration.ofSeconds(1)));
            assertNull(receipts.behandla(Duration.ofSeconds(1))); // Avbruten Kafka-transaktion ger inget synligt kvitto.
            assertEquals(delivery.id(), await(() -> raw.behandla(Duration.ofMillis(500))).id());
            assertEquals(1, backendCount());
            assertEquals(delivery.id(), await(() -> graph.behandla(Duration.ofMillis(500))).id());
            graphVersion(delivery.objektId(), 1);
            offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertEquals(1, offsets.get(new TopicPartition(topic, 0)).offset());
        }
    }

    @Test
    void restSokerMetadataOchOriginalJsonOchSkiljerSaknatObjektFranCachemiss() throws Exception {
        String process = "åäö +/process";
        repository.lagra(process, initial());
        var d = cache.lasProcess(process);
        deliveries.add(d.id());
        backend.lagra(d);
        assertTrue(Leveransformat.samma(d, remote.lasProcess(process)));
        assertTrue(Leveransformat.samma(d, remote.lasObjekt(d.objektId())));
        assertTrue(Leveransformat.samma(d, remote.lasLeverans(d.id())));
        assertNull(remote.lasLeverans(UUID.randomUUID()));
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            var uri = rest.uri().resolve("v1/leveranser/" + d.id() + "/metadata");
            var response = http.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(d.id().toString(), tools.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(response.body()).path("dataleveransId").asString());
            objects.radera(d.id());
            assertThrows(IllegalStateException.class, () -> remote.lasLeverans(d.id()));
            assertEquals(200, http.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405, http.send(java.net.http.HttpRequest.newBuilder(uri)
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    @Test
    void kvittensForeCacheaterstallningBevarasOchFelaktigtFingeravtryckAvvisas() throws Exception {
        repository.lagra("process", initial());
        var d = delivery();
        backend.lagra(d);
        try (var connection = connect(); var delete = connection.prepareStatement("DELETE FROM ffa_dataleverans WHERE topic = ?")) {
            delete.setString(1, topic);
            delete.executeUpdate();
        }
        var graphReceipt = Kvittens.skapa(topic, d, Leveranssteg.GRAFBEHANDLAD);
        cache.bekrafta(graphReceipt);
        repository.lasProcess("process"); // Verifierad återhämtning via REST stämmer av inkorgen.
        assertEquals(Leveranssteg.GRAFBEHANDLAD, cache.lasStatus(d.id()).steg());
        cache.bekrafta(Kvittens.skapa(topic, d, Leveranssteg.RADATA_LAGRADE));
        cache.bekrafta(graphReceipt); // Dublett och omvänd ordning får inte sänka milstolpen.
        assertEquals(Leveranssteg.GRAFBEHANDLAD, cache.lasStatus(d.id()).steg());
        var wrong = new Kvittens(topic, d.id(), d.korrelationsId(), Leveranssteg.GRAFBEHANDLAD,
                Instant.now(), "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> cache.bekrafta(wrong));
    }

    @Test
    void grafavbrottEfterCommitAterbehandlarUtanSynligtKvittensEllerOffset() throws Exception {
        repository.lagra("process", initial());
        var d = delivery();
        var failure = new AtomicBoolean(true);
        String graphGroup = topic + ".graph-reader";
        try (var raw = new Radatasteg(broker, topic, graphTopic, group, backend, keys.getPublic());
             var graph = new Grafsteg(broker, graphTopic, topic, graphGroup, driver, keys.getPublic(), () -> {
                 if (failure.getAndSet(false)) throw new IllegalStateException("Avbrott efter Neo4j-commit");
             })) {
            bekrafta(await(() -> raw.behandla(Duration.ofMillis(500))), Leveranssteg.RADATA_LAGRADE);
            assertThrows(IllegalStateException.class, () -> await(() -> graph.behandla(Duration.ofMillis(500))));
            graphVersion(d.objektId(), 1);
            assertNull(receipts.behandla(Duration.ofSeconds(1)));
            var offsets = admin.listConsumerGroupOffsets(graphGroup).partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertFalse(offsets.containsKey(new TopicPartition(graphTopic, 0)));
            bekrafta(await(() -> graph.behandla(Duration.ofMillis(500))), Leveranssteg.GRAFBEHANDLAD);
            assertEquals(Leveranssteg.GRAFBEHANDLAD, cache.lasStatus(d.id()).steg());
            graphVersion(d.objektId(), 1);
        }
    }

    @Test
    void kvittensensSqlfelCommitterarInteOffsetOchOmstartAterlevererar() throws Exception {
        repository.lagra("process", initial());
        var d = delivery();
        String constraint = "ffa_receipt_" + UUID.randomUUID().toString().replace("-", "");
        try (var raw = new Radatasteg(broker, topic, graphTopic, group, backend, keys.getPublic())) {
            await(() -> raw.behandla(Duration.ofMillis(500)));
        }
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ffa_kvittens ADD CONSTRAINT " + constraint
                    + " CHECK (topic <> '" + topic + "') NOT VALID");
        }
        try {
            assertThrows(IllegalStateException.class, () -> bekrafta(d, Leveranssteg.RADATA_LAGRADE));
            assertEquals(Leveranssteg.KAFKA_PUBLICERAD, cache.lasStatus(d.id()).steg());
            var offsets = admin.listConsumerGroupOffsets(topic + ".cache").partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
            assertFalse(offsets.containsKey(new TopicPartition(topic + ".kvitton", 0)));
        } finally {
            try (var connection = connect(); var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE ffa_kvittens DROP CONSTRAINT " + constraint);
            }
        }
        receipts.close();
        receipts = new Kvittenskonsument(broker, topic + ".kvitton", topic + ".cache", cache);
        bekrafta(d, Leveranssteg.RADATA_LAGRADE);
        assertEquals(Leveranssteg.RADATA_LAGRADE, cache.lasStatus(d.id()).steg());
    }

    @Test
    void sammaLeveransIdMedAnnatSigneratInnehallAvvisasOchOriginaletBevaras() {
        var saved = repository.lagra("process", initial());
        var original = delivery();
        backend.lagra(original);
        saved.beskrivning = "Annat innehåll";
        repository.lagra("process", saved);
        var changed = delivery();
        var conflict = new Dataleverans(original.id(), original.korrelationsId(), original.objektId(),
                changed.forvantadVersion(), changed.objektVersion(), original.skapad(), changed.dokument());
        assertTrue(SignatureUtils.verify(conflict.dokument().json(), conflict.dokument().signatur(), keys.getPublic()));
        assertThrows(IllegalArgumentException.class, () -> backend.lagra(conflict));
        assertTrue(Leveransformat.samma(original, backend.lasLeverans(original.id())));
    }

    @Test
    void indexfelEfterObjektskrivningGerIngenVidareleveransOchKanAterhamtas() throws Exception {
        repository.lagra("process", initial());
        var delivery = delivery();
        String constraint = "ffa_pipeline_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = connect(); var statement = connection.createStatement()) {
            // Villkoret gäller enbart detta tests slumpade topic; andra körningar berörs inte.
            statement.execute("ALTER TABLE ffa_backend.dataleverans ADD CONSTRAINT " + constraint
                    + " CHECK (topic <> '" + topic + "') NOT VALID");
        }
        try (var raw = new Radatasteg(broker, topic, graphTopic, group, backend, keys.getPublic());
             var graph = new Grafsteg(broker, graphTopic, topic + ".graph-reader", driver, keys.getPublic())) {
            try {
                assertThrows(IllegalStateException.class, () -> await(() -> raw.behandla(Duration.ofMillis(500))));
                assertTrue(Leveransformat.samma(delivery, objects.las(delivery.id())));
                assertNull(backend.lasLeverans(delivery.id()));
                assertNull(graph.behandla(Duration.ofSeconds(1)));
                assertEquals(1, repository.lasProcess("process").getVersion()); // Lokal återläsning fungerar.
            } finally {
                try (var connection = connect(); var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE ffa_backend.dataleverans DROP CONSTRAINT " + constraint);
                }
            }
            assertEquals(delivery.id(), await(() -> raw.behandla(Duration.ofMillis(500))).id());
            assertEquals(1, backendCount());
            assertEquals(delivery.id(), await(() -> graph.behandla(Duration.ofMillis(500))).id());
            graphVersion(delivery.objektId(), 1);
        }
    }
}
