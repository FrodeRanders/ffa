package se.fk.mimer.runtime;

import se.fk.data.modell.v1.*;

import java.util.HashSet;
import java.util.Set;

/** Gemensamma strukturkrav. Förmånsregler, exempelvis beloppets storlek, hör till förmånen. */
final class Modellvalidering {
    private Modellvalidering() {}

    static void kontrollera(Yrkande yrkande) {
        if (yrkande == null)
            throw new IllegalArgumentException("Yrkande saknas");

        // Identiteter ska vara unika inom hela yrkandet, inklusive dess livscykelobjekt.
        Set<String> ids = new HashSet<>();
        kontrolleraIdentitet(yrkande, ids);
        if (yrkande.person == null)
            throw new IllegalArgumentException("Yrkandets person saknas");
        if (yrkande.produceratResultat == null)
            throw new IllegalArgumentException("Samlingen producerat resultat saknas");

        // Ett yrkande får vara under handläggning och därför ännu sakna beslut.
        if (yrkande.beslut != null) {
            kontrolleraIdentitet(yrkande.beslut, ids);
            if (yrkande.beslut.datum == null || yrkande.beslut.typ == null || yrkande.beslut.utfall == null) {
                throw new IllegalArgumentException("Beslut måste ha datum, typ och utfall");
            }
        }

        // Kontrollera representationens struktur. Hur beloppet beräknas är en förmånsregel.
        for (ProduceratResultat result : yrkande.produceratResultat) {
            kontrolleraIdentitet(result, ids);
            if (result instanceof Ersattning e) {
                if (e.typ == null || !Double.isFinite(e.belopp)) {
                    throw new IllegalArgumentException("Ersättning måste ha typ och ett ändligt belopp");
                }
                if (e.period != null && (e.period.from == null || e.period.tom == null || e.period.from.after(e.period.tom))) {
                    throw new IllegalArgumentException("Ersättningens period är ogiltig");
                }
            }
        }
    }

    private static void kontrolleraIdentitet(Livscykelhanterad entity, Set<String> ids) {
        if (entity == null || entity.getId() == null || entity.getId().isBlank() || entity.getVersion() < 0) {
            throw new IllegalArgumentException("Objektets identitet eller version är ogiltig");
        }
        if (!ids.add(entity.getId()))
            throw new IllegalArgumentException("Dubblerad objektidentitet: " + entity.getId());
    }
}
