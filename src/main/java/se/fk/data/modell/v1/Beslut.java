package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;
import se.fk.data.modell.ffa.Context;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@Context("https://data.fk.se/kontext/std/beslut/1.0")
public class Beslut extends LivscykelHanterad {
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

    @JsonProperty("datum")
    public Date datum;

    @JsonProperty("beslutsfattare")
    public String beslutsfattare;

    @JsonProperty("typ")
    public Typ typ;

    @JsonProperty("utfall")
    public Utfall utfall;

    @JsonProperty("organisation")
    public String organisation;

    @JsonProperty("lagrum")
    public Lagrum lagrum;

    public Beslut() {} // Required for deserialization

    public Beslut(Date datum) {
        UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
        this.id = uuid.toString();

        this.datum = datum;
    }

    @Override
    public String toString() {
        Locale locale = Locale.forLanguageTag("sv");
        DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, locale);

        StringBuilder sb = new StringBuilder("Beslut{");
        sb.append(super.toString());
        sb.append(", datum='").append(df.format(datum)).append('\'');
        sb.append(", beslutsfattare=");
        if (null != beslutsfattare) {
            sb.append('\'').append(beslutsfattare).append('\'');
        }
        sb.append(", typ=");
        if (null != typ)
        {
            sb.append('\'').append(typ.name()).append('\'');
        }
        sb.append(", utfall=");
        if (null != utfall) {
            sb.append('\'').append(utfall.name()).append('\'');
        }
        sb.append(", organisation=");
        if (null != organisation) {
            sb.append('\'').append(organisation).append('\'');
        }
        sb.append(", lagrum=");
        if (null != lagrum) {
            sb.append('\'').append(lagrum.name()).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }
}
