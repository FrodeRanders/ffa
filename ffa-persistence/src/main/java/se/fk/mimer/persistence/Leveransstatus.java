package se.fk.mimer.persistence;

import java.time.Instant;
import java.util.UUID;

/** Lokala observationstider, inte fjärrsystemens klockor. Saknad bekräftelse bevisar inte ett fel. */
public record Leveransstatus(UUID dataleveransId, Instant lokaltLagrad,
                             Instant kafkaPublicerad, Instant radataLagrade, Instant grafbehandlad,
                             int leveransforsok, String senasteFel) {
    public Leveranssteg steg() {
        if (grafbehandlad != null) return Leveranssteg.GRAFBEHANDLAD;
        if (radataLagrade != null) return Leveranssteg.RADATA_LAGRADE;
        if (kafkaPublicerad != null) return Leveranssteg.KAFKA_PUBLICERAD;
        return Leveranssteg.LOKALT_LAGRAD;
    }
}
