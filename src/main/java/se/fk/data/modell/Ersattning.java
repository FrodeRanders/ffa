package se.fk.data.modell;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Ersattning {
    @JsonProperty("@context")
    private final String context = "https://example.org/contexts/ersattning";

    @JsonProperty("type")
    String type;

    @JsonProperty("amount")
    double amount;

    public Ersattning() {} // Required for deserialization

    public Ersattning(String type, double amount) {
        this.type = type;
        this.amount = amount;
    }
}
