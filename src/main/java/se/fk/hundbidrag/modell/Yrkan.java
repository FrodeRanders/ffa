package se.fk.hundbidrag.modell;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;


@Context("https://data.fk.se/kontext/hundbidrag/yrkan/1.0")
public class Yrkan extends se.fk.data.modell.v1.Yrkan {

    @JsonProperty("ras")
    String ras;

    public Yrkan() {} // Required for deserialization

    public Yrkan(String description, String ras) {
        super(description);
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
