package se.fk.data.modell.json;

import se.fk.data.modell.v1.Livscykelhanterad;

public final class MutationPredicates {

    /** Is this a class, for which we will track modifications? */
    public static boolean isLifeCycleHandled(Class<?> raw) {
        return raw != null && Livscykelhanterad.class.isAssignableFrom(raw);
    }
}
