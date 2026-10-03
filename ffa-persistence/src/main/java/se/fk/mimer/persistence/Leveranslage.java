package se.fk.mimer.persistence;

/** Två uttryckliga felpolicyer; ingen av dem är en distribuerad atomisk transaktion. */
public enum Leveranslage {
    /** Lokal commit föregår Kafka-commit; båda krävs innan verksamhetsprocessen får fortsätta. */
    STRIKT,
    /** Äldre konfigurationsnamn för STRIKT, med samma nya commit-ordning. */
    @Deprecated
    KAFKA_FORST,
    /** Tillstånd och väntande leverans lagras hållbart tillsammans; Kafka kan återförsökas senare. */
    LOKAL_RESILIENS;

    public boolean strikt() { return this != LOKAL_RESILIENS; }
}
