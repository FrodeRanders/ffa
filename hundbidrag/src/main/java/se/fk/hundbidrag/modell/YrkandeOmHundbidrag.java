package se.fk.hundbidrag.modell;

import se.fk.data.modell.annotations.Context;
import se.fk.data.modell.v1.Yrkande;

/** Förmånens utvidgning av FFA:s yrkande, med en uppgift om hundens ras. */
@Context("https://data.fk.se/kontext/hundbidrag/yrkande/1.0")
public class YrkandeOmHundbidrag extends Yrkande {
    public String ras;

    public YrkandeOmHundbidrag() {}
    public YrkandeOmHundbidrag(String beskrivning, String ras) {
        super(beskrivning);
        this.ras = ras;
    }
}
