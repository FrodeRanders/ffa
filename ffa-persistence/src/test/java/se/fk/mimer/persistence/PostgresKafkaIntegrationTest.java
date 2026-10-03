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
    // Testdubbeln simulerar Kafka-commit efter den lokala committen.
    private PostgresKafkaLager store(Leveranslage mode, java.util.function.BiConsumer<String, Dataleverans> publisher) {
        return store(mode, (t, d, commit) -> { commit.run(); publisher.accept(t, d); });
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

    /** Ett förlorat Kafka-kvitto får varken lämna gammal cache eller återanvända en objektversion. */
    @Test
    void striktLageBevararNyVersionOchPausarTillsLeveransenAterhamtats() throws Exception {
        var observed = new ArrayList<Dataleverans>();
        var fail = new AtomicBoolean(false);
        var store = store(Leveranslage.STRIKT, (t, d, commit) -> {
            commit.run();
            observed.add(d);
            if (fail.get()) throw new IllegalStateException("Kafka-commit kan ha lyckats; kvittot saknas");
        });
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var repository = new ForvaltadeYrkanden<>(se.fk.data.modell.v1.Yrkande.class,
                store, keys.getPrivate(), keys.getPublic());
        var initial = new se.fk.data.modell.v1.Yrkande("Version 1");
        initial.setPerson(new se.fk.data.modell.v1.FysiskPerson("19121212-1212"));
        var saved = repository.lagra("process", initial);
        saved.beskrivning = "Version 2";
        fail.set(true);
        var error = assertThrows(Leveransfel.class, () -> repository.lagra("process", saved));
        assertTrue(error.lokaltLagrat());
        assertFalse(error.kafkaKvitterat());
        var pending = store.lasLeverans(error.dataleveransId());
        assertEquals(2, pending.objektVersion());
        assertEquals(Leveranssteg.LOKALT_LAGRAD, store.lasStatus(pending.id()).steg());
        assertThrows(Leveransfel.class, () -> repository.lasProcess("process"));
        assertThrows(Leveransfel.class, () -> repository.lagra("process", saved));
        assertThrows(Leveransfel.class, () -> store.lagra(delivery("process", saved.getId(), 2, 3)));
        assertEquals(2, count());

        fail.set(false);
        var restarted = store(Leveranslage.STRIKT, (t, d) -> observed.add(d));
        assertEquals(1, restarted.skickaVantande(100));
        assertEquals(pending.id(), observed.getLast().id());
        assertArrayEquals(pending.dokument().json(), observed.getLast().dokument().json());
        assertArrayEquals(pending.dokument().signatur(), observed.getLast().dokument().signatur());
        var recovered = repository.lasProcess("process");
        assertEquals(2, recovered.getVersion());
        assertEquals("Version 2", recovered.beskrivning);
        recovered.beskrivning = "Version 3";
        assertEquals(3, repository.lagra("process", recovered).getVersion());
        assertEquals(List.of(1L, 2L, 2L, 3L), observed.stream().map(Dataleverans::objektVersion).toList());
    }

    @Test
    void tvaReplayArbetareFarIntePasseraEnPagåendeLeverans() throws Exception {
        var down = new AtomicBoolean(true);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var observed = new CopyOnWriteArrayList<UUID>();
        var first = delivery("process", "objekt", 0, 1);
        var second = delivery("process", "objekt", 1, 2);
        var store = store(Leveranslage.LOKAL_RESILIENS, (t, d) -> {
            if (down.get()) throw new IllegalStateException("Kafka nere");
            if (d.id().equals(first.id())) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Testtimeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            observed.add(d.id());
        });
        store.lagra(first);
        store.lagra(second);
        down.set(false);
        var other = store(Leveranslage.LOKAL_RESILIENS, (t, d) -> {
            throw new AssertionError("En upptagen process får inte publiceras parallellt");
        });
        try (var workers = Executors.newSingleThreadExecutor()) {
            var active = workers.submit(() -> store.skickaProcess("process", 10));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(0, other.skickaProcess("process", 10));
                assertTrue(observed.isEmpty());
            } finally { release.countDown(); }
            assertEquals(2, active.get(5, TimeUnit.SECONDS));
        }
        assertEquals(List.of(first.id(), second.id()), observed);
        assertEquals(0, store.antalVantande());
    }

    @Test
    void aterstallningArIdempotentOchPublicerarInteEllerSkymmerNyareLokalData() throws Exception {
        var observed = new ArrayList<UUID>();
        var store = store(Leveranslage.STRIKT, (t, d) -> observed.add(d.id()));
        var restored = delivery("process", "objekt", 0, 1);
        store.aterstall(restored);
        store.aterstall(restored);
        assertEquals(1, count());
        assertEquals(0, store.antalVantande());
        assertTrue(observed.isEmpty());
        var collision = new Dataleverans(restored.id(), restored.korrelationsId(), restored.objektId(), 0, 1,
                restored.skapad(), new LagratDokument(new byte[]{123, 125}, restored.dokument().signatur()));
        assertThrows(IllegalStateException.class, () -> store.aterstall(collision));
        var next = delivery("process", "objekt", 1, 2);
        store.lagra(next);
        store.aterstall(delivery("process", "objekt", 0, 1));
        assertEquals(next.id(), store.lasProcess("process").id());
        assertEquals(List.of(next.id()), observed);
    }

    @Test
    void blockeradProcessHindrarInteAndraSkribenter() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var store = store(Leveranslage.STRIKT, (t, d) -> {
            if (d.korrelationsId().equals("blockerad")) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Testtimeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var slow = workers.submit(() -> store.lagra(delivery("blockerad", "o1", 0, 1)));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                workers.submit(() -> store.lagra(delivery("annan", "o2", 0, 1))).get(3, TimeUnit.SECONDS);
                assertNotNull(store.lasProcess("annan"));
            } finally { release.countDown(); }
            slow.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void felandeProcessHindrarInteAndraProcessersReplay() {
        var down = new AtomicBoolean(true);
        var observed = new CopyOnWriteArrayList<UUID>();
        var store = store(Leveranslage.LOKAL_RESILIENS, (t, d) -> {
            if (down.get() || d.korrelationsId().equals("felande")) throw new IllegalStateException("Kafka nere");
            observed.add(d.id());
        });
        store.lagra(delivery("felande", "o1", 0, 1));
        var other = delivery("annan", "o2", 0, 1);
        store.lagra(other);
        down.set(false);
        assertThrows(Leveransfel.class, () -> store.skickaVantande(100));
        assertEquals(List.of(other.id()), observed);
        assertEquals(1, store.antalVantande());
    }

    @Test
    void periodiskArbetareAterforsokerEfterAttKafkaAterhamtatSig() throws Exception {
        var down = new AtomicBoolean(true);
        var sent = new CountDownLatch(1);
        var store = store(Leveranslage.LOKAL_RESILIENS, (t, d) -> {
            if (down.get()) throw new IllegalStateException("Kafka nere");
            sent.countDown();
        });
        store.lagra(delivery("process", "objekt", 0, 1));
        down.set(false);
        try (var worker = new Aterforsoksarbetare(store, Duration.ofMillis(50), 10)) {
            assertTrue(sent.await(5, TimeUnit.SECONDS));
        }
        assertEquals(0, store.antalVantande());
    }

    @Test
    void kafkaFelGerIngenLokalKopiaIStriktLage() throws Exception {
        var store = store(Leveranslage.STRIKT, (t, d, commit) -> { throw new IllegalStateException("Kafka nere före lokal commit"); });
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
        Leveranspublicerare publisher = (t, d, commit) -> {
            commit.run();
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
    void databasfelForhindrarKafkaCommitIStriktLage() throws Exception {
        var observed = new ArrayList<UUID>();
        var store = store(Leveranslage.STRIKT, (t, d) -> observed.add(d.id()));
        var first = delivery("p1", "o1", 0, 1);
        store.lagra(first);
        var collision = new Dataleverans(first.id(), "p2", "o2", 0, 1, Instant.now(), first.dokument());
        var error = assertThrows(Leveransfel.class, () -> store.lagra(collision));
        assertFalse(error.kafkaKvitterat());
        assertFalse(error.lokaltLagrat());
        assertEquals(1, observed.size());
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
        var firstStore = store(Leveranslage.STRIKT, (t, d) -> {});
        var secondStore = store(Leveranslage.STRIKT, (t, d) -> {});
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
                var store = store(Leveranslage.STRIKT, publisher);
                var delivery = delivery("process", "objekt", 0, 1);
                // En riktig Kafka-transaktion skickar data, men avbryts när lokal INSERT
                // misslyckas. read_committed-konsumenten nedan ska inte få den leveransen.
                var aborted = delivery("avbruten", "avbrutet-objekt", 0, 1);
                assertThrows(IllegalStateException.class, () -> publisher.publicera(topic, aborted, () -> {
                    throw new IllegalStateException("Simulerat lokalt commitfel");
                }));
                store.lagra(delivery);
                assertEquals(0, store.antalVantande());
                assertArrayEquals(delivery.dokument().json(), store.lasLeverans(delivery.id()).dokument().json());
                var config = new Properties();
                config.put("bootstrap.servers", broker);
                config.put("group.id", UUID.randomUUID().toString());
                config.put("auto.offset.reset", "earliest");
                config.put("enable.auto.commit", "false");
                config.put("isolation.level", "read_committed");
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

    @Test
    void senaBekraftelserArMonotonaOchMasteAvseSammaData() throws Exception {
        var store = store(Leveranslage.LOKAL_RESILIENS, (t, d) -> { throw new IllegalStateException("Kafka nere"); });
        var delivery = delivery("process", "objekt", 0, 1);
        store.lagra(delivery);
        assertEquals(Leveranssteg.LOKALT_LAGRAD, store.lasStatus(delivery.id()).steg());
        var strict = store(Leveranslage.STRIKT, (t, d) -> fail("Bekräftad leverans ska inte skickas igen"));
        assertThrows(Leveransfel.class, () -> strict.lasProcess("process"));

        strict.bekrafta(delivery, Leveranssteg.GRAFBEHANDLAD);
        var graph = strict.lasStatus(delivery.id());
        assertEquals(Leveranssteg.GRAFBEHANDLAD, graph.steg());
        assertNotNull(graph.kafkaPublicerad());
        assertNotNull(graph.radataLagrade());
        assertNull(graph.senasteFel());
        assertEquals(delivery.id(), strict.lasProcess("process").id());
        strict.bekrafta(delivery, Leveranssteg.RADATA_LAGRADE);
        strict.bekrafta(delivery, Leveranssteg.LOKALT_LAGRAD);
        assertEquals(graph, strict.lasStatus(delivery.id()));
        assertEquals(0, strict.skickaVantande(100));

        var conflict = new Dataleverans(delivery.id(), "annan-process", delivery.objektId(), 0, 1,
                delivery.skapad(), delivery.dokument());
        assertThrows(IllegalArgumentException.class, () -> strict.bekrafta(conflict, Leveranssteg.GRAFBEHANDLAD));
        assertEquals(graph, strict.lasStatus(delivery.id()));
    }

    @Test
    void backendAvstamningLoserOsakertUtfallUtanNyPublicering() {
        var store = store(Leveranslage.STRIKT, (t, d, commit) -> {
            commit.run();
            throw new IllegalStateException("Kafka-kvittot saknas");
        });
        var delivery = delivery("process", "objekt", 0, 1);
        assertThrows(Leveransfel.class, () -> store.lagra(delivery));
        // Återställning får bara anropas efter verifiering av dokumentet och backendkontraktet.
        store.aterstall(delivery);
        assertEquals(Leveranssteg.RADATA_LAGRADE, store.lasStatus(delivery.id()).steg());
        assertEquals(0, store.antalVantande());
        assertEquals(delivery.id(), store.lasProcess("process").id());
    }
}
