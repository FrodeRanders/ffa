package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Belopp;
import se.fk.data.modell.annotations.Context;

/** Ett producerat resultat med ersättningstyp, belopp och eventuell period. */
@Context("https://data.fk.se/kontext/std/ersattning/1.0")
public class Ersattning extends ProduceratResultat {
    public enum Typ {
        SJUKPENNING ("ersattningstyp:SJUKPENNING"),
        FORALDRAPENNING ("ersattningstyp:FORALDRAPENNING"),
        HUNDBIDRAG ("ersattningstyp:HUNDBIDRAG");

        Typ(String typ) {
            this.typ = typ;
        }

        String typ;
    }

    @JsonProperty("typ")
    public Typ typ;

    @Belopp
    @JsonProperty("belopp")
    public double belopp;

    @JsonProperty("period")
    public Period period;

    public Ersattning() {} // Behövs vid återläsning med Jackson.

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
}
