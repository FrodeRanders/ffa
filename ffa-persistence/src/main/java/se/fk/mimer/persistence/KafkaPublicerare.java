package se.fk.mimer.persistence;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import se.fk.mimer.runtime.Dataleverans;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/** JSON skickas oförändrat som värde; headers utgör metadatakanalen. */
public final class KafkaPublicerare implements Leveranspublicerare, AutoCloseable {
    private final Supplier<Producer<String, byte[]>> producentfabrik;
    private Producer<String, byte[]> producer;
    private boolean closed;

    public KafkaPublicerare(String bootstrapServers) {
        this(bootstrapServers, "ffa-" + UUID.randomUUID());
    }

    /** Ett stabilt id måste vara unikt per aktiv producentinstans för att undvika oavsiktlig fencing. */
    public KafkaPublicerare(String bootstrapServers, String transactionalId) {
        this(() -> new KafkaProducer<>(konfiguration(bootstrapServers, transactionalId)));
    }

    KafkaPublicerare(Supplier<Producer<String, byte[]>> producentfabrik) {
        this.producentfabrik = producentfabrik;
    }

    private static Properties konfiguration(String bootstrapServers, String transactionalId) {
        Properties config = new Properties();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", StringSerializer.class.getName());
        config.put("value.serializer", ByteArraySerializer.class.getName());
        config.put("acks", "all");
        config.put("enable.idempotence", "true");
        config.put("transactional.id", transactionalId);
        config.put("delivery.timeout.ms", "10000");
        config.put("request.timeout.ms", "3000");
        config.put("max.block.ms", "10000");
        return config;
    }

    /** Idempotensen täcker interna återförsök; outbox-replay kan ge accepterade dubletter hos mottagaren. */
    public static ProducerRecord<String, byte[]> meddelande(String topic, Dataleverans leverans) {
        var record = new ProducerRecord<String, byte[]>(topic, leverans.korrelationsId(), leverans.dokument().json());
        record.headers().add("dataleverans-id", utf8(leverans.id().toString()));
        record.headers().add("korrelations-id", utf8(leverans.korrelationsId()));
        record.headers().add("objekt-id", utf8(leverans.objektId()));
        record.headers().add("objekt-version", utf8(Long.toString(leverans.objektVersion())));
        record.headers().add("forvantad-version", utf8(Long.toString(leverans.forvantadVersion())));
        record.headers().add("skapad", utf8(leverans.skapad().toString()));
        record.headers().add("signatur", leverans.dokument().signatur());
        record.headers().add("signatur-algoritm", utf8("JCS+RSA-PSS-SHA512"));
        return record;
    }

    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    @Override
    public synchronized void publicera(String topic, Dataleverans leverans, Runnable lokalCommit) {
        if (closed) throw new IllegalStateException("Kafka-publiceraren är stängd");
        boolean started = false;
        boolean committing = false;
        try {
            if (producer == null) {
                producer = producentfabrik.get();
                // Återinitiering med samma transactional.id löser tidigare producenters transaktioner.
                producer.initTransactions();
            }
            producer.beginTransaction();
            started = true;
            producer.send(meddelande(topic, leverans)).get();
            lokalCommit.run();
            committing = true;
            producer.commitTransaction();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            var failure = new IllegalStateException("Kafka-leveransen avbröts", e);
            aterhamta(started, committing, failure);
            throw failure;
        } catch (ExecutionException e) {
            var failure = new IllegalStateException("Kafka-leveransen kunde inte kvitteras", e.getCause());
            aterhamta(started, committing, failure);
            throw failure;
        } catch (RuntimeException e) {
            aterhamta(started, committing, e);
            throw e;
        }
    }

    private void aterhamta(boolean started, boolean committing, RuntimeException failure) {
        // Ett osäkert commitutfall får inte följas av abortTransaction. Nästa försök
        // initierar en ny producent med samma id; den beständiga leveransen behålls.
        if (started && !committing) {
            try { producer.abortTransaction(); return; }
            catch (RuntimeException abort) { failure.addSuppressed(abort); }
        }
        if (producer != null) {
            try { producer.close(Duration.ZERO); }
            catch (RuntimeException close) { failure.addSuppressed(close); }
            producer = null;
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (producer != null) producer.close(Duration.ofSeconds(5));
    }
}
