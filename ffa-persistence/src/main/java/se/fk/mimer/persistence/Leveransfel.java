package se.fk.mimer.persistence;

import java.util.UUID;

/** Leverans-id gör att ett delvis genomfört försök kan spåras och repareras. */
public final class Leveransfel extends IllegalStateException {
    private final UUID dataleveransId;
    private final boolean kafkaKvitterat;

    public Leveransfel(UUID id, boolean kafkaKvitterat, Throwable cause) {
        super("Dataleverans " + id + " misslyckades; Kafka-kvitto=" + kafkaKvitterat, cause);
        this.dataleveransId = id;
        this.kafkaKvitterat = kafkaKvitterat;
    }

    public UUID dataleveransId() { return dataleveransId; }
    public boolean kafkaKvitterat() { return kafkaKvitterat; }
}
