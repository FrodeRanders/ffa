package se.fk.data.modell.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A type-level annotation that indicates we want a
 * "@context" property for this object.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Context {
    String value() default "";
}
