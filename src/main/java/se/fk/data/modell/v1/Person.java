package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import se.fk.data.modell.adapters.PersonTypeIdResolver;
import tools.jackson.databind.annotation.JsonTypeIdResolver;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.CUSTOM,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "@type",
        visible = true
)
@JsonTypeIdResolver(PersonTypeIdResolver.class)
public abstract class Person {
    //public Person() {} // Required for deserialization
}
