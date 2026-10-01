package se.fk.mimer.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Kontrollera gränsen med javac, även om testköraren själv använder klassökvägen. */
class ModulgransTest {
    @TempDir
    Path temp;

    private record Result(int exit, String output) {}

    private Result compile(String body) throws Exception {
        Path directory = Files.createTempDirectory(temp, "probe");
        Path descriptor = directory.resolve("module-info.java");
        Path source = directory.resolve("Probe.java");

        // Kompilera som en separat konsumentmodul, inte som en del av kärnans testkod.
        Files.writeString(descriptor, "module probe { requires se.fk.ffa.core; }");
        Files.writeString(source, "package probe; public class Probe { " + body + " }");

        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                "-J-Duser.language=en", "--module-path", System.getProperty("java.class.path"),
                "-d", directory.resolve("out").toString(), descriptor.toString(), source.toString())
                .redirectErrorStream(true).start();

        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }

    @Test
    void formanFarAnvandaObjektOchLagringsApi() throws Exception {
        var result = compile("void run(se.fk.mimer.api.Yrkanden<se.fk.data.modell.v1.Yrkande> repo) { "
                + "var y = new se.fk.data.modell.v1.Yrkande(); repo.lagra(y); y.getId(); y.getVersion(); }");
        assertEquals(0, result.exit(), result.output());
    }

    @Test
    void internDatahanteringArInteExporterad() throws Exception {
        for (String type : new String[]{"se.fk.mimer.runtime.ModellCodec", "se.fk.mimer.runtime.Dokumentlager",
                "se.fk.data.modell.internal.LifecycleState", "se.fk.mimer.migration.MigrationEngine"}) {
            var result = compile(type + " value;");
            assertNotEquals(0, result.exit());
            assertTrue(result.output().contains("does not export"), result.output());
        }
    }

    @Test
    void jsonbibliotekOchMetadataKanInteAnvandasDirekt() throws Exception {
        var json = compile("tools.jackson.databind.ObjectMapper mapper;");
        assertNotEquals(0, json.exit());
        assertTrue(json.output().contains("does not read"), json.output());
        var jsonPath = compile("com.jayway.jsonpath.JsonPath path;");
        assertNotEquals(0, jsonPath.exit());
        assertTrue(jsonPath.output().contains("does not read"), jsonPath.output());
        var metadata = compile("void run(se.fk.data.modell.v1.Yrkande y) { y.version = 99; y.stepVersion(); }");
        assertNotEquals(0, metadata.exit());
    }
}
