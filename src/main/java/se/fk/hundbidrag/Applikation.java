package se.fk.hundbidrag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import se.fk.data.modell.json.DeserializationSnooper;
import se.fk.data.modell.v1.*;
import se.fk.hundbidrag.modell.Kundbehov;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

import static se.fk.data.modell.json.Modifiers.getModules;


/**
 */
public class Applikation
{
    private final static Logger log = LogManager.getLogger(Applikation.class);

    public static void main( String[] args )
    {
        // Creating the object graph
        Ersattning ers1 = new Ersattning("Vård", 1000);
        Ersattning ers2 = new Ersattning("Utställning", 500);

        Kundbehov kundbehov = new Kundbehov("Behov", Arrays.asList(ers1, ers2), "Collie");

        Beslut beslut = new Beslut(Date.from(Instant.now()));
        kundbehov.setBeslut(beslut);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModules(getModules());
            mapper.addHandler(new DeserializationSnooper());

            // Serialize to JSON
            String jsonLD = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(kundbehov);
            log.debug("Object -> JSON:\n{}", jsonLD);

            // Deserialize from JSON
            Kundbehov deserializedKundbehov = mapper.readValue(jsonLD, Kundbehov.class);
            log.debug("JSON -> Object:\n{}", deserializedKundbehov);

      } catch (JsonProcessingException e) {
            log.error("Failed to run demo: {}", e.getMessage(), e);
        }
    }
}
