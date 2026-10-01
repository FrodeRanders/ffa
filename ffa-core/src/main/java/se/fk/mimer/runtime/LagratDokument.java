package se.fk.mimer.runtime;

/** Infrastrukturens signerade representation. Byte-arrayer kopieras vid in- och utläsning. */
public record LagratDokument(byte[] json, byte[] signatur) {
    // Inkommande arrayer får inte kunna ändra ett redan lagrat dokument via delade referenser.
    public LagratDokument {
        json = json.clone();
        signatur = signatur.clone();
    }

    // Även vid utläsning returneras kopior av den signerade representationen.
    @Override
    public byte[] json() {
        return json.clone();
    }

    @Override
    public byte[] signatur() {
        return signatur.clone();
    }
}
