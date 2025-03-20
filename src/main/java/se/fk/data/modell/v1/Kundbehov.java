package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;
import se.fk.data.modell.ffa.Context;

import java.util.Collection;
import java.util.UUID;

@Context("https://data.fk.se/kontext/kundbehov/1.0")
public class Kundbehov {
    @JsonProperty("id")
    String id;

    @JsonProperty("version")
    int version = 1;

    @JsonProperty("description")
    String description;

    @JsonProperty("ersattningar")
    Collection<Ersattning> ersattningar;

    @JsonProperty("beslut")
    Beslut beslut;


    public Kundbehov() {} // Required for deserialization

    public Kundbehov(String description, Collection<Ersattning> ersattningar) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();
        this.description = description;
        this.ersattningar = ersattningar;
    }

    @JsonIgnore
    public void setBeslut(Beslut beslut) {
        this.beslut = beslut;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Kundbehov{");
        sb.append("id='").append(id).append('\'');
        sb.append(", version=").append(version);
        sb.append(", description='").append(description).append('\'');
        sb.append(", ersattningar=").append(ersattningar);
        sb.append('}');
        return sb.toString();
    }
}

