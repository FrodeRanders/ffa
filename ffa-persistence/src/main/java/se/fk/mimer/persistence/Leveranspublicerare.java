package se.fk.mimer.persistence;

import se.fk.mimer.runtime.Dataleverans;

/** Returnerar först efter kvitto; adaptern behöver inte känna till JSON-modellen. */
@FunctionalInterface
public interface Leveranspublicerare {
    void publicera(String topic, Dataleverans leverans);
}
