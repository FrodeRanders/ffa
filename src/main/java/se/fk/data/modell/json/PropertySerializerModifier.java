package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.Belopp;

import java.util.ArrayList;
import java.util.List;

public class PropertySerializerModifier extends BeanSerializerModifier {
    private static final Logger log = LoggerFactory.getLogger(PropertySerializerModifier.class);

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
                // TODO Currently hardcoded for @Belopp, should ofcourse be handled dynamically
                Belopp annotation = member.getAnnotation(Belopp.class);
                if (null != annotation) {
                    log.trace("Annotating property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                    writer.assignSerializer(
                        new PropertySerializer(
                                annotation.valuta(), annotation.skattestatus(), annotation.period()
                        )
                    );
                }
            }
        }
        return writers;
    }
}