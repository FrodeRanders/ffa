package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;
import se.fk.data.modell.ffa.Context;
import se.fk.data.modell.ffa.Valuta;

import java.util.UUID;

@Context("https://data.fk.se/kontext/std/ersattning/1.0")
public class Ersattning {
    @JsonProperty("id")
    String id;

    @JsonProperty("version")
    int version = 1;

    @JsonProperty("typ")
    String typ;

    @Valuta
    @JsonProperty("amount")
    double amount;

    public Ersattning() {} // Required for deserialization

    public Ersattning(String typ, double amount) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();

        this.typ = typ;
        this.amount = amount;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Ersattning{");
        sb.append("id='").append(id).append('\'');
        sb.append(", version=").append(version);
        sb.append(", typ='").append(typ).append('\'');
        sb.append(", amount=").append(amount);
        sb.append('}');
        return sb.toString();
    }
}
