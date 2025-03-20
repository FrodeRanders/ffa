package se.fk.data.modell.ffa;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(FIELD)
public @interface Valuta {
    String valuta() default "";
    String skattestatus() default "";
    String period() default "";
}
