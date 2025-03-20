package se.fk.data.modell.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import se.fk.data.modell.ffa.Valuta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
                    prop = prop.withNullProvider(des);
                    builder.addOrReplaceProperty(prop, true);
                }
                else {
                    // The field not annotated with @Valuta, so revert to default
                    //prop = prop.withValueDeserializer(null);
                    //prop = prop.withNullProvider(null);
                    //builder.addOrReplaceProperty(prop, true);
                }
            }
        }
        return builder;
    }
}
