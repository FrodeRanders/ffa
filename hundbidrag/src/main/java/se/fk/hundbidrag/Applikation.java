package se.fk.hundbidrag;

import se.fk.data.modell.v1.*;
import se.fk.hundbidrag.modell.YrkandeOmHundbidrag;
import se.fk.mimer.api.Yrkanden;

import java.time.Instant;

import java.util.Date;

/** Ett litet förmånsexempel. All datahantering ligger bakom Yrkanden. */
public final class Applikation {
    private final Yrkanden<YrkandeOmHundbidrag> yrkanden;

    public Applikation(Yrkanden<YrkandeOmHundbidrag> yrkanden) {
        this.yrkanden = yrkanden;
    }

    /** Visar en första handläggning och en senare ändring med samma objekt-API. */
    public YrkandeOmHundbidrag handlagg() {
        YrkandeOmHundbidrag yrkande = new YrkandeOmHundbidrag("Hundutställning", "Collie");
        yrkande.setPerson(new FysiskPerson("19121212-1212"));
        yrkande.addProduceratResultat(beraknaErsattning(1000.0));
        yrkande.setBeslut(fattaBeslut());

        // Fortsätt med det returnerade tillståndet, vars versioner den gemensamma gränsen äger.
        yrkande = yrkanden.lagra(yrkande);

        // Fortsatt handläggning efter återläsning: ändra det befintliga resultatet.
        yrkande = yrkanden.las(yrkande.getId());
        Ersattning ersattning = (Ersattning) yrkande.produceratResultat.iterator().next();
        ersattning.belopp = 1200.0;
        yrkande.beskrivning = "Hundutställning inklusive bad";

        return yrkanden.lagra(yrkande);
    }

    // Fasta belopp och datum gör exemplet reproducerbart; verklig beräkning hör hemma här.
    private Ersattning beraknaErsattning(double belopp) {
        Ersattning ersattning = new Ersattning();
        ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
        ersattning.belopp = belopp;
        ersattning.period = new Period(Date.from(Instant.parse("2026-01-01T00:00:00Z")));
        return ersattning;
    }

    private Beslut fattaBeslut() {
        Beslut beslut = new Beslut();
        beslut.datum = Date.from(Instant.parse("2026-01-01T00:00:00Z"));
        beslut.typ = Beslut.Typ.SLUTLIGT;
        beslut.utfall = Beslut.Utfall.BEVILJAT;
        beslut.beslutsfattare = "Demo";
        beslut.organisation = "Försäkringskassan";
        return beslut;
    }
}
