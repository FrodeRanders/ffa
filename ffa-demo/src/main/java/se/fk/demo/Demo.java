package se.fk.demo;

import se.fk.hundbidrag.Applikation;
import se.fk.hundbidrag.modell.YrkandeOmHundbidrag;
import se.fk.mimer.runtime.*;
import se.fk.mimer.persistence.*;
import se.fk.data.modell.utils.SignatureUtils;

import java.nio.file.*;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Startar den förvaltade modellen med Kafka och PostgreSQL. */
public final class Demo {
    private Demo() {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 || args[0].startsWith("--") ? "target/demo-yrkande.json" : args[0]);
        var keys = Demonycklar.lasEllerSkapa(Path.of(env("FFA_NYCKLAR", ".demo/nycklar")));
        try (var kafka = new KafkaPublicerare(env("FFA_KAFKA", "localhost:19092"))) {
            var cache = new PostgresKafkaLager(env("FFA_JDBC", "jdbc:postgresql://localhost:15432/ffa"),
                    env("FFA_DB_USER", "ffa"), env("FFA_DB_PASSWORD", "ffa-demo"),
                    env("FFA_TOPIC", "ffa.hundbidrag"),
                    Leveranslage.valueOf(env("FFA_LEVERANSLAGE", "KAFKA_FORST")), kafka);
            cache.initiera();
            if (Arrays.asList(args).contains("--aterforsok")) {
                System.out.printf("Återförsökt %d leveranser; %d väntar fortfarande.%n", cache.skickaVantande(100), cache.antalVantande());
            } else {
                try (var arbetare = new Aterforsoksarbetare(cache, java.time.Duration.ofSeconds(5), 100)) {
                    kor(output, cache, keys);
                }
                System.out.printf("Väntande Kafka-leveranser i lokal cache: %d.%n", cache.antalVantande());
            }
        }
    }

    private static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }

    static void kor(Path output, Dokumentlager lager, KeyPair keys) throws Exception {
        var yrkanden = new ForvaltadeYrkanden<>(YrkandeOmHundbidrag.class, lager, keys.getPrivate(), keys.getPublic());

        // Ett signerat historiskt dokument går genom exakt samma leverans- och läsgräns som andra dokument.
        boolean nyttHistoriskt = lager.lasProcess("demo-historiskt") == null;
        if (nyttHistoriskt) {
            byte[] historic;
            try (var input = Demo.class.getResourceAsStream("/yrkande-v0.json")) { historic = input.readAllBytes(); }
            lager.lagra(new Dataleverans(Dataleverans.nyttId(), "demo-historiskt", "yrkande-historiskt", 0, 3,
                    Instant.now(), new LagratDokument(historic, SignatureUtils.sign(historic, keys.getPrivate()))));
        }
        var migrated = yrkanden.lasProcess("demo-historiskt");
        System.out.printf(nyttHistoriskt
                ? "Historiskt yrkande läst genom format 0 → 1 → 2 före Java-bindning. Objektversion: %d.%n"
                : "Historiskt yrkande återläst i aktuell modell. Objektversion: %d.%n", migrated.getVersion());
        yrkanden.lagra("demo-historiskt", migrated);

        String processId = env("FFA_PROCESS_ID", "hundbidrag-" + UUID.randomUUID());
        var application = new Applikation(yrkanden);
        var claim = lager.lasProcess(processId) == null ? application.handlagg(processId) : application.fortsattHandlaggning(processId);
        var delivery = lager.lasProcess(processId);
        // En ny gränsinstans simulerar en framtida aktivitet i processmotorn.
        var reloaded = new ForvaltadeYrkanden<>(YrkandeOmHundbidrag.class, lager, keys.getPrivate(), keys.getPublic()).lasProcess(processId);
        System.out.printf("Process %s återläst. Dataleverans: %s. Yrkandeversion: %d.%n", processId, delivery.id(), reloaded.getVersion());
        yrkanden.lasLeverans(delivery.id().toString());

        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, lager.las(claim.getId()).json());
        System.out.println("Verifierat underlag för separat grafprojektion: " + output);
    }
}
