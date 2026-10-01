package se.fk.data.modell.annotations;

import java.lang.annotation.Retention;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Anger modellklassens JSON-LD-kontext som skrivs i transportens @context-fält. */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Context {
    String value() default "";
}
