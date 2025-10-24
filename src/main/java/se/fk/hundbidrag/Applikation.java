package se.fk.hundbidrag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import se.fk.data.modell.json.DeserializationSnooper;
import se.fk.data.modell.v1.*;
import se.fk.hundbidrag.modell.Kundbehov;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

import static java.time.temporal.ChronoUnit.DAYS;
import static se.fk.data.modell.json.Modifiers.getModules;


/**
 */
public class Applikation {
    private final static Logger log = LogManager.getLogger(Applikation.class);

    public static void main( String[] args )
    {
        // -------------------------------------------------------------------
        // Använd FFAs objektmodell för affärslogik i specifik förmånskontext
        // -------------------------------------------------------------------
        Ersattning ers1 = new Ersattning("Avgift", 1000);
        Ersattning ers2 = new Ersattning("Bad", 500);

        Kundbehov kundbehov = new Kundbehov("Hundutställning", Arrays.asList(ers1, ers2), "Collie");

        Beslut beslut = new Beslut(Date.from(Instant.now().truncatedTo(DAYS)));
        kundbehov.setBeslut(beslut);

        // -------------------------------------------------------------------
        // Medskickade utility-funktioner hanterar:
        //  - serialisering
        //  - lagring
        //  - återläsning
        // -------------------------------------------------------------------
        try {
            ObjectMapper mapper = new ObjectMapper()
                // Date-relaterat
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setDateFormat(new StdDateFormat().withColonInTimeZone(true))
                .setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                //
                .registerModules(getModules())
                .addHandler(new DeserializationSnooper());

            // Initial serialize to JSON
            String jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(kundbehov);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Subsequent deserialize from JSON
            Kundbehov deserializedKundbehov = mapper.readValue(jsonLD, Kundbehov.class);
            log.debug("JSON -> Object:\n{}", deserializedKundbehov);

            // Modify deserialized objects (in order to exercise lifecycle handling/versioning)
            deserializedKundbehov.beskrivning = "Modifierad beskrivning";
            deserializedKundbehov.ersattningar.add(new Ersattning("Tork", 100));

            // Re-serialize to JSON
            jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedKundbehov);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Re-modify, operating on same objects (no serializing+deserializing involved)
            deserializedKundbehov.beskrivning = "Modfierad igen...";
            deserializedKundbehov.ersattningar.add(new Ersattning("Fön", 200));

            // Re-re-serialize to JSON
            jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(deserializedKundbehov);
            log.debug("Object -> JSON:\n{}", jsonLD);

      } catch (JsonProcessingException e) {
            log.error("Failed to run demo: {}", e.getMessage(), e);
        }
    }
}
