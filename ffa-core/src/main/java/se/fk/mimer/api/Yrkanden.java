package se.fk.mimer.api;

import se.fk.data.modell.v1.Yrkande;

/** Förmånens gräns mot förvaltad lagring. Inga transportformat eller nycklar ingår. */
public interface Yrkanden<T extends Yrkande> {
    /**
     * Returnerar ett fristående, verifierat objekt i den aktuella modellen.
     * Saknad identitet eller ogiltigt dokument rapporteras som ett undantag.
     */
    T las(String id);

    /**
     * Lagrar ett fristående tillstånd och returnerar objektet med uppdaterade versioner.
     * Använd returvärdet vid fortsatt handläggning; det inlämnade objektet ändras inte.
     */
    T lagra(T yrkande);

    /** Lagrar ett nytt tillstånd för processmotorns korrelations-id. */
    T lagra(String korrelationsId, T yrkande);

    /** Hämtar processens senast lokalt lagrade tillstånd, även om leverans väntar på återförsök. */
    T lasProcess(String korrelationsId);

    /** Hämtar den historiska representation som tillhör en specifik dataleverans. */
    T lasLeverans(String dataleveransId);
}
