package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;
import se.fk.data.modell.annotations.PII;

@Context("https://data.fk.se/kontext/std/juridiskperson/1.0")
public class JuridiskPerson extends Person {
    @JsonProperty("orgnummer")
    public String orgnummer;

    public JuridiskPerson() {} // Required for deserialization

    public JuridiskPerson(String orgnummer) {
        this.orgnummer = orgnummer;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("JuridiskPerson{");
        sb.append("orgnummer='").append(orgnummer).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
