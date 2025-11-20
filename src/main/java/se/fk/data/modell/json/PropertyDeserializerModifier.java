package se.fk.data.modell.json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.annotations.Belopp;
import se.fk.data.modell.annotations.PII;
import se.fk.data.modell.annotations.Som;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.deser.BeanDeserializerBuilder;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.AnnotationMap;

import java.util.Iterator;

public class PropertyDeserializerModifier extends ValueDeserializerModifier {
    private static final Logger log = LoggerFactory.getLogger(PropertyDeserializerModifier.class);

    @Override
    public BeanDeserializerBuilder updateBuilder(
            DeserializationConfig config,
            BeanDescription.Supplier beanDesc,
            BeanDeserializerBuilder builder
    ) {
        Iterator<SettableBeanProperty> it = builder.getProperties();
        while (it.hasNext()) {
            SettableBeanProperty prop = it.next();
            AnnotatedMember member = prop.getMember();
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
                        PIIPropertyDeserializer des = new PIIPropertyDeserializer();
                        prop = prop.withValueDeserializer(des);
                        builder.addOrReplaceProperty(prop, true);
                        return builder;
                    }

                    Som som = annotations.get(Som.class);
                    if (null != som) {
                        log.trace("@Som property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                        SomPropertyDeserializer des = new SomPropertyDeserializer();
                        prop = prop.withValueDeserializer(des);
                        builder.addOrReplaceProperty(prop, true);
                        return builder;
                    }

                    Belopp belopp = annotations.get(Belopp.class);
                    if (null != belopp) {
                        log.trace("@Belopp property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                        BeloppPropertyDeserializer des = new BeloppPropertyDeserializer();
                        prop = prop.withValueDeserializer(des);
                        builder.addOrReplaceProperty(prop, true);
                        return builder;
                    }
                }
            }
        }
        return builder;
    }
}
