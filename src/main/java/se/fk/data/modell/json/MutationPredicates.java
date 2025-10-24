package se.fk.data.modell.json;

public final class MutationPredicates {

    /** Is this a class, for which we will track modifications? */
    public static boolean isLifeCycleHandled(Class<?> raw) {
        return raw != null && se.fk.data.modell.v1.LivscykelHanterad.class.isAssignableFrom(raw);
    }

    /*
    public static boolean isLifeCycleHandled(com.fasterxml.jackson.databind.JavaType t) {
        return t != null && isTrackedClass(t.getRawClass());
    }
    */

    /*
    public static boolean isSimpleLeaf(Class<?> raw) {
        return raw.isPrimitive()
                || Number.class.isAssignableFrom(raw)
                || CharSequence.class.isAssignableFrom(raw) // String, StringBuilder
                || java.util.Date.class.isAssignableFrom(raw)
                || java.time.temporal.Temporal.class.isAssignableFrom(raw)
                || Boolean.class == raw
                || Character.class == raw
                || Enum.class.isAssignableFrom(raw);
    }
    */
}
