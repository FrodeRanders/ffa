package se.fk.data.modell.internal;

import se.fk.data.modell.v1.Yrkande;

/** Flyttar enbart intern metadata till en fristående arbetskopia inför lagring. */
public final class LifecycleCopies {
    private LifecycleCopies() {}

    /**
     * Förutsätter en redan validerad och strukturellt identisk verksamhetskopia.
     * Resultaten paras ihop i samlingens ordning; egna livscykelobjekt i utvidgningar
     * behöver en motsvarande regel här.
     */
    public static void copy(Yrkande source, Yrkande target) {
        ((LifecycleState) target).copyLifecycleFrom(source);
        if (source.beslut != null) {
            ((LifecycleState) target.beslut).copyLifecycleFrom(source.beslut);
        }

        var originals = source.produceratResultat.iterator();
        for (var result : target.produceratResultat) {
            ((LifecycleState) result).copyLifecycleFrom(originals.next());
        }
    }
}
