package se.fk.mimer.klient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KeyMaterialLoader {
    private static final Pattern PEM_BLOCK =
            Pattern.compile("-----BEGIN ([^-]+)-----([\\s\\S]*?)-----END \\1-----");

    private KeyMaterialLoader() {}

    public record KeyMaterial(
            PrivateKey privateKey,
            X509Certificate signerCertificate,
            List<X509Certificate> certificateChain,
            List<X509Certificate> trustAnchors
    ) {}

    public static KeyMaterial loadFromEnv(
            String keyEnv,
            String certEnv,
            String chainEnv,
            String trustEnv
    ) {
        String keyPem = getEnvPem(keyEnv);
        String certPem = getEnvPem(certEnv);
        String chainPem = getEnvPem(chainEnv);
        String trustPem = getEnvPem(trustEnv);
        return loadFromPemStrings(keyPem, certPem, chainPem, trustPem);
    }

    public static KeyMaterial loadFromFiles(
            Path keyPath,
            Path certPath,
            Path chainPath,
            Path trustPath
    ) {
        try {
            String keyPem = keyPath == null ? null : Files.readString(keyPath);
            String certPem = certPath == null ? null : Files.readString(certPath);
            String chainPem = chainPath == null ? null : Files.readString(chainPath);
            String trustPem = trustPath == null ? null : Files.readString(trustPath);
            return loadFromPemStrings(keyPem, certPem, chainPem, trustPem);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read key material from files", e);
        }
    }

    public static KeyMaterial loadFromPemStrings(
            String privateKeyPem,
            String signerCertPem,
            String chainPem,
            String trustAnchorsPem
    ) {
        PrivateKey privateKey = privateKeyPem == null ? null : parsePrivateKey(privateKeyPem);
        X509Certificate signerCertificate = firstOrNull(parseCertificates(signerCertPem));
        List<X509Certificate> chain = parseCertificates(chainPem);
        List<X509Certificate> trustAnchors = parseCertificates(trustAnchorsPem);
        return new KeyMaterial(privateKey, signerCertificate, chain, trustAnchors);
    }

    private static String getEnvPem(String env) {
        if (env == null || env.isBlank()) {
            return null;
        }
        String value = System.getenv(env);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.contains("BEGIN")) {
            return value;
        }
        byte[] decoded = Base64.getDecoder().decode(value);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static PrivateKey parsePrivateKey(String pem) {
        PemBlock block = findFirstBlock(pem, "PRIVATE KEY");
        if (block == null) {
            PemBlock encrypted = findFirstBlock(pem, "ENCRYPTED PRIVATE KEY");
            if (encrypted != null) {
                throw new IllegalArgumentException("Encrypted private keys are not supported");
            }
            throw new IllegalArgumentException("No PKCS#8 private key found");
        }
        byte[] der = block.decode();
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        for (String alg : List.of("RSA", "EC", "Ed25519")) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(spec);
            } catch (Exception ignored) {
                // try next algorithm
            }
        }
        throw new IllegalArgumentException("Unsupported private key algorithm");
    }

    private static List<X509Certificate> parseCertificates(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        List<PemBlock> blocks = findBlocks(pem, "CERTIFICATE");
        if (blocks.isEmpty()) {
            return null;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certs = new ArrayList<>();
            for (PemBlock block : blocks) {
                try (ByteArrayInputStream in = new ByteArrayInputStream(block.decode())) {
                    certs.add((X509Certificate) cf.generateCertificate(in));
                }
            }
            return certs;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse X.509 certificate(s)", e);
        }
    }

    private static X509Certificate firstOrNull(List<X509Certificate> certs) {
        return (certs == null || certs.isEmpty()) ? null : certs.getFirst();
    }

    private static PemBlock findFirstBlock(String pem, String type) {
        List<PemBlock> blocks = findBlocks(pem, type);
        return blocks.isEmpty() ? null : blocks.getFirst();
    }

    private static List<PemBlock> findBlocks(String pem, String type) {
        List<PemBlock> blocks = new ArrayList<>();
        if (pem == null) {
            return blocks;
        }
        Matcher matcher = PEM_BLOCK.matcher(pem);
        while (matcher.find()) {
            String foundType = matcher.group(1).trim();
            if (foundType.equals(type)) {
                blocks.add(new PemBlock(foundType, matcher.group(2)));
            }
        }
        return blocks;
    }

    private record PemBlock(String type, String body) {
        byte[] decode() {
            String normalized = body.replaceAll("\\s", "");
            return Base64.getDecoder().decode(normalized);
        }
    }
}
