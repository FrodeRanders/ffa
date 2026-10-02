package se.fk.mimer.runtime;

import java.util.concurrent.ConcurrentHashMap;

/** Lagringsadapter för PoC:en; data försvinner när processen avslutas. */
public final class Minneslager implements Dokumentlager {
    private final ConcurrentHashMap<String, LagratDokument> dokument = new ConcurrentHashMap<>();
    private final java.util.LinkedHashMap<java.util.UUID, Dataleverans> leveranser = new java.util.LinkedHashMap<>();
    public LagratDokument las(String id) {
        return dokument.get(id);
    }

    public void lagra(String id, LagratDokument value) {
        dokument.put(id, value);
    }

    @Override
    public synchronized void lagra(Dataleverans leverans) {
        leveranser.put(leverans.id(), leverans);
        lagra(leverans.objektId(), leverans.dokument());
    }

    @Override
    public synchronized Dataleverans lasProcess(String korrelationsId) {
        Dataleverans latest = null;
        for (var leverans : leveranser.values())
            if (leverans.korrelationsId().equals(korrelationsId)) latest = leverans;
        return latest;
    }

    @Override
    public synchronized Dataleverans lasLeverans(java.util.UUID id) {
        return leveranser.get(id);
    }
}
