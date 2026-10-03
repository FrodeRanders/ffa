package se.fk.mimer.pipeline;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.producer.*;
import se.fk.mimer.persistence.*;
import org.neo4j.driver.Driver;
import se.fk.mimer.graph.Grafprojektion;
import se.fk.mimer.runtime.Dataleverans;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Map;

/** N: beständig versionsskyddad grafprojektion före offsetcommit. */
public final class Grafsteg implements AutoCloseable {
    private final KafkaConsumer<String, byte[]> consumer;
    private final Driver driver;
    private final String broker;
    private final String sourceTopic;
    private final String producerId = "ffa-graph-" + java.util.UUID.randomUUID();
    private KafkaProducer<String, byte[]> producer;
    private final PublicKey key;
    private final Runnable efterLagring;
    private final Grafprojektion projection = new Grafprojektion();

    public Grafsteg(String broker, String topic, String group, Driver driver, PublicKey key) {
        this(broker, topic, topic.endsWith(".graph") ? topic.substring(0, topic.length() - 6) : topic,
                group, driver, key);
    }

    public Grafsteg(String broker, String topic, String sourceTopic, String group, Driver driver, PublicKey key) {
        this(broker, topic, sourceTopic, group, driver, key, () -> {});
    }

    // Felinjektion mellan Neo4j-commit och kvittenspublicering.
    Grafsteg(String broker, String topic, String sourceTopic, String group, Driver driver, PublicKey key,
             Runnable efterLagring) {
        this.efterLagring = efterLagring;
        this.broker = broker;
        this.sourceTopic = sourceTopic;
        consumer = KafkaFlode.konsument(broker, topic, group);
        this.driver = driver;
        this.key = key;
        try (var session = driver.session()) { session.run(Grafprojektion.schema()).consume(); }
        catch (RuntimeException e) {
            consumer.close(Duration.ofSeconds(5));
            throw e;
        }
    }

    public Dataleverans behandla(Duration timeout) {
        var records = consumer.poll(timeout);
        if (records.isEmpty()) return null;
        var record = records.iterator().next();
        var partition = new TopicPartition(record.topic(), record.partition());
        boolean started = false;
        boolean committing = false;
        try {
            var headers = record.headers().headers("ursprungs-topic").iterator();
            if (!headers.hasNext()) throw new IllegalArgumentException("Ursprungstopic saknas");
            var header = headers.next();
            if (headers.hasNext() || header.value() == null || !sourceTopic.equals(
                    new String(header.value(), java.nio.charset.StandardCharsets.UTF_8)))
                throw new IllegalArgumentException("Fel ursprungstopic");
            if (producer == null) producer = KafkaFlode.producent(broker, producerId);
            producer.beginTransaction();
            started = true;
            var delivery = Leveransformat.las(record, key);
            String cypher = projection.projektera(delivery.dokument().json());
            try (var session = driver.session()) {
                // Resultatet kan vara tomt när en nyare version redan finns; även det är slutförd hantering.
                session.executeWrite(tx -> tx.run(cypher).consume());
            }
            efterLagring.run();
            // GRAFBEHANDLAD omfattar även versioner som säkert hoppats över på grund av en nyare graf.
            producer.send(new ProducerRecord<>(sourceTopic + ".kvitton", delivery.korrelationsId(),
                    Kvittens.skapa(sourceTopic, delivery, Leveranssteg.GRAFBEHANDLAD).json())).get();
            producer.sendOffsetsToTransaction(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)),
                    consumer.groupMetadata());
            committing = true;
            producer.commitTransaction();
            return delivery;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (started && !committing) {
                try { producer.abortTransaction(); } catch (RuntimeException abort) { e.addSuppressed(abort); }
            }
            if (producer != null) {
                try { producer.close(Duration.ZERO); } catch (RuntimeException close) { e.addSuppressed(close); }
                producer = null;
            }
            consumer.seek(partition, record.offset());
            throw new IllegalStateException("Grafsteget kunde inte slutföras; samma leverans återförsöks", e);
        }
    }

    @Override public void close() {
        try { consumer.close(Duration.ofSeconds(5)); }
        finally { if (producer != null) producer.close(Duration.ofSeconds(5)); }
    }
}
