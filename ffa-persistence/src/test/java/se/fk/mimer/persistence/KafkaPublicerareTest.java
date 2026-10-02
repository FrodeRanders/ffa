package se.fk.mimer.persistence;

import org.junit.jupiter.api.Test;
import se.fk.mimer.runtime.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KafkaPublicerareTest {
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
