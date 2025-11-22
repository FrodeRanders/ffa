package se.fk.hundbidrag;

import tools.jackson.core.JacksonException;
import com.fasterxml.uuid.Generators;
import se.fk.data.modell.json.DeserializationSnooper;
import se.fk.data.modell.v1.*;
import se.fk.hundbidrag.modell.Yrkan;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;
import static se.fk.data.modell.json.Modifiers.getModules;


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
        /* Yrkan */ Yrkan yrkan = new Yrkan("Hundutställning (inkl. bad)","Collie");
        {
            FysiskPerson person = new FysiskPerson("19121212-1212");

            yrkan.setPerson(person);
        }

        // Efter bedömning av rätten till...
        {
            RattenTillPeriod rattenTillPeriod = new RattenTillPeriod();
            rattenTillPeriod.omfattning = RattenTillPeriod.Omfattning.HEL;
            rattenTillPeriod.ersattningstyp = Ersattning.Typ.HUNDBIDRAG;

            yrkan.addProduceradeResultat(rattenTillPeriod);
        }

        // Efter beräkning...
        {
            Ersattning ersattning = new Ersattning();
            ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
            ersattning.belopp = 1000.0;
            ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkan.addProduceradeResultat(ersattning);
        }
        {
            Ersattning ersattning = new Ersattning();
            ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
            ersattning.belopp = 500.0;
            ersattning.period = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkan.addProduceradeResultat(ersattning);
        }

        // I samband med beslut, så utfärdar vi ett "Hittepå"-intyg
        {
            Intyg intyg = new Intyg();
            intyg.beskrivning = "Hittepå";
            intyg.giltighetsperiod = new Period(Date.from(Instant.now().truncatedTo(DAYS)));

            yrkan.addProduceradeResultat(intyg);
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
            JsonMapper mapper = JsonMapper.builder()
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .addModules(getModules())
                    .addHandler(new DeserializationSnooper())
                    .build();

            // Initial serialize to JSON
            String jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(yrkan);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Subsequent deserialize from JSON
            Yrkan aaterlaestYrkan = mapper.readValue(jsonLD, Yrkan.class);
            log.debug("JSON -> Object:\n{}", aaterlaestYrkan);

            // Modify deserialized objects (in order to exercise lifecycle handling/versioning)
            aaterlaestYrkan.beskrivning = "Hundutställning (inkl. bad och tork)";
            {
                Ersattning ersattning = new Ersattning();
                ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
                ersattning.belopp = 100.0;

                aaterlaestYrkan.addProduceradeResultat(ersattning);
            }

            // Re-serialize to JSON
            jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(aaterlaestYrkan);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Re-modify, operating on same objects (no serializing+deserializing involved)
            aaterlaestYrkan.beskrivning = "Hundutställning (inkl. bad, tork och fön)";
            {
                Ersattning ersattning = new Ersattning();
                ersattning.typ = Ersattning.Typ.HUNDBIDRAG;
                ersattning.belopp = 200.0;

                aaterlaestYrkan.addProduceradeResultat(ersattning);
            }

            // Re-re-serialize to JSON
            jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(aaterlaestYrkan);
            log.debug("Object -> JSON:\n{}", jsonLD);

      } catch (JacksonException e) {
            log.error("Failed to run demo: {}", e.getMessage(), e);
        }
    }
}
