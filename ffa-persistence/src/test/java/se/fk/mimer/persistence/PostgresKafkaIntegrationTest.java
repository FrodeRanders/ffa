package se.fk.mimer.persistence;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import se.fk.mimer.runtime.*;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Körs uttryckligen mot Docker-miljön: mvn -Dffa.integration=true test. */
@EnabledIfSystemProperty(named = "ffa.integration", matches = "true")
class PostgresKafkaIntegrationTest {
    private final String topic = "ffa.test." + UUID.randomUUID();
    private final String url = System.getenv().getOrDefault("FFA_JDBC", "jdbc:postgresql://localhost:15432/ffa");
    private final String broker = System.getenv().getOrDefault("FFA_KAFKA", "localhost:19092");
    private final String user = System.getenv().getOrDefault("FFA_DB_USER", "ffa");
    private final String password = System.getenv().getOrDefault("FFA_DB_PASSWORD", "ffa-demo");

    private Connection connect() throws SQLException { return DriverManager.getConnection(url, user, password); }
    private PostgresKafkaLager store(Leveranslage mode, Leveranspublicerare publisher) {
        var store = new PostgresKafkaLager(url, user, password, topic, mode, publisher);
        store.initiera();
        return store;
    }
    private Dataleverans delivery(String process, String object, long previous, long version) {
        return new Dataleverans(Dataleverans.nyttId(), process, object, previous, version, Instant.now(),
                new LagratDokument("{ \"bevarat\": 42 }".getBytes(StandardCharsets.UTF_8), new byte[]{1, 2, 3}));
    }
    private long count() throws SQLException {
        try (var connection = connect(); var query = connection.prepareStatement("SELECT count(*) FROM ffa_dataleverans WHERE topic = ?")) {
            query.setString(1, topic);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }
    @AfterEach
    void cleanup() throws SQLException {
        try (var connection = connect(); var query = connection.prepareStatement("DELETE FROM ffa_dataleverans WHERE topic = ?")) {
            query.setString(1, topic); query.executeUpdate();
        }
    }

    @Test
    void kafkaFelGerIngenLokalKopiaIStriktLage() throws Exception {
        var store = store(Leveranslage.KAFKA_FORST, (t, d) -> { throw new IllegalStateException("Kafka nere"); });
        var delivery = delivery("process", "objekt", 0, 1);
        var error = assertThrows(Leveransfel.class, () -> store.lagra(delivery));
        assertFalse(error.kafkaKvitterat());
        assertEquals(delivery.id(), error.dataleveransId());
        assertEquals(0, count());
    }

    @Test
    void lokalResiliensBevararHistorikOchReplayOrdningMedSammaId() throws Exception {
        var down = new AtomicBoolean(true);
        var observed = new ArrayList<Dataleverans>();
        Leveranspublicerare publisher = (t, d) -> {
            if (down.get()) throw new IllegalStateException("Kafka nere");
            observed.add(d);
        };
        var store = store(Leveranslage.LOKAL_RESILIENS, publisher);
        var first = delivery("process", "objekt", 0, 1);
        var second = delivery("process", "objekt", 1, 2);
        store.lagra(first);
        store.lagra(second);
        assertEquals(2, count());
        assertEquals(2, store.antalVantande());
        assertEquals(second.id(), store.lasProcess("process").id());
        assertArrayEquals(first.dokument().json(), store.lasLeverans(first.id()).dokument().json());

        // Ny adapter simulerar omstart; väntande leveranser hämtas från databasen.
        down.set(false);
        var restarted = store(Leveranslage.LOKAL_RESILIENS, publisher);
        assertEquals(2, restarted.skickaVantande(100));
        assertEquals(List.of(first.id(), second.id()), observed.stream().map(Dataleverans::id).toList());
        assertArrayEquals(first.dokument().signatur(), observed.getFirst().dokument().signatur());
        assertEquals(0, restarted.antalVantande());
        assertEquals(0, restarted.skickaVantande(100));
    }

    @Test
    void databasfelEfterKafkaKvittoRapporterasSomDelvisGenomfordLeverans() throws Exception {
        var observed = new ArrayList<UUID>();
        var store = store(Leveranslage.KAFKA_FORST, (t, d) -> observed.add(d.id()));
        var first = delivery("p1", "o1", 0, 1);
        store.lagra(first);
        var collision = new Dataleverans(first.id(), "p2", "o2", 0, 1, Instant.now(), first.dokument());
        var error = assertThrows(Leveransfel.class, () -> store.lagra(collision));
        assertTrue(error.kafkaKvitterat());
        assertEquals(2, observed.size());
        assertEquals(1, count());
        assertNull(store.lasProcess("p2"));
    }

    @Test
    void delvisLyckadBatchBevararTidigareKvitton() {
        var blocked = new HashSet<UUID>();
        var sent = new ArrayList<UUID>();
        var first = delivery("process", "objekt", 0, 1);
        var second = delivery("process", "objekt", 1, 2);
        blocked.add(first.id());
        blocked.add(second.id());
        var store = store(Leveranslage.LOKAL_RESILIENS, (t, d) -> {
            if (blocked.contains(d.id())) throw new IllegalStateException("Tillfälligt leveransfel");
            sent.add(d.id());
        });
        store.lagra(first);
        store.lagra(second);

        blocked.remove(first.id());
        assertThrows(Leveransfel.class, () -> store.skickaVantande(100));
        assertEquals(1, store.antalVantande());
        assertEquals(List.of(first.id()), sent);

        blocked.clear();
        assertEquals(1, store.skickaVantande(100));
        assertEquals(List.of(first.id(), second.id()), sent);
    }

    @Test
    void databasfelIResilientLagePublicerarIngenting() throws Exception {
        var observed = new ArrayList<UUID>();
        var store = store(Leveranslage.LOKAL_RESILIENS, (t, d) -> observed.add(d.id()));
        var first = delivery("p1", "o1", 0, 1);
        store.lagra(first);
        var collision = new Dataleverans(first.id(), "p2", "o2", 0, 1, Instant.now(), first.dokument());
        assertThrows(Leveransfel.class, () -> store.lagra(collision));
        assertEquals(List.of(first.id()), observed);
        assertEquals(1, count());
    }

    @Test
    void tvaSkribenterKanInteSkrivaSammaGamlaVersion() throws Exception {
        var firstStore = store(Leveranslage.KAFKA_FORST, (t, d) -> {});
        var secondStore = store(Leveranslage.KAFKA_FORST, (t, d) -> {});
        firstStore.lagra(delivery("process", "objekt", 0, 1));
        try (var workers = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> first = () -> { start.await(); try { firstStore.lagra(delivery("process", "objekt", 1, 2)); return true; } catch (Leveransfel e) { return false; } };
            Callable<Boolean> second = () -> { start.await(); try { secondStore.lagra(delivery("process", "objekt", 1, 2)); return true; } catch (Leveransfel e) { return false; } };
            var a = workers.submit(first); var b = workers.submit(second); start.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(2, count());
    }

    @Test
    void verkligKafkaOchPostgresFarIdentiskaBytesOchMetadata() throws Exception {
        try (var admin = Admin.create(Map.of("bootstrap.servers", broker))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(15, TimeUnit.SECONDS);
            try (var publisher = new KafkaPublicerare(broker)) {
                var store = store(Leveranslage.KAFKA_FORST, publisher);
                var delivery = delivery("process", "objekt", 0, 1);
                store.lagra(delivery);
                assertEquals(0, store.antalVantande());
                assertArrayEquals(delivery.dokument().json(), store.lasLeverans(delivery.id()).dokument().json());
                var config = new Properties();
                config.put("bootstrap.servers", broker);
                config.put("group.id", UUID.randomUUID().toString());
                config.put("auto.offset.reset", "earliest");
                config.put("enable.auto.commit", "false");
                try (var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer())) {
                    consumer.subscribe(List.of(topic));
                    ConsumerRecord<String, byte[]> message = null;
                    var deadline = Instant.now().plusSeconds(15);
                    while (message == null && Instant.now().isBefore(deadline)) {
                        var records = consumer.poll(Duration.ofMillis(500));
                        if (!records.isEmpty()) message = records.iterator().next();
                    }
                    assertNotNull(message);
                    assertEquals("process", message.key());
                    assertArrayEquals(delivery.dokument().json(), message.value());
                    assertArrayEquals(delivery.dokument().signatur(), message.headers().lastHeader("signatur").value());
                    assertEquals(delivery.id().toString(), new String(message.headers().lastHeader("dataleverans-id").value(), StandardCharsets.UTF_8));
                }
            } finally { admin.deleteTopics(List.of(topic)).all().get(15, TimeUnit.SECONDS); }
        }
    }
}
