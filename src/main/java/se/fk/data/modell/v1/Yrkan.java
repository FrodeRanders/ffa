package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;
import se.fk.data.modell.annotations.Som;

import java.util.ArrayList;
import java.util.Collection;


/* ----------------------------------------------------
 * Consider using Lombok for ergonomic reasons, to
 * achieve fluent, chained, and custom accessors.
 *
 * Imports:
 *   import lombok.experimental.Accessors;
 *
 * Class annotation:
 *   @Data
 *   @Accessors(chain = true, fluent = true)
 *
 * Use:
 *   Yrkan y = new Yrkan("id-123")
 *       .person(insuredPerson)
 *       .beskrivning("abc def ghi ...");
 * ----------------------------------------------------
 */

@Context("https://data.fk.se/kontext/std/yrkan/1.0")
public class Yrkan extends LivscykelHanterad {
    @Som(typ = "ffa:yrkande")
    @JsonProperty("person")
    public Person person;

    @JsonProperty("beskrivning")
    public String beskrivning;

    @JsonProperty("beslut")
    public Beslut beslut;

    @JsonProperty("producerade_resultat")
    public Collection<ProduceratResultat> produceradeResultat = new ArrayList<>();

    public Yrkan() {} // Required for deserialization

    public Yrkan(String beskrivning) {
        super(null);
        this.beskrivning = beskrivning;
    }

    @JsonIgnore
    public void setPerson(FysiskPerson person) {
        this.person = person;
    }

    @JsonIgnore
    public void setBeslut(Beslut beslut) {
        this.beslut = beslut;
    }

    @JsonIgnore
    public void addProduceradeResultat(ProduceratResultat produceratResultat) {
        this.produceradeResultat.add(produceratResultat);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Kundbehov{");
        sb.append(super.toString());
        sb.append(", beskrivning='").append(beskrivning).append('\'');
        sb.append(", person=").append(person);
        sb.append(", beslut=");
        if (null != beslut) {
            sb.append(beslut);
        }
        sb.append(", producerade-resultat=[");
        for (ProduceratResultat pr : produceradeResultat) {
            sb.append(pr);
            sb.append(", ");
        }
        sb.append("]}");
        return sb.toString();
    }
}

