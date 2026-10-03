package se.fk.mimer.persistence;

/** Bekräftat framsteg per leverans, inte objektets version eller ett villkor för grafens synlighet. */
public enum Leveranssteg {
    LOKALT_LAGRAD,
    KAFKA_PUBLICERAD,
    RADATA_LAGRADE,
    /** Tillämpad, redan tillämpad eller ersatt av en nyare version. */
    GRAFBEHANDLAD
}
