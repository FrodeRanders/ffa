package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.ffa.Context;
import se.fk.data.modell.ffa.Belopp;

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
    // or @Belopp(valuta="valuta:SEK", skattestatus="sfa:skattepliktig", period="sfa:perdag")
    @JsonProperty("belopp")
    public double belopp;

    public Ersattning() {} // Required for deserialization

    public Ersattning(Typ typ, double belopp) {
        super(null);

        this.typ = typ;
        this.belopp = belopp;
    }

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
