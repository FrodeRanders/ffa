package se.fk.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.fk.hundbidrag.Applikation;
import se.fk.hundbidrag.modell.YrkandeOmHundbidrag;
import se.fk.data.modell.v1.Ersattning;
import se.fk.mimer.runtime.*;

import java.nio.file.Path;
import java.nio.file.Files;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

/** Verifierar förmånsutvidgningen genom den förvaltade gränsen och den startbara demon. */
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
    void startbarDemoExporterarUnderlag() throws Exception {
        Path output = temp.resolve("demo.json");

        Demo.main(new String[]{output.toString()});

        assertTrue(Files.readString(output).contains("producerat_resultat"));
    }
}
