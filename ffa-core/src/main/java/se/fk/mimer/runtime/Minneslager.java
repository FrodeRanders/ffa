package se.fk.mimer.runtime;

import java.util.concurrent.ConcurrentHashMap;

/** Lagringsadapter för PoC:en; data försvinner när processen avslutas. */
public final class Minneslager implements Dokumentlager {
    private final ConcurrentHashMap<String, LagratDokument> dokument = new ConcurrentHashMap<>();
    public LagratDokument las(String id) {
        return dokument.get(id);
    }

    public void lagra(String id, LagratDokument value) {
        dokument.put(id, value);
    }
}
