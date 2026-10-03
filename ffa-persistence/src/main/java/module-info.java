/** Leverans till masterdataflödet och lokal cache för förmånsprocesser. */
module se.fk.ffa.persistence {
    requires se.fk.ffa.core;
    requires java.sql;
    requires java.net.http;
    requires tools.jackson.databind;
    requires org.postgresql.jdbc;
    requires kafka.clients;
    exports se.fk.mimer.persistence to se.fk.ffa.demo, se.fk.ffa.pipeline;
}
