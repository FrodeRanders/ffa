package se.fk.mimer.pipeline;

import org.apache.kafka.clients.admin.*;
import org.neo4j.driver.*;
import se.fk.hundbidrag.Applikation;
import se.fk.hundbidrag.modell.YrkandeOmHundbidrag;
import se.fk.mimer.persistence.*;
import se.fk.mimer.runtime.*;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Kör ett isolerat förmånsflöde och lämnar de verkliga lagringarna kvar för inspektion. */
public final class Pipelinedemo {
    private Pipelinedemo() {}

    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }

    public static void main(String[] args) throws Exception {
        String run = UUID.randomUUID().toString();
        String topic = "ffa.demo.pipeline." + run;
        String graphTopic = topic + ".graph";
        String process = "hundbidrag-" + run;
        String broker = env("FFA_KAFKA", "localhost:19092");
        String bucket = "ffa-pipeline-demo";
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();

        try (var admin = Admin.create(Map.of("bootstrap.servers", broker))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1), new NewTopic(graphTopic, 1, (short) 1), new NewTopic(topic + ".kvitton", 1, (short) 1)))
                    .all().get(20, TimeUnit.SECONDS);
        }
        try (var objects = new Objektlager(env("FFA_S3", "http://localhost:19000"),
                     env("FFA_S3_ACCESS_KEY", "ffa-demo"), env("FFA_S3_SECRET_KEY", "ffa-demo-object-store"), bucket);
             var publisher = new KafkaPublicerare(broker);
             var driver = GraphDatabase.driver(env("FFA_NEO4J", "bolt://localhost:17687"),
                     AuthTokens.basic(env("FFA_NEO4J_USER", "neo4j"), env("FFA_NEO4J_PASSWORD", "ffa-demo-password")))) {
            String url = env("FFA_JDBC", "jdbc:postgresql://localhost:15432/ffa");
            String user = env("FFA_DB_USER", "ffa");
            String password = env("FFA_DB_PASSWORD", "ffa-demo");
            var cache = new PostgresKafkaLager(url, user, password, topic, Leveranslage.STRIKT, publisher);
            cache.initiera();
            var backend = new Radatalager(url, user, password, topic, objects, keys.getPublic());
            backend.initiera();
            try (var rest = new BackendRestServer(new java.net.InetSocketAddress("127.0.0.1",
                         Integer.parseInt(env("FFA_REST_PORT", "18080"))), backend);
                 var remote = new RestDokumentkalla(rest.uri());
                 var receipts = new Kvittenskonsument(broker, topic + ".kvitton", topic + ".cache", cache)) {
                var claims = new ForvaltadeYrkanden<>(YrkandeOmHundbidrag.class, cache, remote,
                        keys.getPrivate(), keys.getPublic());
                var claim = new Applikation(claims).handlagg(process);
                var target = cache.lasProcess(process);
                rapportera(cache, target);

                try (var raw = new Radatasteg(broker, topic, graphTopic, topic + ".backend", backend, keys.getPublic());
                     var graph = new Grafsteg(broker, graphTopic, topic + ".graph-reader", driver, keys.getPublic())) {
                    Dataleverans d;
                    do {
                        d = invanta(() -> raw.behandla(Duration.ofMillis(500)));
                        invantaKvittens(receipts, d, Leveranssteg.RADATA_LAGRADE);
                        rapportera(cache, d);
                    } while (!target.id().equals(d.id()));
                    do {
                        d = invanta(() -> graph.behandla(Duration.ofMillis(500)));
                        invantaKvittens(receipts, d, Leveranssteg.GRAFBEHANDLAD);
                        rapportera(cache, d);
                    } while (!target.id().equals(d.id()));
                }
                var stored = backend.lasProcess(process);
                if (!Leveransformat.samma(target, stored)) throw new IllegalStateException("Fel tillstånd i rådatasteget");
                try (var session = driver.session()) {
                    var row = session.run("MATCH (n:FfaObjekt {id:$id}) RETURN n.version AS version", Map.of("id", claim.getId())).single();
                    if (row.get("version").asLong() != target.objektVersion())
                        throw new IllegalStateException("Grafen har inte rätt objektversion");
                }

                Path manifest = Path.of("target", "pipeline-demo", run + ".json");
                Files.createDirectories(manifest.getParent());
                // Endast offentlig verifieringsnyckel sparas; demon kan granskas utan privata nycklar.
                Files.writeString(manifest, JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(
                        Map.of("rest", rest.uri().toString(), "kvittensTopic", topic + ".kvitton", "topic", topic, "graphTopic", graphTopic, "bucket", bucket, "process", process,
                                "objektId", claim.getId(), "dataleveransId", target.id().toString(),
                                "version", target.objektVersion(),
                                "verifieringsnyckelX509", Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()))));
                System.out.println("Hela kedjan verifierad. Inspektionsunderlag: " + manifest);
                System.out.println("Topics, cache, rådata och graf lämnas kvar. Varje körning har egen process och betrodd demonyckel.");
                if (Boolean.parseBoolean(env("FFA_PIPELINE_SERVE", "false"))) {
                    System.out.println("REST tillgängligt på " + rest.uri() + "; stoppa med Ctrl-C.");
                    while (!Thread.currentThread().isInterrupted()) receipts.behandla(Duration.ofMillis(500));
                }
            }
        }
    }

    private static void invantaKvittens(Kvittenskonsument receipts, Dataleverans d, Leveranssteg steg) {
        var deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            var receipt = receipts.behandla(Duration.ofMillis(500));
            if (receipt != null && receipt.dataleveransId().equals(d.id()) && receipt.steg() == steg) return;
        }
        throw new IllegalStateException("Kvittensen uteblev för " + d.id());
    }

    private static Dataleverans invanta(Supplier<Dataleverans> poll) {
        var deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            var d = poll.get();
            if (d != null) return d;
        }
        throw new IllegalStateException("Nästa steg kunde inte slutföras inom 30 sekunder");
    }

    private static void rapportera(PostgresKafkaLager cache, Dataleverans d) {
        System.out.printf("Leverans %s, objektversion %d: %s%n", d.id(), d.objektVersion(), cache.lasStatus(d.id()).steg());
    }
}
