package se.fk.hundbidrag.modell;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;
import se.fk.data.modell.v1.Yrkande;


@Context("https://data.fk.se/kontext/hundbidrag/yrkande/1.0")
public class YrkandeOmHundbidrag extends Yrkande {

    @JsonProperty("ras")
    String ras;

    public YrkandeOmHundbidrag() {} // Required for deserialization

    public YrkandeOmHundbidrag(String description, String ras) {
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
