package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Belopp;
import se.fk.data.modell.annotations.Context;

/**
 * Ett producerat krav med typ, belopp och eventuell period.
 */
@Context("https://data.fk.se/kontext/std/krav/1.0")
public class Krav extends ProduceratResultat {
    @JsonProperty("typ")
    public Typ typ;
    @Belopp
    @JsonProperty("belopp")
    public double belopp;
    @JsonProperty("period")
    public Period period;

    public Krav() {
    } // Behövs vid återläsning med Jackson.

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Ersattning{");
        sb.append(super.toString());
        sb.append(", typ=");
        if (null != typ) {
            sb.append('\'').append(typ.typ).append('\'');
        }
        sb.append(", belopp=").append(belopp);
        sb.append('}');
        return sb.toString();
    }

    public enum Typ {
        NAGON("Någon typ");

        String typ;

        Typ(String typ) {
            this.typ = typ;
        }
    }
}
