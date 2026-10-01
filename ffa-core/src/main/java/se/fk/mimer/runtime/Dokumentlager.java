package se.fk.mimer.runtime;

/** Lagringsadapter för infrastrukturen. null betyder att dokumentet saknas. */
public interface Dokumentlager {
    /** Returnerar dokumentet för identiteten, eller null när det saknas. */
    LagratDokument las(String id);

    /** Skriver den färdiga signerade representationen; fel rapporteras till anroparen. */
    void lagra(String id, LagratDokument dokument);
}
