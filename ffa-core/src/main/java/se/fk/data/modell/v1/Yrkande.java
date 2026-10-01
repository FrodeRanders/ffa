package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;
import se.fk.data.modell.annotations.Som;

import java.util.ArrayList;

import java.util.Collection;

/** Samlar en parts yrkande, handläggningens producerade resultat och ett eventuellt beslut. */
@Context("https://data.fk.se/kontext/std/yrkande/1.0")
public class Yrkande extends Livscykelhanterad {
    @Som(roll = "ffa:yrkanden")
    @JsonProperty("person")
    public Person person;

    @JsonProperty("beskrivning")
    public String beskrivning;

    @JsonProperty("beslut")
    public Beslut beslut;

    @JsonProperty("producerat_resultat")
    public Collection<ProduceratResultat> produceratResultat = new ArrayList<>();

    public Yrkande() {} // Behövs vid återläsning med Jackson.

    public Yrkande(String beskrivning) {
        this.beskrivning = beskrivning;
    }

    @JsonIgnore
    public void setPerson(Person person) {
        this.person = person;
    }

    @JsonIgnore
    public void setBeslut(Beslut beslut) {
        this.beslut = beslut;
    }

    @JsonIgnore
    public void addProduceratResultat(ProduceratResultat produceratResultat) {
        this.produceratResultat.add(produceratResultat);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Yrkande{");
        sb.append(super.toString());
        sb.append(", beskrivning='").append(beskrivning).append('\'');
        sb.append(", person=").append(person);
        sb.append(", beslut=");
        if (null != beslut) {
            sb.append(beslut);
        }
        sb.append(", producerat-resultat=[");
        for (ProduceratResultat pr : produceratResultat) {
            sb.append(pr);
            sb.append(", ");
        }
        sb.append("]}");
        return sb.toString();
    }
}
