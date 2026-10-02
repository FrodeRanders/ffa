package se.fk.mimer.runtime;

import java.util.UUID;

/** Infrastrukturens läskontrakt. null betyder att tillståndet saknas i denna källa. */
public interface Dokumentkalla {
    Dataleverans lasObjekt(String id);
    Dataleverans lasProcess(String korrelationsId);
    Dataleverans lasLeverans(UUID dataleveransId);

    default LagratDokument las(String id) {
        var leverans = lasObjekt(id);
        return leverans == null ? null : leverans.dokument();
    }
}
