package se.fk.mimer.persistence;

import se.fk.mimer.runtime.Dataleverans;

/** Kafka-commit får ske först när lokalCommit har lyckats. Returnerar efter Kafka-commit. */
@FunctionalInterface
public interface Leveranspublicerare {
    void publicera(String topic, Dataleverans leverans, Runnable lokalCommit);

    /** Återförsök av en redan beständigt lagrad leverans. */
    default void publicera(String topic, Dataleverans leverans) {
        publicera(topic, leverans, () -> {});
    }
}
