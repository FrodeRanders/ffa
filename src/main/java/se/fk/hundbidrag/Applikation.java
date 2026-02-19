package se.fk.hundbidrag;

import com.fasterxml.uuid.Generators;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.fk.data.modell.v1.*;
import se.fk.hundbidrag.modell.YrkandeOmHundbidrag;
import se.fk.mimer.klient.MimerProxy;
import tools.jackson.core.JacksonException;

import java.time.Instant;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;


/**
 */
public class Applikation {
    private final static Logger log = LogManager.getLogger(Applikation.class);

    private static String id() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    public static void main( String[] args )
    {
        // -------------------------------------------------------------------
        // Använd FFAs objektmodell för affärslogik i specifik förmånskontext
        // -------------------------------------------------------------------

        // Efter etablering av yrkande och i samband med initiering av handläggningsflöde
        YrkandeOmHundbidrag yrkande = new YrkandeOmHundbidrag("Hundutställning (inkl. bad)","Collie");
        {
            FysiskPerson person = new FysiskPerson("19121212-1212");

            yrkande.setPerson(person);
        }

        // Efter bedömning av rätten till...
        {
            RattenTillPeriod rattenTillPeriod = new RattenTillPeriod();
            rattenTillPeriod.omfattning = RattenTillPeriod.Omfattning.HEL;
            rattenTillPeriod.ersattningstyp = Ersattning.Typ.HUNDBIDRAG;

            yrkande.addProduceratResultat(rattenTillPeriod);
        }

        // Efter beräkning...
        {
            Ersattning ersattning = new Ersattning();
            ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
            ersattning.belopp = 1000.0;
            ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkande.addProduceratResultat(ersattning);
        }
        {
            Ersattning ersattning = new Ersattning();
            ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
            ersattning.belopp = 500.0;
            ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkande.addProduceratResultat(ersattning);
        }

        // I samband med beslut, så utfärdar vi ett "Hittepå"-intyg
        {
            Intyg intyg = new Intyg();
            intyg.beskrivning = "Hittepå";
            intyg.giltighetsperiod = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkande.addProduceratResultat(intyg);
        }
        {
            Beslut beslut = new Beslut();
            beslut.datum = Date.from(Instant.now().truncatedTo(DAYS));

            yrkande.setBeslut(beslut);
        }

        // -------------------------------------------------------------------
        // Medskickade utility-funktioner hanterar:
        //  - serialisering
        //  - lagring
        //  - återläsning
        // -------------------------------------------------------------------
        try {
            MimerProxy proxy = MimerProxy.defaultInstance();

            // Initial serialize to JSON
            String jsonLD = proxy.serializePretty(yrkande);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Subsequent deserialize from JSON
            YrkandeOmHundbidrag aaterlaestYrkande = proxy.deserialize(jsonLD, YrkandeOmHundbidrag.class);
            log.debug("JSON -> Object:\n{}", aaterlaestYrkande);

            // Modify deserialized objects (in order to exercise lifecycle handling/versioning)
            aaterlaestYrkande.beskrivning = "Hundutställning (inkl. bad och tork)";
            {
                Ersattning ersattning = new Ersattning();
                ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
                ersattning.belopp = 100.0;

                aaterlaestYrkande.addProduceratResultat(ersattning);
            }

            // Re-serialize to JSON
            jsonLD = proxy.serializePretty(aaterlaestYrkande);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Re-modify, operating on same objects (no serializing+deserializing involved)
            aaterlaestYrkande.beskrivning = "Hundutställning (inkl. bad, tork och fön)";
            {
                Ersattning ersattning = new Ersattning();
                ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
                ersattning.belopp = 200.0;

                aaterlaestYrkande.addProduceratResultat(ersattning);
            }

            // Re-re-serialize to JSON
            jsonLD = proxy.serializePretty(aaterlaestYrkande);
            log.debug("Object -> JSON:\n{}", jsonLD);

      } catch (JacksonException e) {
            log.error("Failed to run demo: {}", e.getMessage(), e);
        }
    }
}
