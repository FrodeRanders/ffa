package se.fk.data.modell.json;

import se.fk.data.modell.ffa.Som;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotationMap;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.Belopp;
import se.fk.data.modell.ffa.PII;

import java.util.ArrayList;
import java.util.List;

public class PropertySerializerModifier extends ValueSerializerModifier {
    private static final Logger log = LoggerFactory.getLogger(PropertySerializerModifier.class);

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription.Supplier beanDesc,
            List<BeanPropertyWriter> beanProperties
    ) {
        List<BeanPropertyWriter> writers = new ArrayList<>(beanProperties);

        for (BeanPropertyWriter writer : writers) {
            AnnotatedMember member = writer.getMember();

            if (member != null) {
                AnnotationMap annotations = member._annotationMap();
                if (annotations != null) {
                    //------------------------------------------------
                    // These are all the known property annotations,
                    // known at design-time (i.e. right now)
                    //------------------------------------------------
                    PII pii = annotations.get(PII.class);
                    if (null != pii) {
                        log.trace("@PII property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                        writer.assignSerializer(
                                new PiiPropertySerializer(
                                        pii.typ()
                                )
                        );
                        return writers;
                    }

                    Som som = annotations.get(Som.class);
                    if (null != som) {
                        log.trace("@Som property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                        writer.assignSerializer(
                                new SomPropertySerializer(
                                        som.typ()
                                )
                        );
                        return writers;
                    }

                    Belopp belopp = annotations.get(Belopp.class);
                    if (null != belopp) {
                        log.trace("@Belopp property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                        writer.assignSerializer(
                                new BeloppPropertySerializer(
                                        belopp.valuta(), belopp.skattestatus(), belopp.period()
                                )
                        );
                        return writers;
                    }
                }
            }
        }
        return writers;
    }
}
