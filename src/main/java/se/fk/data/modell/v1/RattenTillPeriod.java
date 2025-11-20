package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;

import java.util.Date;

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
 *   RattenTillPeriod r = new RattenTillPeriod("id-123")
 *       .ersattningstyp(Ersattning.Typ.HUNDBIDRAG)
 *       .omfattning(RattenTillPeriod.Omfattning.HEL);
 * ----------------------------------------------------
 */

@Context("https://data.fk.se/kontext/std/ratten-till-period/1.0")
public class RattenTillPeriod extends ProduceratResultat {

    public enum Omfattning {
        HEL("Hel"),
        EN_ATTONDEL("En åttondel"),
        OCHSAAVIDARE("Annat");

        Omfattning(String omfattning) {
            this.omfattning = omfattning;
        }

        String omfattning;
    }

    @JsonProperty("ersattningstyp")
    public Ersattning.Typ ersattningstyp;

    @JsonProperty("omfattning")
    public Omfattning omfattning;

    public RattenTillPeriod() {} // Required for deserialization

    public RattenTillPeriod(String id) {
        super(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RattenTillPeriod{");
        sb.append(super.toString());
        sb.append(", ersattningstyp='").append(ersattningstyp).append('\'');
        sb.append(", omfattning='").append(omfattning).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
