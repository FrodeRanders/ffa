package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.ffa.Context;

import java.util.Collection;


@Context("https://data.fk.se/kontext/std/kundbehov/1.0")
public class Kundbehov extends LivscykelHanterad {
    @JsonProperty("person")
    public FysiskPerson person;

    @JsonProperty("beskrivning")
    public String beskrivning;

    @JsonProperty("ersattningar")
    public Collection<Ersattning> ersattningar;

    @JsonProperty("beslut")
    public Beslut beslut;

    public Kundbehov() {} // Required for deserialization

    public Kundbehov(String beskrivning, Collection<Ersattning> ersattningar) {
        super(null);
        this.beskrivning = beskrivning;
        this.ersattningar = ersattningar;
    }

    @JsonIgnore
    public void setPerson(FysiskPerson person) {
        this.person = person;
    }

    @JsonIgnore
    public void setBeslut(Beslut beslut) {
        this.beslut = beslut;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Kundbehov{");
        sb.append(super.toString());
        sb.append(", beskrivning='").append(beskrivning).append('\'');
        sb.append(", person=").append(person);
        sb.append(", ersattningar=").append(ersattningar);
        sb.append(", beslut=");
        if (null != beslut) {
            sb.append(beslut);
        }
        sb.append('}');
        return sb.toString();
    }
}

