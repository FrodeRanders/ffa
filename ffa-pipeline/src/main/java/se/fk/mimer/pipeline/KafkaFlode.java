package se.fk.mimer.pipeline;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.*;

import java.util.*;

final class KafkaFlode {
    private KafkaFlode() {}

    static KafkaConsumer<String, byte[]> konsument(String broker, String topic, String group) {
        var config = new Properties();
        config.put("bootstrap.servers", broker);
        config.put("group.id", group);
        config.put("enable.auto.commit", "false");
        config.put("auto.offset.reset", "earliest");
        config.put("isolation.level", "read_committed");
        // Ett fel kan då återställa exakt den pollade posten utan att tappa resten av en batch.
        config.put("max.poll.records", "1");
        var consumer = new KafkaConsumer<>(config, new StringDeserializer(), new ByteArrayDeserializer());
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    static KafkaProducer<String, byte[]> producent(String broker, String id) {
        var config = new Properties();
        config.put("bootstrap.servers", broker);
        config.put("transactional.id", id);
        config.put("acks", "all");
        config.put("enable.idempotence", "true");
        config.put("max.block.ms", "10000");
        config.put("delivery.timeout.ms", "10000");
        config.put("request.timeout.ms", "3000");
        var producer = new KafkaProducer<>(config, new StringSerializer(), new ByteArraySerializer());
        try { producer.initTransactions(); }
        catch (RuntimeException e) {
            producer.close(java.time.Duration.ZERO);
            throw e;
        }
        return producer;
    }
}
