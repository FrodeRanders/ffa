package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;

/** Handläggningens bedömning av arbetsförmågans omfattning. */
@Context("https://data.fk.se/kontext/std/bedomd-arbetsformaga/1.0")
public class BedomdArbetsformaga extends ProduceratResultat {

    public enum Omfattning {
        HEL("Hel"),
        EN_ATTONDEL("En åttondel"),
        OCHSAAVIDARE("Annat");

        Omfattning(String omfattning) {
            this.omfattning = omfattning;
        }

        String omfattning;
    }

    @JsonProperty("omfattning")
    public Omfattning omfattning;

    public BedomdArbetsformaga() {} // Behövs vid återläsning med Jackson.

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BedomdArbetsformaga{");
        sb.append(super.toString());
        sb.append(", omfattning='").append(omfattning).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
