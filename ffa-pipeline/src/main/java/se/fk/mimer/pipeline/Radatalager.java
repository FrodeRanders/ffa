package se.fk.mimer.pipeline;

import org.postgresql.ds.PGSimpleDataSource;
import se.fk.mimer.runtime.Dataleverans;
import se.fk.mimer.runtime.Dokumentkalla;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.util.*;

/** N−1: objekt först, sedan indexcommit. Positiv läsning kräver rätt data i båda lagren. */
public final class Radatalager implements Dokumentkalla {
    private final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    private final Objektlager objects;
    private final String topic;
    private final PublicKey key;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public Radatalager(String url, String user, String password, String topic, Objektlager objects, PublicKey key) {
        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        dataSource.setConnectTimeout(5);
        this.topic = Objects.requireNonNull(topic);
        this.objects = Objects.requireNonNull(objects);
        this.key = Objects.requireNonNull(key);
    }

    public void initiera() {
        objects.initiera();
        try (var input = getClass().getResourceAsStream("/backend.sql");
             var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("Rådatalagret kunde inte initieras", e); }
    }

    public void lagra(Dataleverans d) {
        Leveransformat.verifiera(d, key);
        // Gemensam första bindningspunkt: atomisk villkorad objektskrivning.
        // Om indexskrivning sedan misslyckas återlevererar Kafka samma id och data.
        objects.lagra(d);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var insert = connection.prepareStatement("""
                        INSERT INTO ffa_backend.dataleverans(dataleverans_id, topic, korrelations_id,
                            objekt_id, objekt_version, metadata, dokument_sha256)
                        VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (dataleverans_id) DO NOTHING
                        """)) {
                    insert.setObject(1, d.id());
                    insert.setString(2, topic);
                    insert.setString(3, d.korrelationsId());
                    insert.setString(4, d.objektId());
                    insert.setLong(5, d.objektVersion());
                    insert.setString(6, JSON.writeValueAsString(Leveransformat.metadata(d)));
                    insert.setBytes(7, hash(d));
                    insert.executeUpdate();
                }
                try (var query = connection.prepareStatement(
                        "SELECT * FROM ffa_backend.dataleverans WHERE dataleverans_id = ?")) {
                    query.setObject(1, d.id());
                    try (var rows = query.executeQuery()) {
                        rows.next();
                        kontrolleraIndex(rows, d);
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) { throw new IllegalStateException("Backendindex kunde inte skrivas", e); }
    }

    @Override public Dataleverans lasLeverans(UUID id) { return hamta("dataleverans_id = ?", id); }
    @Override public Dataleverans lasProcess(String id) { return hamta("korrelations_id = ?", id); }
    @Override public Dataleverans lasObjekt(String id) { return hamta("objekt_id = ?", id); }

    /** Indexuppslag är sökmetadata, inte ensamt bevis för att objektet finns i S3. */
    public Map<String, Object> metadata(String typ, String id) {
        String predicate = switch (typ) {
            case "leveranser" -> "dataleverans_id = ?";
            case "processer" -> "korrelations_id = ?";
            case "objekt" -> "objekt_id = ?";
            default -> throw new IllegalArgumentException("Okänd söktyp");
        };
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement(
                "SELECT * FROM ffa_backend.dataleverans WHERE topic = ? AND " + predicate
                        + " ORDER BY objekt_version DESC, ordning DESC LIMIT 1")) {
            query.setString(1, topic);
            query.setObject(2, typ.equals("leveranser") ? UUID.fromString(id) : id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                return Map.of("dataleveransId", rows.getObject("dataleverans_id").toString(),
                        "topic", topic, "korrelationsId", rows.getString("korrelations_id"),
                        "objektId", rows.getString("objekt_id"), "objektVersion", rows.getLong("objekt_version"),
                        "lagrad", rows.getTimestamp("lagrad").toInstant().toString(),
                        "dokumentSha256", HexFormat.of().formatHex(rows.getBytes("dokument_sha256")));
            }
        } catch (SQLException e) { throw new IllegalStateException("Metadataindex kunde inte läsas", e); }
    }

    private Dataleverans hamta(String predicate, Object value) {
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement(
                "SELECT * FROM ffa_backend.dataleverans WHERE topic = ? AND " + predicate
                        + " ORDER BY objekt_version DESC, ordning DESC LIMIT 1")) {
            query.setString(1, topic);
            query.setObject(2, value);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                var delivery = objects.las(rows.getObject("dataleverans_id", UUID.class));
                if (delivery == null) throw new IllegalStateException("Index finns men objektet saknas; rådata kan inte bekräftas");
                kontrolleraIndex(rows, delivery);
                Leveransformat.verifiera(delivery, key);
                return delivery;
            }
        } catch (SQLException e) { throw new IllegalStateException("Rådata kunde inte läsas", e); }
    }

    private void kontrolleraIndex(ResultSet rows, Dataleverans d) throws SQLException {
        if (!topic.equals(rows.getString("topic"))
                || !d.korrelationsId().equals(rows.getString("korrelations_id"))
                || !d.objektId().equals(rows.getString("objekt_id"))
                || d.objektVersion() != rows.getLong("objekt_version")
                || !JSON.readTree(rows.getString("metadata")).equals(JSON.valueToTree(Leveransformat.metadata(d)))
                || !Arrays.equals(hash(d), rows.getBytes("dokument_sha256")))
            throw new IllegalStateException("Backendindex och objektnyckel är inte knutna till samma leveransdata");
    }

    private static byte[] hash(Dataleverans d) {
        try { return MessageDigest.getInstance("SHA-256").digest(d.dokument().json()); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
