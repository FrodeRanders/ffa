package se.fk.data.modell;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;

import java.util.Date;
import java.util.UUID;

public class Beslut {
    public enum Typ {
        INTERRIMISTISK (1),
        STALLNINGSTAGANDE (2),
        SLUTLIGT (3);

        Typ(int typ) {
            this.typ = typ;
        }

        int typ;
    }

    public enum Utfall {
        BEVILJAT (1),
        AVSLAG (2),
        DELVIS_BEVILJANDE (3),
        AVVISNING (4),
        AVSKRIVNING (5);

        Utfall(int utfall) {
            this.utfall = utfall;
        }

        int utfall;
    }

    public enum Lagrum {
        SFB_K112_P2a ("SFB Kap. 112 § 2a"),
        SFB_K112_P3 ("SFB Kap. 112 § 3"),
        SFB_K112_P4 ("SFB Kap. 112 § 4"),
        SFB_K113_P3_S1 ("SFB Kap. 113 § 3 p. 1"),
        SFB_K113_P3_S2 ("SFB Kap. 113 § 3 p. 2"),
        SFB_K113_P3_S3 ("SFB Kap. 113 § 3 p. 3"),
        FL_P36 ("FL § 36"),
        FL_P37 ("FL § 37"),
        FL_P38 ("FL § 38");

        Lagrum(String lagrum) {
            this.lagrum = lagrum;
        }

        String lagrum;
    }

    @JsonProperty("@context")
    private final String context = "https://data.fk.se/kontext/beslut/1.0";

    @JsonProperty("id")
    String id;

    @JsonProperty("version")
    int version = 1;

    @JsonProperty("datum")
    Date datum;

    @JsonProperty("beslutsfattare")
    String beslutsfattare;

    @JsonProperty("typ")
    Typ typ;

    @JsonProperty("utfall")
    Utfall utfall;

    @JsonProperty("organisation")
    String organisation;

    @JsonProperty("lagrum")
    Lagrum lagrum;

    public Beslut() {} // Required for deserialization

    public Beslut(Date datum) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();

        this.datum = datum;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Beslut{");
        sb.append("context='").append(context).append('\'');
        sb.append(", id='").append(id).append('\'');
        sb.append(", version=").append(version);
        sb.append(", typ='").append(typ).append('\'');
        sb.append(", datum='").append(datum.toString()).append('\'');
        sb.append(", beslutsfattare='").append(beslutsfattare).append('\'');
        sb.append(", typ='").append(typ.name()).append('\'');
        sb.append(", utfall='").append(utfall.name()).append('\'');
        sb.append(", organisation='").append(organisation).append('\'');
        sb.append(", lagrum='").append(lagrum.name()).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
