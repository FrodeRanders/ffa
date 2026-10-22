package se.fk.demo;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

/**
 * En lokal utvecklingsnyckel; produktionsnycklar ska komma från förvaltad nyckelhantering.
 */
final class Demonycklar {
    private Demonycklar() {
    }

    static synchronized KeyPair lasEllerSkapa(Path directory) throws Exception {
        Files.createDirectories(directory);
        // synchronized och fillås skyddar även samtidiga demoprocesser.
        try (var channel = FileChannel.open(directory.resolve(".nyckel.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            return lasEllerSkapaLast(directory);
        }
    }

    private static KeyPair lasEllerSkapaLast(Path directory) throws Exception {
        Path file = directory.resolve("signering.pkcs8");
        if (!Files.exists(file)) {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            Path temporary = Files.createTempFile(directory, "nyckel-", ".tmp");
            try {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
                Files.write(temporary, generator.generateKeyPair().getPrivate().getEncoded());
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        var factory = KeyFactory.getInstance("RSA");
        var privateKey = (RSAPrivateCrtKey) factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(file)));
        var publicKey = factory.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }
}
