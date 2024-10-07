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

        Kundbehov kundbehov = new Kundbehov("Insurance needs", Arrays.asList(ers1, ers2));

        // Creating ObjectMapper
        ObjectMapper mapper = new ObjectMapper();

        // Serialize to JSON-LD
        try {
            String jsonLD = mapper.writeValueAsString(kundbehov);
            System.out.println("Serialized JSON-LD:\n" + jsonLD);

            /*
                Serialized JSON-LD:
                
                {
                    "@context": "https://example.org/contexts/kundbehov",
                    "id": "0192686f-c1b0-7f6a-8e87-cc9a568cee78",
                    "version": 1,
                    "description": "Insurance needs",
                    "ersattningar": [
                        {
                            "@context": "https://example.org/contexts/ersattning",
                            "id": "0192686f-c1af-785d-a2bf-9828040ef812",
                            "version": 1,
                            "type": "Health",
                            "amount": 1000
                        },
                        {
                            "@context": "https://example.org/contexts/ersattning",
                            "id": "0192686f-c1b0-75a2-8a7c-871f583dba15",
                            "version": 1,
                            "type": "Travel",
                            "amount": 500
                        }
                    ]
                }
            */

            // Deserialize from JSON
            Kundbehov deserializedKundbehov = mapper.readValue(jsonLD, Kundbehov.class);
            System.out.println("\n\nDeserialized Object:\n" + deserializedKundbehov);

            /*
                Deserialized Object:

                Kundbehov{
                    context='https://example.org/contexts/kundbehov',
                    id='0192686f-c1b0-7f6a-8e87-cc9a568cee78',
                    version=1,
                    description='Insurance needs',
                    ersattningar=[
                        Ersattning{
                            context='https://example.org/contexts/ersattning',
                            id='0192686f-c1af-785d-a2bf-9828040ef812',
                            version=1,
                            type='Health',
                            amount=1000.0
                        },
                        Ersattning{
                            context='https://example.org/contexts/ersattning',
                            id='0192686f-c1b0-75a2-8a7c-871f583dba15',
                            version=1,
                            type='Travel',
                            amount=500.0
                        }
                    ]
                }
             */
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
