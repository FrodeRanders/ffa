package se.fk.mimer.persistence;

/** Två uttryckliga felpolicyer; ingen av dem är en distribuerad atomisk transaktion. */
public enum Leveranslage {
    /** Kafka-kvitto krävs innan cachen får en ny rad. Kafka kan dock lyckas trots ett senare databasfel. */
    KAFKA_FORST,
    /** Tillstånd och väntande leverans lagras hållbart tillsammans; Kafka kan återförsökas senare. */
    LOKAL_RESILIENS
}
