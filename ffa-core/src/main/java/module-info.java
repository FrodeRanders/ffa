/** FFA:s objektmodell och förvaltade gräns för datahantering. */
module se.fk.ffa.core {
    requires tools.jackson.databind;
    requires json.path;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.uuid;
    requires org.slf4j;
    requires titanium.jcs;
    requires tree.io.api;

    exports se.fk.data.modell.v1;
    exports se.fk.data.modell.annotations;
    exports se.fk.mimer.api;

    // Endast uppstart/infrastruktur får konfigurera lagring och nycklar.
    exports se.fk.mimer.runtime to se.fk.ffa.demo, se.fk.ffa.persistence, se.fk.ffa.pipeline;
    exports se.fk.data.modell.utils to se.fk.ffa.demo, se.fk.ffa.pipeline;
    opens se.fk.data.modell.adapters to tools.jackson.databind;
    opens se.fk.data.modell.json to tools.jackson.databind;
    opens se.fk.data.modell.v1 to tools.jackson.databind;
    opens se.fk.data.modell.internal to tools.jackson.databind;
}
