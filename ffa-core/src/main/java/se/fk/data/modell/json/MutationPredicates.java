package se.fk.data.modell.json;

import se.fk.data.modell.v1.Livscykelhanterad;

/** Avgör vilka modellklasser som omfattas av den gemensamma livscykelhanteringen. */
public final class MutationPredicates {

    /** Har klassen den livscykelbas som den gemensamma infrastrukturen hanterar? */
    public static boolean isLifeCycleHandled(Class<?> raw) {
        return raw != null && Livscykelhanterad.class.isAssignableFrom(raw);
    }
}
