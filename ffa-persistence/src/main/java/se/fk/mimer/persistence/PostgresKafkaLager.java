package se.fk.mimer.persistence;

import org.postgresql.ds.PGSimpleDataSource;
import se.fk.mimer.runtime.Dataleverans;
import se.fk.mimer.runtime.Dokumentlager;
import se.fk.mimer.runtime.LagratDokument;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;

/** Historikcache och outbox. Masterdata finns hos systemen efter Kafka, inte i denna databas. */
public final class PostgresKafkaLager implements Dokumentlager {
    private static final System.Logger LOG = System.getLogger(PostgresKafkaLager.class.getName());
    private final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    private final String topic;
    private final Leveranslage lage;
    private final Leveranspublicerare publicerare;

    public PostgresKafkaLager(String url, String user, String password, String topic,
                             Leveranslage lage, Leveranspublicerare publicerare) {
        if (topic == null || topic.isBlank())
            throw new IllegalArgumentException("Förmånstopic måste anges");

        dataSource.setURL(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        dataSource.setConnectTimeout(5);
        this.topic = topic;
        this.lage = java.util.Objects.requireNonNull(lage);
        this.publicerare = java.util.Objects.requireNonNull(publicerare);
    }

    /** Idempotent schemainitiering för den lokala utvecklingsdatabasen. */
    public void initiera() {
        try (var input = getClass().getResourceAsStream("/cache.sql");
             var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("PostgreSQL-cachen kunde inte initieras", e);
        }
    }

    @Override
    public Dataleverans lasObjekt(String id) { return hamta("objekt_id = ?", id); }

    @Override
    public Dataleverans lasProcess(String id) { return hamta("korrelations_id = ?", id); }

    @Override
    public Dataleverans lasLeverans(UUID id) { return hamta("dataleverans_id = ?", id); }

    private Dataleverans hamta(String predicate, Object value) {
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement(
                "SELECT * FROM ffa_dataleverans WHERE topic = ? AND " + predicate + " ORDER BY objekt_version DESC, ordning DESC LIMIT 1")) {
            query.setString(1, topic);
            query.setObject(2, value);
            try (var rows = query.executeQuery()) { return rows.next() ? lasRad(rows) : null; }
        } catch (SQLException e) { throw new IllegalStateException("Cachen kunde inte läsas", e); }
    }

    private static Dataleverans lasRad(ResultSet row) throws SQLException {
        return new Dataleverans(row.getObject("dataleverans_id", UUID.class), row.getString("korrelations_id"),
                row.getString("objekt_id"), row.getLong("forvantad_version"), row.getLong("objekt_version"),
                row.getTimestamp("skapad").toInstant(), new LagratDokument(
                row.getString("dokument").getBytes(StandardCharsets.UTF_8), row.getBytes("signatur")));
    }

    // Processlås bevarar publiceringsordningen; objektlås skyddar versionskontrollen även
    // om två anrop felaktigt försöker knyta samma objekt till olika processer.
    private boolean las(Connection connection, String key, boolean vanta) throws SQLException {
        String function = vanta ? "pg_advisory_xact_lock" : "pg_try_advisory_xact_lock";
        try (var lock = connection.prepareStatement("SELECT " + function + "(hashtextextended(?, 0))")) {
            lock.setString(1, "ffa:" + topic.length() + ":" + topic + ":" + key);
            try (var rows = lock.executeQuery()) { rows.next(); return vanta || rows.getBoolean(1); }
        }
    }

    @Override
    public void aterstall(Dataleverans delivery) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                las(connection, "process:" + delivery.korrelationsId(), true);
                las(connection, "objekt:" + delivery.objektId(), true);
                try (var query = connection.prepareStatement(
                        "SELECT * FROM ffa_dataleverans WHERE dataleverans_id = ?")) {
                    query.setObject(1, delivery.id());
                    try (var rows = query.executeQuery()) {
                        if (rows.next()) {
                            var existing = lasRad(rows);
                            if (!topic.equals(rows.getString("topic")) || !sammaLeverans(existing, delivery))
                                throw new IllegalStateException("Dataleverans-id är redan knutet till annat data");
                            // En lokal, ännu okvitterad leverans behåller sin outbox-status.
                        } else {
                            skriv(connection, delivery, true);
                            try (var update = connection.prepareStatement(
                                    "UPDATE ffa_dataleverans SET leveransforsok = 0 WHERE dataleverans_id = ?")) {
                                update.setObject(1, delivery.id());
                                update.executeUpdate();
                            }
                        }
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) { throw new IllegalStateException("Backenddata kunde inte återställas", e); }
    }

    private static boolean sammaLeverans(Dataleverans a, Dataleverans b) {
        return a.id().equals(b.id()) && a.korrelationsId().equals(b.korrelationsId())
                && a.objektId().equals(b.objektId()) && a.objektVersion() == b.objektVersion()
                && a.forvantadVersion() == b.forvantadVersion() && a.skapad().equals(b.skapad())
                && java.util.Arrays.equals(a.dokument().json(), b.dokument().json())
                && java.util.Arrays.equals(a.dokument().signatur(), b.dokument().signatur());
    }

    private void kontrolleraVersion(Connection connection, Dataleverans delivery) throws SQLException {
        try (var query = connection.prepareStatement("SELECT objekt_id, objekt_version, korrelations_id FROM ffa_dataleverans "
                + "WHERE topic = ? AND (objekt_id = ? OR korrelations_id = ?) ORDER BY objekt_version DESC, ordning DESC")) {
            query.setString(1, topic);
            query.setString(2, delivery.objektId());
            query.setString(3, delivery.korrelationsId());
            try (var rows = query.executeQuery()) {
                if (rows.next()) {
                    if (!rows.getString(1).equals(delivery.objektId())
                            || !rows.getString(3).equals(delivery.korrelationsId())
                            || rows.getLong(2) != delivery.forvantadVersion())
                        throw new IllegalStateException("Processen har ändrats; läs om före lagring");
                } else if (delivery.forvantadVersion() != 0) {
                    throw new IllegalStateException("Tidigare processtillstånd saknas i cachen");
                }
            }
        }
    }

    @Override
    public void lagra(Dataleverans delivery) {
        boolean acknowledged = false;
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                las(connection, "process:" + delivery.korrelationsId(), true);
                las(connection, "objekt:" + delivery.objektId(), true);
                kontrolleraVersion(connection, delivery);
                if (lage == Leveranslage.KAFKA_FORST) {
                    if (vantande(connection, delivery.korrelationsId()) != 0)
                        throw new IllegalStateException("Processen har väntande leveranser; återförsök dem först");

                    publicerare.publicera(topic, delivery);
                    acknowledged = true;
                }
                skriv(connection, delivery, acknowledged);
                connection.commit();
            } catch (Exception e) {
                try { connection.rollback(); }
                catch (SQLException rollback) { e.addSuppressed(rollback); }

                throw new Leveransfel(delivery.id(), acknowledged, e);
            }
        } catch (SQLException e) { throw new Leveransfel(delivery.id(), acknowledged, e); }

        // Här är den lokala kopian redan hållbart committad. Ett leveransfel får inte låtsas rulla tillbaka den.
        if (lage == Leveranslage.LOKAL_RESILIENS) {
            try { skickaProcess(delivery.korrelationsId(), 100); }
            catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Lokalt lagrat; Kafka väntar på återförsök: " + e.getMessage());
            }
        }
    }

    private void skriv(Connection connection, Dataleverans delivery, boolean sent) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO ffa_dataleverans(dataleverans_id, korrelations_id, objekt_id, objekt_version,
                    forvantad_version, topic, skapad, dokument, signatur, kafka_publicerad, kafka_publicerad_tid, leveransforsok)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? THEN clock_timestamp() ELSE NULL END, ?)
                """)) {
            insert.setObject(1, delivery.id());
            insert.setString(2, delivery.korrelationsId());
            insert.setString(3, delivery.objektId());
            insert.setLong(4, delivery.objektVersion());
            insert.setLong(5, delivery.forvantadVersion());
            insert.setString(6, topic);
            insert.setTimestamp(7, Timestamp.from(delivery.skapad()));
            insert.setString(8, new String(delivery.dokument().json(), StandardCharsets.UTF_8));
            insert.setBytes(9, delivery.dokument().signatur());
            insert.setBoolean(10, sent);
            insert.setBoolean(11, sent);
            insert.setInt(12, sent ? 1 : 0);
            insert.executeUpdate();
        }
    }

    /** Fördelar batchen mellan processer. Ett fel hindrar inte andra processers återförsök. */
    public int skickaVantande(int maxAntal) {
        if (maxAntal <= 0) throw new IllegalArgumentException("Batchstorleken måste vara positiv");
        var processer = new java.util.LinkedHashMap<String, Integer>();
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement("""
                SELECT korrelations_id FROM (
                    SELECT korrelations_id, ordning,
                           row_number() OVER (PARTITION BY korrelations_id ORDER BY ordning) AS plats
                    FROM ffa_dataleverans WHERE topic = ? AND NOT kafka_publicerad
                ) AS vantande ORDER BY plats, ordning LIMIT ?
                """)) {
            query.setString(1, topic);
            query.setInt(2, maxAntal);
            try (var rows = query.executeQuery()) {
                while (rows.next()) processer.merge(rows.getString(1), 1, Integer::sum);
            }
        } catch (SQLException e) { throw new IllegalStateException("Outbox kunde inte läsas", e); }
        if (processer.isEmpty()) return 0;

        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(Math.min(8, processer.size()))) {
            var resultat = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            processer.forEach((process, antal) -> resultat.add(executor.submit(() -> skickaProcess(process, antal))));
            int skickade = 0;
            RuntimeException fel = null;
            for (var future : resultat) {
                try { skickade += future.get(); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Återförsök avbröts", e);
                } catch (java.util.concurrent.ExecutionException e) {
                    var failure = e.getCause() instanceof RuntimeException runtime ? runtime
                            : new IllegalStateException("Återförsök misslyckades", e.getCause());
                    if (fel == null) fel = failure; else fel.addSuppressed(failure);
                }
            }
            if (fel != null) throw fel;
            return skickade;
        }
    }

    /** Återförsök i processens lagringsordning, med samma id, JSON och signatur.
     * En process som redan bearbetas av en annan arbetare hoppas över denna gång. */
    public int skickaProcess(String korrelationsId, int maxAntal) {
        if (maxAntal <= 0) throw new IllegalArgumentException("Batchstorleken måste vara positiv");
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!las(connection, "process:" + korrelationsId, false)) return 0;
                int sent = 0;
                try (var query = connection.prepareStatement("SELECT * FROM ffa_dataleverans "
                        + "WHERE topic = ? AND korrelations_id = ? AND NOT kafka_publicerad ORDER BY ordning LIMIT ? FOR UPDATE")) {
                    query.setString(1, topic);
                    query.setString(2, korrelationsId);
                    query.setInt(3, maxAntal);
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) {
                            var delivery = lasRad(rows);
                            try {
                                publicerare.publicera(topic, delivery);
                                uppdatera(connection, delivery.id(), true, null);
                                sent++;
                            } catch (RuntimeException e) {
                                uppdatera(connection, delivery.id(), false, e.toString());

                                // Bevara tidigare kvitton och felinformationen; nästa leverans får inte passera den felande.
                                connection.commit();
                                throw new Leveransfel(delivery.id(), false, e);
                            }
                        }
                    }
                }
                connection.commit();
                return sent;
            } catch (Exception e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Återförsök misslyckades", e);
            }
        } catch (SQLException e) { throw new IllegalStateException("Outbox kunde inte läsas", e); }
    }

    private void uppdatera(Connection connection, UUID id, boolean sent, String error) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE ffa_dataleverans SET kafka_publicerad = ?, "
                + "kafka_publicerad_tid = CASE WHEN ? THEN clock_timestamp() ELSE NULL END, "
                + "leveransforsok = leveransforsok + 1, senaste_fel = ? WHERE dataleverans_id = ?")) {
            update.setBoolean(1, sent);
            update.setBoolean(2, sent);
            update.setString(3, error);
            update.setObject(4, id);
            update.executeUpdate();
        }
    }

    private long vantande(Connection connection, String correlation) throws SQLException {
        try (var query = connection.prepareStatement("SELECT count(*) FROM ffa_dataleverans "
                + "WHERE topic = ? AND NOT kafka_publicerad AND (? IS NULL OR korrelations_id = ?)")) {
            query.setString(1, topic);
            query.setString(2, correlation);
            query.setString(3, correlation);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    public long antalVantande() {
        try (var connection = dataSource.getConnection()) { return vantande(connection, null); }
        catch (SQLException e) { throw new IllegalStateException("Outbox kunde inte räknas", e); }
    }
}
