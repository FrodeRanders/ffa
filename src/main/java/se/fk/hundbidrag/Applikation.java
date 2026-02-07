package se.fk.hundbidrag;

import com.fasterxml.uuid.Generators;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import se.fk.data.modell.v1.*;
import se.fk.hundbidrag.modell.YrkanOmHundbidrag;
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

        // Efter etablering av yrkan och i samband med initiering av handläggningsflöde
        YrkanOmHundbidrag yrkan = new YrkanOmHundbidrag("Hundutställning (inkl. bad)","Collie");
        {
            FysiskPerson person = new FysiskPerson("19121212-1212");

            yrkan.setPerson(person);
        }

        // Efter bedömning av rätten till...
        {
            RattenTillPeriod rattenTillPeriod = new RattenTillPeriod();
            rattenTillPeriod.omfattning = RattenTillPeriod.Omfattning.HEL;
            rattenTillPeriod.ersattningstyp = Ersattning.Typ.HUNDBIDRAG;

            yrkan.addProduceratResultat(rattenTillPeriod);
        }

        // Efter beräkning...
        {
            Ersattning ersattning = new Ersattning();
            ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
            ersattning.belopp = 1000.0;
            ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkan.addProduceratResultat(ersattning);
        }
        {
            Ersattning ersattning = new Ersattning();
            ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
            ersattning.belopp = 500.0;
            ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkan.addProduceratResultat(ersattning);
        }

        // I samband med beslut, så utfärdar vi ett "Hittepå"-intyg
        {
            Intyg intyg = new Intyg();
            intyg.beskrivning = "Hittepå";
            intyg.giltighetsperiod = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkan.addProduceratResultat(intyg);
        }
        {
            Beslut beslut = new Beslut();
            beslut.datum = Date.from(Instant.now().truncatedTo(DAYS));

            yrkan.setBeslut(beslut);
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
            String jsonLD = proxy.serializePretty(yrkan);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Subsequent deserialize from JSON
            YrkanOmHundbidrag aaterlaestYrkan = proxy.deserialize(jsonLD, YrkanOmHundbidrag.class);
            log.debug("JSON -> Object:\n{}", aaterlaestYrkan);

            // Modify deserialized objects (in order to exercise lifecycle handling/versioning)
            aaterlaestYrkan.beskrivning = "Hundutställning (inkl. bad och tork)";
            {
                Ersattning ersattning = new Ersattning();
                ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
                ersattning.belopp = 100.0;

                aaterlaestYrkan.addProduceratResultat(ersattning);
            }

            // Re-serialize to JSON
            jsonLD = proxy.serializePretty(aaterlaestYrkan);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Re-modify, operating on same objects (no serializing+deserializing involved)
            aaterlaestYrkan.beskrivning = "Hundutställning (inkl. bad, tork och fön)";
            {
                Ersattning ersattning = new Ersattning();
                ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
                ersattning.belopp = 200.0;

                aaterlaestYrkan.addProduceratResultat(ersattning);
            }

            // Re-re-serialize to JSON
            jsonLD = proxy.serializePretty(aaterlaestYrkan);
            log.debug("Object -> JSON:\n{}", jsonLD);

      } catch (JacksonException e) {
            log.error("Failed to run demo: {}", e.getMessage(), e);
        }
    }
}
