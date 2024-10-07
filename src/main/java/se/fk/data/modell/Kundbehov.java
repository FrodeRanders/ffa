package se.fk.data.modell;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;

import java.util.List;
import java.util.UUID;

public class Kundbehov {
    @JsonProperty("@context")
    private final String context = "https://example.org/contexts/kundbehov";

    @JsonProperty("id")
    String id;

    @JsonProperty("version")
    int version = 1;

    @JsonProperty("description")
    String description;

    @JsonProperty("ersattningar")
    List<Ersattning> ersattningar;

    public Kundbehov() {} // Required for deserialization

    public Kundbehov(String description, List<Ersattning> ersattningar) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();
        this.description = description;
        this.ersattningar = ersattningar;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Kundbehov{");
        sb.append("context='").append(context).append('\'');
        sb.append(", id='").append(id).append('\'');
        sb.append(", version=").append(version);
        sb.append(", description='").append(description).append('\'');
        sb.append(", ersattningar=").append(ersattningar);
        sb.append('}');
        return sb.toString();
    }
}

