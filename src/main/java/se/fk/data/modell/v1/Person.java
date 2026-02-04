package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import se.fk.data.modell.adapters.PersonTypeIdResolver;
import tools.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * Notera att Person inte är ett Livscykelhanterat objekt (hos oss).
 * Livscykelhanteringen av Person sköts om 'naturligt', så att säga,
 * så vi vare sig kan eller vill operera med 'version' här.
 *
 * Person-objekt används som en referens (främmande nyckel om ni vill),
 * så syftet är att kapsla in en ID som gör att vi förstår vilken person
 * eller vilket företag vi förhåller oss till.
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
