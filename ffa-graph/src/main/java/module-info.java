/** Separat projektion av förvaltad JSON till Neo4j:s grafmodell. */
module se.fk.ffa.graph {
    requires tools.jackson.databind;
    exports se.fk.mimer.graph to se.fk.ffa.pipeline;
}
