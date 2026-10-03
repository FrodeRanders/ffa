package se.fk.mimer.pipeline;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.TopicPartition;
import se.fk.mimer.persistence.*;
import se.fk.mimer.runtime.Dataleverans;

import java.security.PublicKey;
import java.time.Duration;
import java.util.*;

/** N−1: rådatacommit före Kafka-transaktionens atomiska vidareleverans och offsetcommit. */
public final class Radatasteg implements AutoCloseable {
    private final KafkaConsumer<String, byte[]> consumer;
    private KafkaProducer<String, byte[]> producer;
    private final String broker;
    private final String producerId = "ffa-backend-" + UUID.randomUUID();
    private final String graphTopic;
    private final String inputTopic;
    private final String kvittensTopic;
    private final Radatalager backend;
    private final PublicKey key;
    private final Runnable efterLagring;

    public Radatasteg(String broker, String inputTopic, String graphTopic, String group,
                     Radatalager backend, PublicKey key) {
        this(broker, inputTopic, graphTopic, group, backend, key, () -> {});
    }

    // Felinjektion efter externa commits, före Kafka-commit.
    Radatasteg(String broker, String inputTopic, String graphTopic, String group,
               Radatalager backend, PublicKey key, Runnable efterLagring) {
        this.broker = broker;
        this.graphTopic = graphTopic;
        this.inputTopic = inputTopic;
        this.kvittensTopic = inputTopic + ".kvitton";
        this.backend = backend;
        this.key = key;
        this.efterLagring = efterLagring;
        consumer = KafkaFlode.konsument(broker, inputTopic, group);
    }

    /** Returnerar den slutförda leveransen eller null om inget meddelande kommit. */
    public Dataleverans behandla(Duration timeout) {
        var records = consumer.poll(timeout);
        if (records.isEmpty()) return null;
        var record = records.iterator().next();
        var partition = new TopicPartition(record.topic(), record.partition());
        boolean committing = false;
        boolean started = false;
        try {
            if (producer == null) producer = KafkaFlode.producent(broker, producerId);
            producer.beginTransaction();
            started = true;
            var delivery = Leveransformat.las(record, key);
            backend.lagra(delivery);
            efterLagring.run();
            var forwarded = KafkaPublicerare.meddelande(graphTopic, delivery);
            forwarded.headers().add("ursprungs-topic", inputTopic.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            producer.send(forwarded).get();
            producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(kvittensTopic,
                    delivery.korrelationsId(), Kvittens.skapa(inputTopic, delivery, Leveranssteg.RADATA_LAGRADE).json())).get();
            producer.sendOffsetsToTransaction(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)),
                    consumer.groupMetadata());
            committing = true;
            producer.commitTransaction();
            return delivery;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (started && !committing) {
                try { producer.abortTransaction(); }
                catch (RuntimeException abort) { e.addSuppressed(abort); }
            }
            // Ny init med samma producent-id löser även osäkra commits före återleverans.
            if (producer != null) {
                try { producer.close(Duration.ZERO); }
                catch (RuntimeException close) { e.addSuppressed(close); }
                producer = null;
            }
            consumer.seek(partition, record.offset());
            throw new IllegalStateException("Rådatasteget kunde inte slutföra leveransen; samma post återförsöks", e);
        }
    }

    @Override public void close() {
        try { consumer.close(Duration.ofSeconds(5)); }
        finally { if (producer != null) producer.close(Duration.ofSeconds(5)); }
    }
}
