package se.fk.data.modell;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;

import java.util.UUID;

public class Ersattning {
    @JsonProperty("@context")
    private final String context = "https://example.org/contexts/ersattning";

    @JsonProperty("id")
    String id;

    @JsonProperty("version")
    int version = 1;

    @JsonProperty("type")
    String type;

    @JsonProperty("amount")
    double amount;

    public Ersattning() {} // Required for deserialization

    public Ersattning(String type, double amount) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();

        this.type = type;
        this.amount = amount;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Ersattning{");
        sb.append("context='").append(context).append('\'');
        sb.append(", id='").append(id).append('\'');
        sb.append(", version=").append(version);
        sb.append(", type='").append(type).append('\'');
        sb.append(", amount=").append(amount);
        sb.append('}');
        return sb.toString();
    }
}
