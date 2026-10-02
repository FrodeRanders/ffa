package se.fk.mimer.persistence;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import se.fk.mimer.runtime.Dataleverans;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/** JSON skickas oförändrat som värde; headers utgör metadatakanalen. */
public final class KafkaPublicerare implements Leveranspublicerare, AutoCloseable {
    private final KafkaProducer<String, byte[]> producer;

    public KafkaPublicerare(String bootstrapServers) {
        Properties config = new Properties();
        config.put("bootstrap.servers", bootstrapServers);
        config.put("key.serializer", StringSerializer.class.getName());
        config.put("value.serializer", ByteArraySerializer.class.getName());
        config.put("acks", "all");
        config.put("enable.idempotence", "true");
        config.put("delivery.timeout.ms", "10000");
        config.put("request.timeout.ms", "3000");
        config.put("max.block.ms", "10000");
        producer = new KafkaProducer<>(config);
    }

    /** Idempotensen täcker producentens interna återförsök; outbox-replay kräver deduplicering hos mottagaren. */
    public static ProducerRecord<String, byte[]> meddelande(String topic, Dataleverans leverans) {
        var record = new ProducerRecord<String, byte[]>(topic, leverans.korrelationsId(), leverans.dokument().json());
        record.headers().add("dataleverans-id", utf8(leverans.id().toString()));
        record.headers().add("korrelations-id", utf8(leverans.korrelationsId()));
        record.headers().add("objekt-id", utf8(leverans.objektId()));
        record.headers().add("skapad", utf8(leverans.skapad().toString()));
        record.headers().add("signatur", leverans.dokument().signatur());
        record.headers().add("signatur-algoritm", utf8("JCS+RSA-PSS-SHA512"));
        return record;
    }

    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    @Override
    public void publicera(String topic, Dataleverans leverans) {
        try {
            producer.send(meddelande(topic, leverans)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka-leveransen avbröts", e);
        } catch (ExecutionException e) {
            // Vid ett timeout-fel kan meddelandet redan finnas hos Kafka; samma leverans-id används vid replay.
            throw new IllegalStateException("Kafka-leveransen kunde inte kvitteras", e.getCause());
        }
    }

    @Override
    public void close() { producer.close(Duration.ofSeconds(5)); }
}
