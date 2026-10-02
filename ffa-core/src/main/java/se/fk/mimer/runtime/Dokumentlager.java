package se.fk.mimer.runtime;

/** Lagring kräver fullständig leveransmetadata; inga adaptrar får tappa processhistoriken. */
public interface Dokumentlager extends Dokumentkalla {
    /** Ny leverans som ska vidare till Kafka enligt adapterns leveransläge. */
    void lagra(Dataleverans leverans);

    /** Återför verifierat masterdata till cachen, med ursprungligt id och utan ny publicering. */
    void aterstall(Dataleverans leverans);
}
