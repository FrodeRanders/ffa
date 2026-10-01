package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;

import java.util.Date;

/** Ett producerat intyg med utfärdare, beskrivning och giltighetsperiod. */
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

    public Intyg() {} // Behövs vid återläsning med Jackson.

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
