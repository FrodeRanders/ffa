package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.ffa.Belopp;
import se.fk.data.modell.ffa.Context;

@Context("https://data.fk.se/kontext/std/ersattning/1.0")
public class ProduceratResultat extends LivscykelHanterad {

    public ProduceratResultat() {} // Required for deserialization

    public ProduceratResultat(String id) {
        super(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ProduceratResultat{");
        sb.append(super.toString());
        sb.append('}');
        return sb.toString();
    }
}
