package se.fk.demo;

import org.junit.jupiter.api.Test;
import se.fk.teststod.Minneslager;
import org.junit.jupiter.api.io.TempDir;
import se.fk.hundbidrag.Applikation;
import se.fk.hundbidrag.modell.YrkandeOmHundbidrag;
import se.fk.data.modell.v1.Ersattning;
import se.fk.mimer.runtime.*;
import se.fk.data.modell.utils.SignatureUtils;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Verifierar förmånsutvidgningen genom den förvaltade gränsen och demoflödet med en lagringsadapter för test. */
class DemoTest {
    @TempDir
    Path temp;

    @Test
    void formanensUtvidgningOchBeslutAterlasas() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var repository = new ForvaltadeYrkanden<>(YrkandeOmHundbidrag.class, new Minneslager(), keys.getPrivate(), keys.getPublic());

        var result = new Applikation(repository).handlagg();
        var loaded = repository.las(result.getId());

        assertEquals("Collie", loaded.ras);
        assertEquals(2, loaded.getVersion());
        assertEquals(2, loaded.produceratResultat.iterator().next().getVersion());
        assertEquals(1, loaded.beslut.getVersion());
        assertEquals(1200, ((Ersattning) loaded.produceratResultat.iterator().next()).belopp);
    }

    @Test
    void demoflodeExporterarUnderlag() throws Exception {
        Path output = temp.resolve("demo.json");

        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        Demo.kor(output, new Minneslager(), generator.generateKeyPair());

        assertTrue(Files.readString(output).contains("producerat_resultat"));
    }

    @Test
    void historisktDemoUnderlagMigrerasForeBindningOchSignerasOmVidLagring() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        byte[] historic;
        try (var input = Demo.class.getResourceAsStream("/yrkande-v0.json")) {
            historic = input.readAllBytes();
        }
        var store = new Minneslager();
        var original = new LagratDokument(historic, SignatureUtils.sign(historic, keys.getPrivate()));
        store.lagra("yrkande-historiskt", original);
        var repository = new ForvaltadeYrkanden<>(YrkandeOmHundbidrag.class, store, keys.getPrivate(), keys.getPublic());

        var loaded = repository.las("yrkande-historiskt");

        assertEquals("Collie", loaded.ras);
        assertEquals(3, loaded.getVersion());
        assertEquals(1000, ((Ersattning) loaded.produceratResultat.iterator().next()).belopp);
        assertArrayEquals(historic, store.las(loaded.getId()).json());

        var saved = repository.lagra(loaded);
        var current = store.las(saved.getId());
        assertEquals(3, saved.getVersion());
        assertEquals(2, saved.produceratResultat.iterator().next().getVersion());
        assertFalse(Arrays.equals(historic, current.json()));
        assertTrue(SignatureUtils.verify(current.json(), current.signatur(), keys.getPublic()));
        assertEquals(1000, ((Ersattning) repository.las(saved.getId()).produceratResultat.iterator().next()).belopp);
    }
}
