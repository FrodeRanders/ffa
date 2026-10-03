package se.fk.mimer.persistence;

import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import se.fk.mimer.runtime.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KafkaPublicerareTest {
    private Dataleverans leverans() {
        return new Dataleverans(Dataleverans.nyttId(), "process", "objekt", 0, 1,
                Instant.now(), new LagratDokument("{}".getBytes(StandardCharsets.UTF_8), new byte[]{1}));
    }

    @Test
    void kafkaCommitSkerEfterLokalCommitOchLokaltFelAborterar() {
        var producer = new MockProducer<String, byte[]>(true, null, new StringSerializer(), new ByteArraySerializer());
        try (var publisher = new KafkaPublicerare(() -> producer)) {
            publisher.publicera("topic", leverans(), () -> {
                assertTrue(producer.transactionInFlight());
                assertFalse(producer.transactionCommitted());
                assertTrue(producer.history().isEmpty());
            });
            assertTrue(producer.transactionCommitted());
            assertEquals(1, producer.history().size());

            assertThrows(IllegalStateException.class, () -> publisher.publicera("topic", leverans(), () -> {
                throw new IllegalStateException("PostgreSQL-commit misslyckades");
            }));
            assertTrue(producer.transactionAborted());
            assertEquals(1, producer.history().size());
        }
    }

    @Test
    void osakerKafkaCommitAborterasInteOchNyProducentInitierasVidAterforsok() {
        var first = new MockProducer<String, byte[]>(true, null, new StringSerializer(), new ByteArraySerializer()) {
            @Override public void commitTransaction() {
                throw new org.apache.kafka.common.errors.TimeoutException("Commitutfallet är osäkert");
            }
        };
        var second = new MockProducer<String, byte[]>(true, null, new StringSerializer(), new ByteArraySerializer());
        var factories = new java.util.concurrent.atomic.AtomicInteger();
        var local = new java.util.concurrent.atomic.AtomicBoolean();
        try (var publisher = new KafkaPublicerare(() -> factories.getAndIncrement() == 0 ? first : second)) {
            var delivery = leverans();
            assertThrows(org.apache.kafka.common.errors.TimeoutException.class,
                    () -> publisher.publicera("topic", delivery, () -> local.set(true)));
            assertTrue(local.get());
            assertFalse(first.transactionAborted());
            assertTrue(first.closed());
            publisher.publicera("topic", delivery);
            assertTrue(second.transactionInitialized());
            assertTrue(second.transactionCommitted());
            assertEquals(2, factories.get());
        }
    }

    @Test
    void metadataLiggerIHeadersOchJsonAndrasInte() {
        byte[] json = "{ \"data\": 42 }".getBytes(StandardCharsets.UTF_8);
        byte[] signature = {1, 2, 3};
        var delivery = new Dataleverans(UUID.randomUUID(), "process-17", "objekt-1", 0, 1,
                Instant.parse("2026-10-02T12:00:00Z"), new LagratDokument(json, signature));

        var message = KafkaPublicerare.meddelande("ffa.hundbidrag", delivery);

        assertEquals("process-17", message.key());
        assertArrayEquals(json, message.value());
        assertArrayEquals(signature, message.headers().lastHeader("signatur").value());
        assertEquals(delivery.id().toString(), new String(message.headers().lastHeader("dataleverans-id").value(), StandardCharsets.UTF_8));
        assertEquals("process-17", new String(message.headers().lastHeader("korrelations-id").value(), StandardCharsets.UTF_8));
    }
}
