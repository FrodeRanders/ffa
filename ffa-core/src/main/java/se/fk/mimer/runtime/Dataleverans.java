package se.fk.mimer.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Infrastrukturens leveranskuvert; metadata ändrar inte det signerade JSON-dokumentet. */
public record Dataleverans(UUID id, String korrelationsId, String objektId,
                          long forvantadVersion, long objektVersion,
                          Instant skapad, LagratDokument dokument) {
    private static final com.fasterxml.uuid.NoArgGenerator IDS =
            com.fasterxml.uuid.Generators.timeBasedEpochGenerator();

    /** Nya leveranser får UUID version 7; äldre id-versioner kan fortfarande läsas från cachen. */
    public static UUID nyttId() { return IDS.generate(); }

    public Dataleverans {
        Objects.requireNonNull(id);
        Objects.requireNonNull(skapad);
        Objects.requireNonNull(dokument);

        // PostgreSQL lagrar mikrosekunder; samma tidsvärde ska användas även vid replay.
        skapad = skapad.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if (korrelationsId == null || korrelationsId.isBlank() || objektId == null || objektId.isBlank())
            throw new IllegalArgumentException("Korrelations-id och objekt-id måste anges");
        if (forvantadVersion < 0 || objektVersion < forvantadVersion)
            throw new IllegalArgumentException("Ogiltiga objektversioner i dataleveransen");
    }
}
