package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;
import se.fk.data.modell.ffa.Context;
import se.fk.data.modell.ffa.Valuta;

import java.util.UUID;

@Context(value = "https://data.fk.se/kontext/ersattning/1.0")
public class Ersattning {
    @JsonProperty("id")
    String id;

    @JsonProperty("version")
    int version = 1;

    @JsonProperty("type")
    String type;

    @Valuta
    @JsonProperty("amount")
    double amount;

    public Ersattning() {} // Required for deserialization

    public Ersattning(String type, double amount) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();

        this.type = type;
        this.amount = amount;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Ersattning{");
        sb.append("id='").append(id).append('\'');
        sb.append(", version=").append(version);
        sb.append(", type='").append(type).append('\'');
        sb.append(", amount=").append(amount);
        sb.append('}');
        return sb.toString();
    }
}
