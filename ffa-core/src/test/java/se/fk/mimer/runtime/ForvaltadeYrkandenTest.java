package se.fk.mimer.runtime;

import org.junit.jupiter.api.Test;
import se.fk.teststod.Minneslager;
import se.fk.data.modell.v1.*;
import se.fk.data.modell.utils.SignatureUtils;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifierar objektgränsens kontrakt från förmånens perspektiv.
 * Testerna öppnar lagringsadaptern enbart för att simulera fel och historiska dokument.
 */
class ForvaltadeYrkandenTest {
    private static final KeyPair KEYS = nycklar();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ForvaltadeYrkanden<Yrkande> repository(Dokumentlager store) {
        return new ForvaltadeYrkanden<>(Yrkande.class, store, KEYS.getPrivate(), KEYS.getPublic());
    }

    private static KeyPair nycklar() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Yrkande yrkande() {
        var y = new Yrkande("Ansökan");
        y.setPerson(new FysiskPerson("19121212-1212"));

        var e = new Ersattning();
        e.typ = Ersattning.Typ.HUNDBIDRAG;
        e.belopp = 1000;
        y.addProduceratResultat(e);

        var b = new Beslut();
        b.datum = new Date(0);
        b.typ = Beslut.Typ.SLUTLIGT;
        b.utfall = Beslut.Utfall.BEVILJAT;
        y.setBeslut(b);
        return y;
    }

    private static LagratDokument signera(byte[] json) {
        return new LagratDokument(json, SignatureUtils.sign(json, KEYS.getPrivate()));
    }

    @Test
    void lagringAterlasningAndringOchOförandratTillstand() {
        var store = new Minneslager();
        var repo = repository(store);
        var original = yrkande();

        var saved = repo.lagra(original);

        assertEquals(0, original.getVersion());
        assertEquals(1, saved.getVersion());
        assertEquals(1, saved.beslut.getVersion());
        assertEquals(1, saved.produceratResultat.iterator().next().getVersion());
        assertEquals(se.fk.mimer.migration.MimerMigrations.CURRENT, JSON.readTree(store.las(saved.getId()).json()).path("mimer:schemaVersion").intValue());

        saved = repo.las(saved.getId());
        ((Ersattning) saved.produceratResultat.iterator().next()).belopp = 1200;

        var updated = repo.lagra(saved);

        assertEquals(1, saved.getVersion());
        assertEquals(2, updated.getVersion());
        assertEquals(2, updated.produceratResultat.iterator().next().getVersion());
        assertEquals(1, updated.beslut.getVersion());
        assertEquals(1200, ((Ersattning) repo.las(updated.getId()).produceratResultat.iterator().next()).belopp);

        // En ny lagring utan verksamhetsändring ska inte markera några objekt som ändrade.
        var unchanged = repo.lagra(updated);
        assertEquals(2, unchanged.getVersion());
        assertEquals(2, unchanged.produceratResultat.iterator().next().getVersion());
        var json = JSON.readTree(store.las(unchanged.getId()).json());
        assertFalse(json.has("__attention"));
        assertFalse(json.path("beslut").has("__attention"));
        assertFalse(json.path("producerat_resultat").get(0).has("__attention"));
    }

    @Test
    void nyttResultatFarEgenVersion() {
        var repo = repository(new Minneslager());
        var saved = repo.lagra(yrkande());
        var nytt = new Ersattning();
        nytt.typ = Ersattning.Typ.HUNDBIDRAG;
        nytt.belopp = 200;
        saved.addProduceratResultat(nytt);
        var updated = repo.lagra(saved);
        assertEquals(2, updated.getVersion());
        assertEquals(1, updated.produceratResultat.stream().skip(1).findFirst().orElseThrow().getVersion());
    }

    @Test
    void manipuleratDokumentAvvisasForeBindning() {
        var store = new Minneslager();
        var repo = repository(store);
        var saved = repo.lagra(yrkande());
        var doc = store.las(saved.getId());

        // Ändra giltig JSON utan att uppdatera signaturen, som vid manipulation i transport eller lager.
        byte[] tampered = new String(doc.json(), StandardCharsets.UTF_8).replace("1000.0", "9000.0").getBytes(StandardCharsets.UTF_8);
        assertFalse(java.util.Arrays.equals(doc.json(), tampered));
        store.lagra(saved.getId(), new LagratDokument(tampered, doc.signatur()));
        assertThrows(IllegalStateException.class, () -> repo.las(saved.getId()));
    }

    @Test
    void kanoniseringAccepterarFormateringMenAvvisarInnehallsandring() {
        byte[] original = "{\"belopp\":1000,\"typ\":\"HUNDBIDRAG\"}".getBytes(StandardCharsets.UTF_8);
        byte[] formatted = "{ \"typ\": \"HUNDBIDRAG\", \"belopp\": 1000 }".getBytes(StandardCharsets.UTF_8);
        byte[] signature = SignatureUtils.sign(original, KEYS.getPrivate());

        assertTrue(SignatureUtils.verify(formatted, signature, KEYS.getPublic()));
        assertFalse(SignatureUtils.verify(new String(formatted, StandardCharsets.UTF_8)
                .replace("1000", "9000").getBytes(StandardCharsets.UTF_8), signature, KEYS.getPublic()));
    }

    @Test
    void annanNyckelArInteBetrodd() {
        var store = new Minneslager();
        var saved = repository(store).lagra(yrkande());
        var wrong = new ForvaltadeYrkanden<>(Yrkande.class, store, KEYS.getPrivate(), nycklar().getPublic());
        assertThrows(IllegalStateException.class, () -> wrong.las(saved.getId()));
    }

    @Test
    void historisktSigneratFormatMigrerasUtanAttOriginaletAndras() throws Exception {
        byte[] historic;
        try (var input = getClass().getResourceAsStream("/fixtures/yrkande-v0.json")) { historic = input.readAllBytes();
        }
        var store = new Minneslager();
        store.lagra("yrkande-historiskt", signera(historic));
        var repo = repository(store);
        var loaded = repo.las("yrkande-historiskt");
        assertEquals(3, loaded.getVersion());
        assertEquals(2, loaded.produceratResultat.iterator().next().getVersion());
        assertEquals(1000, ((Ersattning) loaded.produceratResultat.iterator().next()).belopp);

        // Migreringen måste lämna de ursprungliga signerade byten i lagret intakta.
        assertArrayEquals(historic, store.las(loaded.getId()).json());

        // Först vid nästa lagring skrivs det aktuella formatet; verksamhetsversionen behålls.
        var saved = repo.lagra(loaded);
        assertEquals(3, saved.getVersion());
        var current = JSON.readTree(store.las(saved.getId()).json());
        assertEquals(se.fk.mimer.migration.MimerMigrations.CURRENT, current.path("mimer:schemaVersion").intValue());
        assertFalse(current.has("producerade_resultat"));
        assertTrue(current.has("producerat_resultat"));
    }

    @Test
    void framtidaFormatOchFelTypAvvisasAvenMedGiltigSignatur() {
        var store = new Minneslager();
        var repo = repository(store);
        var saved = repo.lagra(yrkande());
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree(store.las(saved.getId()).json());
        root.put("mimer:schemaVersion", 99);
        store.lagra(saved.getId(), signera(JSON.writeValueAsBytes(root)));
        assertThrows(IllegalArgumentException.class, () -> repo.las(saved.getId()));
        root.put("mimer:schemaVersion", 1);
        root.put("@type", "se.fk.data.modell.v1.Beslut");
        store.lagra(saved.getId(), signera(JSON.writeValueAsBytes(root)));
        assertThrows(IllegalArgumentException.class, () -> repo.las(saved.getId()));
    }

    @Test
    void tvetydigMigreringAvvisas() {
        var root = (tools.jackson.databind.node.ObjectNode) JSON.readTree("{\"producerade_resultat\": [], \"producerat_resultat\": []}");
        assertThrows(IllegalArgumentException.class, () -> se.fk.mimer.migration.MimerMigrations.migrate(root));
    }

    @Test
    void processlasningGerSenasteTillstandOchLeveranslasningBevararHistoriken() {
        var store = new Minneslager();
        var repo = repository(store);
        var original = yrkande();
        assertThrows(IllegalArgumentException.class, () -> repo.lagra(" ", original));
        assertEquals(0, original.getVersion());

        var first = repo.lagra("process-17", original);
        var firstDelivery = store.lasProcess("process-17");
        first.beskrivning = "Uppdaterat processtillstånd";
        var second = repo.lagra("process-17", first);
        var secondDelivery = store.lasProcess("process-17");

        assertNotEquals(firstDelivery.id(), secondDelivery.id());
        assertEquals(7, firstDelivery.id().version());
        assertEquals(7, secondDelivery.id().version());
        assertEquals(1, repo.lasLeverans(firstDelivery.id().toString()).getVersion());
        assertEquals(second.getVersion(), repo.lasProcess("process-17").getVersion());
        assertEquals("Uppdaterat processtillstånd", repo.lasProcess("process-17").beskrivning);
    }

    @Test
    void signaturenVerifierasInnanMigreringsreglernaKors() throws Exception {
        byte[] historic;
        try (var input = getClass().getResourceAsStream("/fixtures/yrkande-v0.json")) {
            historic = input.readAllBytes();
        }
        byte[] signature = SignatureUtils.sign(historic, KEYS.getPrivate());
        var altered = (tools.jackson.databind.node.ObjectNode) JSON.readTree(historic);
        // Migreringen skulle avvisa det tvetydiga namnbytet, men signaturfelet ska upptäckas först.
        altered.putArray("producerat_resultat");
        var store = new Minneslager();
        store.lagra("yrkande-historiskt", new LagratDokument(JSON.writeValueAsBytes(altered), signature));

        var error = assertThrows(IllegalStateException.class, () -> repository(store).las("yrkande-historiskt"));

        assertEquals("Dokumentets signatur är ogiltig", error.getMessage());
    }

    @Test
    void ogiltigModellLagrasInte() {
        var store = new Minneslager();
        var repo = repository(store);
        var y = yrkande();
        y.beslut.utfall = null;
        assertThrows(IllegalArgumentException.class, () -> repo.lagra(y));
        assertNull(store.las(y.getId()));
        assertEquals(0, y.getVersion());
        y.beslut.utfall = Beslut.Utfall.BEVILJAT;
        ((Ersattning) y.produceratResultat.iterator().next()).belopp = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> repo.lagra(y));
        ((Ersattning) y.produceratResultat.iterator().next()).belopp = 1000;
        y.produceratResultat.add(y.produceratResultat.iterator().next());
        assertThrows(IllegalArgumentException.class, () -> repo.lagra(y));
    }

    @Test
    void lagringsfelAndrarInteOriginaletsLivscykel() {
        // Felet inträffar efter att representationen förberetts, vid själva lagringsanropet.
        Dokumentlager failing = new Dokumentlager() {
            public LagratDokument las(String id) {
                return null;
            }
            public void lagra(String id, LagratDokument doc) {
                throw new IllegalStateException("Lagringen misslyckades");
            }
        };
        var repo = repository(failing);
        var y = yrkande();
        assertThrows(IllegalStateException.class, () -> repo.lagra(y));
        assertEquals(0, y.getVersion());
        assertEquals(0, y.beslut.getVersion());
        assertEquals(0, y.produceratResultat.iterator().next().getVersion());
        assertEquals(1, repository(new Minneslager()).lagra(y).getVersion());
    }

    @Test
    void inaktuellVersionKanInteSkrivaOverSenareTillstand() {
        var repo = repository(new Minneslager());
        var saved = repo.lagra(yrkande());
        var stale = repo.las(saved.getId());
        saved.beskrivning = "Uppdaterad";
        repo.lagra(saved);
        stale.beskrivning = "Inaktuell";
        assertThrows(IllegalStateException.class, () -> repo.lagra(stale));
        assertEquals("Uppdaterad", repo.las(saved.getId()).beskrivning);
        assertEquals(1, stale.getVersion());
    }

    @Test
    void representationensBytearrayerKanInteAndrasViaReferenser() {
        byte[] bytes = {1};
        var doc = new LagratDokument(bytes, bytes);
        bytes[0] = 9;
        doc.json()[0] = 8;
        doc.signatur()[0] = 7;
        assertEquals(1, doc.json()[0]);
        assertEquals(1, doc.signatur()[0]);
    }
}
