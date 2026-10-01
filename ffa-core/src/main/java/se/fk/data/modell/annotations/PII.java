package se.fk.data.modell.annotations;

import java.lang.annotation.Retention;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Klassificerar ett personrelaterat värde i representationen; ger ingen kryptering eller maskering. */
@Retention(RUNTIME)
@Target(FIELD)
public @interface PII {
    String typ() default "";
}
