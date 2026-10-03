package se.fk.mimer.persistence;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import java.time.Duration;
import java.util.*;

/** SQL-commit före offsetcommit: avbrott ger återleverans, aldrig tappad bekräftelse. */
public final class Kvittenskonsument implements AutoCloseable {
    private final KafkaConsumer<String, byte[]> consumer;
    private final PostgresKafkaLager cache;

    public Kvittenskonsument(String broker, String kvittensTopic, String group, PostgresKafkaLager cache) {
        this.cache = Objects.requireNonNull(cache);
        consumer = new KafkaConsumer<>(Map.of("bootstrap.servers", broker, "group.id", group,
                "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
                "value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer",
                "enable.auto.commit", false, "auto.offset.reset", "earliest",
                "isolation.level", "read_committed", "max.poll.records", 1));
        consumer.subscribe(List.of(kvittensTopic));
    }

    /** null betyder tom poll. Kvitton för andra förmånstopics konsumeras utan cacheändring. */
    public Kvittens behandla(Duration timeout) {
        var records = consumer.poll(timeout);
        if (records.isEmpty()) return null;
        var record = records.iterator().next();
        var partition = new TopicPartition(record.topic(), record.partition());
        try {
            var kvittens = Kvittens.las(record.value());
            if (!kvittens.korrelationsId().equals(record.key()))
                throw new IllegalArgumentException("Kvittensens Kafka-nyckel avviker från korrelations-id");
            if (cache.topic().equals(kvittens.topic())) cache.bekrafta(kvittens);
            consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
            return kvittens;
        } catch (RuntimeException e) {
            consumer.seek(partition, record.offset());
            throw e;
        }
    }

    @Override public void close() { consumer.close(Duration.ofSeconds(5)); }
}
