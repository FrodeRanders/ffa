package se.fk.mimer.persistence;

import java.util.UUID;

/** Leverans-id gör att ett delvis genomfört försök kan spåras och repareras. */
public final class Leveransfel extends IllegalStateException {
    private final UUID dataleveransId;
    private final boolean lokaltLagrat;
    private final boolean kafkaKvitterat;

    public Leveransfel(UUID id, boolean lokaltLagrat, boolean kafkaKvitterat, Throwable cause) {
        super("Dataleverans " + id + " kunde inte slutföras; lokalt lagrad=" + lokaltLagrat
                + "; Kafka-commit bekräftad=" + kafkaKvitterat, cause);
        this.dataleveransId = id;
        this.lokaltLagrat = lokaltLagrat;
        this.kafkaKvitterat = kafkaKvitterat;
    }

    public UUID dataleveransId() { return dataleveransId; }
    /** Positiv lokal commitbekräftelse. false utesluter inte ett osäkert databasutfall. */
    public boolean lokaltLagrat() { return lokaltLagrat; }
    /** Positiv Kafka-commitbekräftelse. false betyder inte säkert att Kafka saknar leveransen. */
    public boolean kafkaKvitterat() { return kafkaKvitterat; }
}
