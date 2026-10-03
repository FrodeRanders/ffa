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
    public Dataleverans lasObjekt(String id) { return hamta("objekt_id = ?", id, true); }

    @Override
    public Dataleverans lasProcess(String id) { return hamta("korrelations_id = ?", id, true); }

    @Override
    public Dataleverans lasLeverans(UUID id) { return hamta("dataleverans_id = ?", id, false); }

    private Dataleverans hamta(String predicate, Object value, boolean processlasning) {
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement(
                "SELECT * FROM ffa_dataleverans WHERE topic = ? AND " + predicate + " ORDER BY objekt_version DESC, ordning DESC LIMIT 1")) {
            query.setString(1, topic);
            query.setObject(2, value);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                var delivery = lasRad(rows);
                if (processlasning && lage.strikt() && vantande(connection, delivery.korrelationsId()) != 0)
                    throw new Leveransfel(delivery.id(), true, false,
                            new IllegalStateException("Processen har obekräftade leveranser; återhämta före fortsättning"));
                return delivery;
            }
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

    // Strikt publicering behöver behålla låset även efter den inre PostgreSQL-committen.
    // PGSimpleDataSource ger en egen fysisk anslutning; close släpper sessionslåsen också vid fel.
    private void lasSession(Connection connection, String key) throws SQLException {
        try (var lock = connection.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
            lock.setString(1, "ffa:" + topic.length() + ":" + topic + ":" + key);
            lock.execute();
        }
    }

    @Override
    public void aterstall(Dataleverans delivery) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                las(connection, "process:" + delivery.korrelationsId(), true);
                las(connection, "objekt:" + delivery.objektId(), true);
                las(connection, "leverans:" + delivery.id(), true);
                try (var query = connection.prepareStatement(
                        "SELECT * FROM ffa_dataleverans WHERE dataleverans_id = ?")) {
                    query.setObject(1, delivery.id());
                    try (var rows = query.executeQuery()) {
                        if (rows.next()) {
                            var existing = lasRad(rows);
                            if (!topic.equals(rows.getString("topic")) || !sammaLeverans(existing, delivery))
                                throw new IllegalStateException("Dataleverans-id är redan knutet till annat data");
                            // Verifierat backenddata är positivt bevis även vid ett tidigare osäkert Kafka-utfall.
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
                bekrafta(connection, delivery.id(), Leveranssteg.RADATA_LAGRADE);
                avstamKvitton(connection, delivery);
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
        var local = new boolean[]{false};
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (lage.strikt()) {
                    lasSession(connection, "process:" + delivery.korrelationsId());
                    lasSession(connection, "objekt:" + delivery.objektId());
                } else {
                    las(connection, "process:" + delivery.korrelationsId(), true);
                    las(connection, "objekt:" + delivery.objektId(), true);
                }
                kontrolleraVersion(connection, delivery);
                if (lage.strikt()) {
                    if (vantande(connection, delivery.korrelationsId()) != 0)
                        throw new IllegalStateException("Processen har väntande leveranser; återförsök dem först");

                    publicerare.publicera(topic, delivery, () -> {
                        try {
                            skriv(connection, delivery, false);
                            connection.commit();
                            local[0] = true;
                        } catch (SQLException e) {
                            throw new IllegalStateException("Lokal commit kunde inte bekräftas", e);
                        }
                    });
                    if (!local[0]) throw new IllegalStateException("Publiceraren utelämnade lokal commit");
                    acknowledged = true;
                    registreraForsok(delivery.id(), true, null);
                } else {
                    skriv(connection, delivery, false);
                    connection.commit();
                    local[0] = true;
                }
            } catch (Exception e) {
                try { connection.rollback(); }
                catch (SQLException rollback) { e.addSuppressed(rollback); }

                if (local[0]) registreraForsok(delivery.id(), false, e.toString());
                throw new Leveransfel(delivery.id(), local[0], acknowledged, e);
            }
        } catch (SQLException e) { throw new Leveransfel(delivery.id(), local[0], acknowledged, e); }

        // Här är den lokala kopian redan hållbart committad. Ett leveransfel får inte låtsas rulla tillbaka den.
        if (lage == Leveranslage.LOKAL_RESILIENS) {
            try { skickaProcess(delivery.korrelationsId(), 100); }
            catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Lokalt lagrat; Kafka väntar på återförsök: " + e.getMessage());
            }
        }
    }

    // Fel i bokföringen får inte förvandla en bekräftad Kafka-commit till en påstådd rollback.
    // Raden förblir väntande och kan avstämmas eller skickas igen med samma id.
    private void registreraForsok(UUID id, boolean sent, String error) {
        try (var connection = dataSource.getConnection()) { uppdatera(connection, id, sent, error); }
        catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Leveransstatus kunde inte registreras för " + id, e);
        }
    }

    public Leveransstatus lasStatus(UUID id) {
        try (var connection = dataSource.getConnection(); var query = connection.prepareStatement(
                "SELECT * FROM ffa_dataleverans WHERE topic = ? AND dataleverans_id = ?")) {
            query.setString(1, topic);
            query.setObject(2, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) return null;
                return new Leveransstatus(id, tid(rows, "lagrad"), tid(rows, "kafka_publicerad_tid"),
                        tid(rows, "radata_lagrade_tid"), tid(rows, "grafbehandlad_tid"),
                        rows.getInt("leveransforsok"), rows.getString("senaste_fel"));
            }
        } catch (SQLException e) { throw new IllegalStateException("Leveransstatus kunde inte läsas", e); }
    }

    private static java.time.Instant tid(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** Anropas av betrodd infrastruktur efter positivt kvitto, aldrig enbart efter ett misslyckat uppslag.
     * Rådatakvittot måste avse båda backendlagren. Grafkvittot måste avse slutförd grafhantering. */
    public void bekrafta(Dataleverans delivery, Leveranssteg steg) {
        java.util.Objects.requireNonNull(steg);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var query = connection.prepareStatement(
                        "SELECT * FROM ffa_dataleverans WHERE topic = ? AND dataleverans_id = ? FOR UPDATE")) {
                    query.setString(1, topic);
                    query.setObject(2, delivery.id());
                    try (var rows = query.executeQuery()) {
                        if (!rows.next() || !sammaLeverans(lasRad(rows), delivery))
                            throw new IllegalArgumentException("Bekräftelsen avser inte samma lokala leveransdata");
                    }
                }
                bekrafta(connection, delivery.id(), steg);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) { throw new IllegalStateException("Leveransbekräftelse kunde inte lagras", e); }
    }

    public String topic() { return topic; }

    /** Beständig inkorg även när leveransen ännu saknas i cachen. */
    public void bekrafta(Kvittens kvittens) {
        if (!topic.equals(kvittens.topic())) throw new IllegalArgumentException("Fel förmånstopic");
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                las(connection, "leverans:" + kvittens.dataleveransId(), true);
                try (var insert = connection.prepareStatement("""
                        INSERT INTO ffa_kvittens(topic, dataleverans_id, steg, kvittens)
                        VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """)) {
                    insert.setString(1, topic);
                    insert.setObject(2, kvittens.dataleveransId());
                    insert.setString(3, kvittens.steg().name());
                    insert.setBytes(4, kvittens.json());
                    insert.executeUpdate();
                }
                try (var query = connection.prepareStatement(
                        "SELECT kvittens FROM ffa_kvittens WHERE topic = ? AND dataleverans_id = ?")) {
                    query.setString(1, topic);
                    query.setObject(2, kvittens.dataleveransId());
                    try (var rows = query.executeQuery()) {
                        while (rows.next()) {
                            var saved = Kvittens.las(rows.getBytes(1));
                            if (!saved.fingeravtryck().equals(kvittens.fingeravtryck())
                                    || !saved.korrelationsId().equals(kvittens.korrelationsId()))
                                throw new IllegalArgumentException("Samma leverans-id har motstridiga kvitton");
                        }
                    }
                }
                try (var query = connection.prepareStatement(
                        "SELECT * FROM ffa_dataleverans WHERE topic = ? AND dataleverans_id = ? FOR UPDATE")) {
                    query.setString(1, topic);
                    query.setObject(2, kvittens.dataleveransId());
                    try (var rows = query.executeQuery()) {
                        if (rows.next()) avstamKvitton(connection, lasRad(rows));
                    }
                }
                connection.commit();
            } catch (Exception e) { connection.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Kvittens kunde inte lagras", e); }
    }

    private void avstamKvitton(Connection connection, Dataleverans d) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT kvittens FROM ffa_kvittens WHERE topic = ? AND dataleverans_id = ?")) {
            query.setString(1, topic);
            query.setObject(2, d.id());
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    var kvittens = Kvittens.las(rows.getBytes(1));
                    if (!kvittens.avser(d)) throw new IllegalArgumentException("Kvittot avser andra leveransdata");
                    bekrafta(connection, d.id(), kvittens.steg());
                }
            }
        }
    }

    private void bekrafta(Connection connection, UUID id, Leveranssteg steg) throws SQLException {
        boolean kafka = steg != Leveranssteg.LOKALT_LAGRAD;
        boolean raw = steg == Leveranssteg.RADATA_LAGRADE || steg == Leveranssteg.GRAFBEHANDLAD;
        try (var update = connection.prepareStatement("""
                UPDATE ffa_dataleverans SET
                    kafka_publicerad = kafka_publicerad OR ?,
                    kafka_publicerad_tid = CASE WHEN ? THEN COALESCE(kafka_publicerad_tid, clock_timestamp()) ELSE kafka_publicerad_tid END,
                    radata_lagrade_tid = CASE WHEN ? THEN COALESCE(radata_lagrade_tid, clock_timestamp()) ELSE radata_lagrade_tid END,
                    grafbehandlad_tid = CASE WHEN ? THEN COALESCE(grafbehandlad_tid, clock_timestamp()) ELSE grafbehandlad_tid END,
                    senaste_fel = CASE WHEN ? THEN NULL ELSE senaste_fel END
                WHERE topic = ? AND dataleverans_id = ?
                """)) {
            update.setBoolean(1, kafka);
            update.setBoolean(2, kafka);
            update.setBoolean(3, raw);
            update.setBoolean(4, steg == Leveranssteg.GRAFBEHANDLAD);
            update.setBoolean(5, kafka);
            update.setString(6, topic);
            update.setObject(7, id);
            update.executeUpdate();
        }
    }

    private void skriv(Connection connection, Dataleverans delivery, boolean sent) throws SQLException {
        las(connection, "leverans:" + delivery.id(), true);
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
        avstamKvitton(connection, delivery);
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
                                throw new Leveransfel(delivery.id(), true, false, e);
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
        try (var update = connection.prepareStatement("UPDATE ffa_dataleverans SET kafka_publicerad = kafka_publicerad OR ?, "
                + "kafka_publicerad_tid = CASE WHEN ? THEN COALESCE(kafka_publicerad_tid, clock_timestamp()) ELSE kafka_publicerad_tid END, "
                + "leveransforsok = leveransforsok + 1, senaste_fel = CASE WHEN kafka_publicerad OR ? THEN NULL ELSE ? END "
                + "WHERE topic = ? AND dataleverans_id = ?")) {
            update.setBoolean(1, sent);
            update.setBoolean(2, sent);
            update.setBoolean(3, sent);
            update.setString(4, error);
            update.setString(5, topic);
            update.setObject(6, id);
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
