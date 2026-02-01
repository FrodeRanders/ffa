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
 *   Intyg i = new Intyg("id-123")
 *       .beskrivning("abc def ghi ...")
 *       .utfardatDatum(new Date());
 * ----------------------------------------------------
 */

@Context("https://data.fk.se/kontext/std/intyg/1.0")
public class Intyg extends ProduceratResultat {

    @JsonProperty("giltighetsperiod")
    public Period giltighetsperiod;

    @JsonProperty("institution")
    public String institution;

    @JsonProperty("beskrivning")
    public String beskrivning;

    @JsonProperty("utfardat_datum")
    public Date utfardatDatum;

    public Intyg() {} // Required for deserialization

    public Intyg(String id) {
        super(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Intyg{");
        sb.append(super.toString());
        sb.append(", giltighetsperiod=").append(giltighetsperiod);
        sb.append(", institution='").append(institution).append('\'');
        sb.append(", beskrivning='").append(beskrivning).append('\'');
        sb.append(", utfardatDatum='").append(utfardatDatum).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
