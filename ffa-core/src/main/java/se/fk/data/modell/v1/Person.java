package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import se.fk.data.modell.adapters.PersonTypeIdResolver;
import tools.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * Referens till en fysisk eller juridisk person som hör till ett yrkande.
 * Personreferensen har ingen egen livscykelversion här; ändringar ingår i ägarobjektets tillstånd.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CUSTOM,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "@type",
        visible = true
)
@JsonTypeIdResolver(PersonTypeIdResolver.class)
public abstract class Person {
}
