/** Separat infrastrukturdemonstration av N−1 och N; inga beroenden från förmånens verksamhetskod. */
module se.fk.ffa.pipeline {
    requires se.fk.ffa.core;
    requires se.fk.ffa.persistence;
    requires se.fk.ffa.graph;
    requires se.fk.hundbidrag;
    requires kafka.clients;
    requires org.postgresql.jdbc;
    requires java.sql;
    requires jdk.httpserver;
    requires tools.jackson.databind;
    requires software.amazon.awssdk.services.s3;
    requires software.amazon.awssdk.core;
    requires software.amazon.awssdk.awscore;
    requires software.amazon.awssdk.auth;
    requires software.amazon.awssdk.regions;
    requires software.amazon.awssdk.http;
    requires software.amazon.awssdk.http.urlconnection;
    requires org.neo4j.driver;
}
