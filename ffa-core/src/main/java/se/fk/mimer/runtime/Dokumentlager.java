package se.fk.mimer.runtime;

/** Lagringsadapter för infrastrukturen. null betyder att dokumentet saknas. */
public interface Dokumentlager {
    /** Returnerar dokumentet för identiteten, eller null när det saknas. */
    LagratDokument las(String id);

    /** Skriver den färdiga signerade representationen; fel rapporteras till anroparen. */
    void lagra(String id, LagratDokument dokument);

    /** Leveransmetadata används av beständiga adaptrar; äldre testadaptrar kan lagra per objekt-id. */
    default void lagra(Dataleverans leverans) {
        lagra(leverans.objektId(), leverans.dokument());
    }

    default Dataleverans lasProcess(String korrelationsId) {
        throw new UnsupportedOperationException("Adaptern stöder inte processhistorik");
    }

    default Dataleverans lasLeverans(java.util.UUID dataleveransId) {
        throw new UnsupportedOperationException("Adaptern stöder inte leveranshistorik");
    }
}
