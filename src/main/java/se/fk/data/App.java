package se.fk.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import se.fk.data.modell.Ersattning;
import se.fk.data.modell.Kundbehov;

import java.util.Arrays;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        // Creating the object graph
        Ersattning ers1 = new Ersattning("Health", 1000);
        Ersattning ers2 = new Ersattning("Travel", 500);

        Kundbehov kundbehov = new Kundbehov(1, "Insurance needs", Arrays.asList(ers1, ers2));

        // Creating ObjectMapper
        ObjectMapper mapper = new ObjectMapper();

        // Serialize to JSON-LD
        try {
            String jsonLD = mapper.writeValueAsString(kundbehov);
            System.out.println("Serialized JSON-LD:\n" + jsonLD);

            /*
            {
                "@context": "https://example.org/contexts/kundbehov",
                "id": 1,
                "description": "Insurance needs",
                "ersattningar": [
                    {
                        "@context": "https://example.org/contexts/ersattning",
                        "type": "Health",
                        "amount": 1000
                    },
                    {
                        "@context": "https://example.org/contexts/ersattning",
                        "type": "Travel",
                        "amount": 500
                    }
                ]
            }
            */

            // Deserialize from JSON
            Kundbehov deserializedKundbehov = mapper.readValue(jsonLD, Kundbehov.class);
            //System.out.println("Deserialized Object: " + deserializedKundbehov);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
