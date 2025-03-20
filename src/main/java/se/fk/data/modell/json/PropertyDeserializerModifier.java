package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import se.fk.data.modell.ffa.Valuta;

import java.util.Iterator;

public class PropertyDeserializerModifier extends BeanDeserializerModifier {

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
                Valuta annotation = member.getAnnotation(Valuta.class);
                if (null != annotation) {
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
