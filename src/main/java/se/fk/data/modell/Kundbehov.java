package se.fk.data.modell;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Kundbehov {
    @JsonProperty("@context")
    private final String context = "https://example.org/contexts/kundbehov";

    @JsonProperty("id")
    int id;

    @JsonProperty("description")
    String description;

    @JsonProperty("ersattningar")
    List<Ersattning> ersattningar;

    public Kundbehov() {} // Required for deserialization

    public Kundbehov(int id, String description, List<Ersattning> ersattningar) {
        this.id = id;
        this.description = description;
        this.ersattningar = ersattningar;
    }
}

