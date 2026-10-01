/** Förmånslogik: endast FFA:s offentliga modell och objekt-API används. */
module se.fk.hundbidrag {
    requires se.fk.ffa.core;
    exports se.fk.hundbidrag;
    exports se.fk.hundbidrag.modell;
    opens se.fk.hundbidrag.modell to tools.jackson.databind;
}
