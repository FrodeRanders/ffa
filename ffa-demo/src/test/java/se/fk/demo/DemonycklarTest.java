package se.fk.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DemonycklarTest {
    @TempDir Path directory;

    @Test
    void samtidigaJvmProcesserAnvanderSammaBestandigaNyckel() throws Exception {
        var processer = new ArrayList<Process>();
        try {
            for (int i = 0; i < 4; i++) {
                processer.add(new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                        Probe.class.getName(), directory.toString()).redirectErrorStream(true).start());
            }
            var publicKeys = new HashSet<String>();
            for (var process : processer) {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS));
                String output = new String(process.getInputStream().readAllBytes()).trim();
                assertEquals(0, process.exitValue(), output);
                publicKeys.add(output);
            }
            assertEquals(1, publicKeys.size());
            byte[] original = Files.readAllBytes(directory.resolve("signering.pkcs8"));
            var reopened = Demonycklar.lasEllerSkapa(directory);
            assertEquals(publicKeys.iterator().next(), Base64.getEncoder().encodeToString(reopened.getPublic().getEncoded()));
            assertArrayEquals(original, Files.readAllBytes(directory.resolve("signering.pkcs8")));
        } finally { processer.forEach(Process::destroyForcibly); }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            // Endast offentlig nyckel skrivs ut; privat nyckel stannar i den isolerade testkatalogen.
            var keys = Demonycklar.lasEllerSkapa(Path.of(args[0]));
            System.out.println(Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));
        }
    }
}
