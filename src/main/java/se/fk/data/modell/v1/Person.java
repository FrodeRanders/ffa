package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import se.fk.data.modell.adapters.PersonTypeIdResolver;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.CUSTOM,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "@context",
        visible = true
)
@JsonTypeIdResolver(PersonTypeIdResolver.class)
public abstract class Person {
    public Person() {} // Required for deserialization
}
