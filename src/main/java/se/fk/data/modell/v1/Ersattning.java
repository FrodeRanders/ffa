package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;
import se.fk.data.modell.ffa.Context;
import se.fk.data.modell.ffa.Belopp;

import java.util.UUID;

@Context("https://data.fk.se/kontext/std/ersattning/1.0")
public class Ersattning extends LivscykelHanterad {
    @JsonProperty("typ")
    public String typ;

    @Belopp
    // or @Belopp(valuta="valuta:SEK", skattestatus="sfa:skattepliktig", period="sfa:perdag")
    @JsonProperty("belopp")
    public double belopp;

    public Ersattning() {} // Required for deserialization

    public Ersattning(String typ, double belopp) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();

        this.typ = typ;
        this.belopp = belopp;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Ersattning{");
        sb.append(super.toString());
        sb.append(", typ='").append(typ).append('\'');
        sb.append(", belopp=").append(belopp);
        sb.append('}');
        return sb.toString();
    }
}
