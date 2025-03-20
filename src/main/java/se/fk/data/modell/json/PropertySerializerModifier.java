package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
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
        List<BeanPropertyWriter> newProps = new ArrayList<>(beanProperties);

        for (BeanPropertyWriter bpw : newProps) {
            AnnotatedMember member = bpw.getMember();

            if (member != null) {
                Valuta annotation = member.getAnnotation(Valuta.class);
                if (null != annotation) {
                    bpw.assignSerializer(new PropertySerializer());
                }
                else {
                    // The field not annotated with @Valuta, so revert to default
                    bpw.assignSerializer(null);
                    bpw.assignNullSerializer(null);
                }
            }
        }
        return newProps;
    }
}