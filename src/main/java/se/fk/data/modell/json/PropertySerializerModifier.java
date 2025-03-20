package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import se.fk.data.modell.ffa.Valuta;

import java.util.ArrayList;
import java.util.List;

public class PropertySerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties
    ) {
        List<BeanPropertyWriter> writers = new ArrayList<>(beanProperties);

        for (BeanPropertyWriter writer : writers) {
            AnnotatedMember member = writer.getMember();

            if (member != null) {
                Valuta annotation = member.getAnnotation(Valuta.class);
                if (null != annotation) {
                    writer.assignSerializer(new PropertySerializer());
                }
            }
        }
        return writers;
    }
}