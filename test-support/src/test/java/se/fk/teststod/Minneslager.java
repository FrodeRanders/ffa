package se.fk.teststod;

import se.fk.mimer.runtime.Dataleverans;
import se.fk.mimer.runtime.Dokumentlager;
import se.fk.mimer.runtime.LagratDokument;

import java.util.concurrent.ConcurrentHashMap;

/** Testhjälp för enhetstester; kompileras endast som testkod och ingår inte i demots JAR-filer. */
public class Minneslager implements Dokumentlager {
    private final ConcurrentHashMap<String, LagratDokument> dokument = new ConcurrentHashMap<>();
    private final java.util.LinkedHashMap<java.util.UUID, Dataleverans> leveranser = new java.util.LinkedHashMap<>();
    public LagratDokument las(String id) {
        return dokument.get(id);
    }

    public void lagra(String id, LagratDokument value) {
        dokument.put(id, value);
    }

    @Override
    public synchronized Dataleverans lasObjekt(String id) {
        return leveranser.values().stream().filter(l -> l.objektId().equals(id))
                .max(java.util.Comparator.comparingLong(Dataleverans::objektVersion)).orElse(null);
    }

    @Override
    public synchronized void aterstall(Dataleverans leverans) {
        var tidigare = lasObjekt(leverans.objektId());
        leveranser.putIfAbsent(leverans.id(), leverans);
        if (tidigare == null || tidigare.objektVersion() <= leverans.objektVersion())
            dokument.put(leverans.objektId(), leverans.dokument());
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
            if (leverans.korrelationsId().equals(korrelationsId)
                    && (latest == null || latest.objektVersion() <= leverans.objektVersion())) latest = leverans;
        return latest;
    }

    @Override
    public synchronized Dataleverans lasLeverans(java.util.UUID id) {
        return leveranser.get(id);
    }
}
