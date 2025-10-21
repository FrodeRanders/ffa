package se.fk.hundbidrag.modell;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.ffa.Context;
import se.fk.data.modell.v1.Ersattning;
import java.util.Collection;


@Context("https://data.fk.se/kontext/hundbidrag/kundbehov/1.0")
public class Kundbehov extends se.fk.data.modell.v1.Kundbehov {

    @JsonProperty("ras")
    String ras;

    public Kundbehov() {} // Required for deserialization

    public Kundbehov(String description, Collection<Ersattning> ersattningar, String ras) {
        super(description, ersattningar);
        this.ras = ras;
    }

    @Override
    public String toString() {
        String s = super.toString();
        s += "+{";
        s += "ras='" + ras;
        s += "'}";
        return s;
    }
}
