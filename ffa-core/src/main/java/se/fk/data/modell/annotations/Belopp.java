package se.fk.data.modell.annotations;

import java.lang.annotation.Retention;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Anger beloppets valuta, skattestatus och enhetsperiod i den förvaltade representationen. */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Belopp {
    String valuta() default "";
    String skattestatus() default "";
    String period() default "";
}
