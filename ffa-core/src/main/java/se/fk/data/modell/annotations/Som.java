package se.fk.data.modell.annotations;

import java.lang.annotation.Retention;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Anger vilken organisatorisk roll ett värde har i sitt ägarobjekt. */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Som {
    String roll() default "";
}
