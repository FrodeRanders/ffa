package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotationMap;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.Belopp;
import se.fk.data.modell.ffa.PII;

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
                AnnotationMap annotations = member.getAllAnnotations();
                if (annotations != null) {
                    //------------------------------------------------
                    // These are all the known property annotations,
                    // known at design-time (i.e. right now)
                    //------------------------------------------------
                    PII pii = annotations.get(PII.class);
                    if (null != pii) {
                        log.trace("@PII property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                        writer.assignSerializer(
                                new PIIPropertySerializer(
                                        pii.typ()
                                )
                        );
                    } else {
                        Belopp belopp = annotations.get(Belopp.class);
                        if (null != belopp) {
                            log.trace("@Belopp property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                            writer.assignSerializer(
                                    new BeloppPropertySerializer(
                                            belopp.valuta(), belopp.skattestatus(), belopp.period()
                                    )
                            );
                        }
                    }
                }
            }
        }
        return writers;
    }
}