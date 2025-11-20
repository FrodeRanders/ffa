package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;
import se.fk.data.modell.annotations.PII;

@Context("https://data.fk.se/kontext/std/fysiskperson/1.0")
public class FysiskPerson extends Person {
    @PII(typ="pii:personnummer")
    @JsonProperty("personnummer")
    public String personnummer;

    public FysiskPerson() {} // Required for deserialization

    public FysiskPerson(String personnummer) {
        this.personnummer = personnummer;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FysiskPerson{");
        sb.append("personnummer='").append(personnummer).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
