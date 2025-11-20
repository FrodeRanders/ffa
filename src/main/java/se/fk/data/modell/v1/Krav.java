package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Belopp;
import se.fk.data.modell.annotations.Context;

/* ----------------------------------------------------
 * Consider using Lombok for ergonomic reasons, to
 * achieve fluent, chained, and custom accessors.
 *
 * Imports:
 *   import lombok.experimental.Accessors;
 *
 * Class annotation:
 *   @Data
 *   @Accessors(chain = true, fluent = true)
 *
 * Use:
 *   Ersattning e = new Ersattning("id-123")
 *       .datum(new Date())
 *       .typ(Ersattning.Typ.HUNDBIDRAG)
 *       .belopp(1000.0);
 * ----------------------------------------------------
 */

@Context("https://data.fk.se/kontext/std/krav/1.0")
public class Krav extends ProduceratResultat {
    public enum Typ {
        NAGON("Någon typ");

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

    public Krav() {} // Required for deserialization

    public Krav(String id) {
        super(id);
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
