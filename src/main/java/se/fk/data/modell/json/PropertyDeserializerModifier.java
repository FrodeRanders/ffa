package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.ffa.Belopp;

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
                // TODO Currently hardcoded for @Belopp, should ofcourse be handled dynamically
                Belopp annotation = member.getAnnotation(Belopp.class);
                if (null != annotation) {
                    log.trace("Handling annotated property {}#{}", beanDesc.getBeanClass().getCanonicalName(), member.getName());
                    PropertyDeserializer des = new PropertyDeserializer();
                    prop = prop.withValueDeserializer(des);
                    //prop = prop.withNullProvider(des);
                    builder.addOrReplaceProperty(prop, true);
                }
                /*
                else {
                    SettableBeanProperty originalProp = builder.findProperty(prop.getFullName());
                    if (originalProp != null) {
                        prop = prop.withValueDeserializer(originalProp.getValueDeserializer());
                        prop = prop.withNullProvider(originalProp.getNullValueProvider());
                        builder.addOrReplaceProperty(prop, true);
                    }
                }
                */
            }
        }
        return builder;
    }
}
