package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotationMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.Belopp;
import se.fk.data.modell.annotations.PII;

import java.util.Iterator;

public class PropertyDeserializerModifier extends BeanDeserializerModifier {
    private static final Logger log = LoggerFactory.getLogger(PropertyDeserializerModifier.class);

    @Override
    public BeanDeserializerBuilder updateBuilder(
            DeserializationConfig config,
            BeanDescription beanDesc,
            BeanDeserializerBuilder builder
    ) {
        Iterator<SettableBeanProperty> it = builder.getProperties();
        while (it.hasNext()) {
            SettableBeanProperty prop = it.next();
            AnnotatedMember member = prop.getMember();
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
                        PIIPropertyDeserializer des = new PIIPropertyDeserializer();
                        prop = prop.withValueDeserializer(des);
                        //prop = prop.withNullProvider(des);
                        builder.addOrReplaceProperty(prop, true);
                    } else {
                        Belopp belopp = annotations.get(Belopp.class);
                        if (null != belopp) {
                            log.trace("@Belopp property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                            BeloppPropertyDeserializer des = new BeloppPropertyDeserializer();
                            prop = prop.withValueDeserializer(des);
                            //prop = prop.withNullProvider(des);
                            builder.addOrReplaceProperty(prop, true);
                        }
                    }
                }
            }
        }
        return builder;
    }
}
