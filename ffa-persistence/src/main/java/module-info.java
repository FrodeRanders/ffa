/** Leverans till masterdataflödet och lokal cache för förmånsprocesser. */
module se.fk.ffa.persistence {
    requires se.fk.ffa.core;
    requires java.sql;
    requires org.postgresql.jdbc;
    requires kafka.clients;
    exports se.fk.mimer.persistence to se.fk.ffa.demo;
}
