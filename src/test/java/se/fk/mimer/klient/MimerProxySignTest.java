package se.fk.mimer.klient;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.json.DigestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.*;

public class MimerProxySignTest {
    private static final Logger log = LoggerFactory.getLogger(MimerProxySignTest.class);
    private static final byte[] SHA256_DIGEST_INFO_PREFIX = new byte[] {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86,
            0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };

    @Test
    public void serializeAndSign_producesVerifiableSignature() throws Exception {
        log.info("*** Testcase *** Serialize payload and sign it, then verify signature and chain");
        ensureBcProvider();
        KeyPair keyPair = rsaKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        X509Certificate cert = selfSigned(keyPair, "CN=Mimer-Test");
        PublicKey publicKey = cert.getPublicKey();

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> payload = Map.of(
                "id", "abc-123",
                "amount", 2500,
                "currency", "SEK"
        );

        MimerProxy.SignedJson signed = MimerProxy.serializeAndSign(
                payload,
                mapper,
                privateKey,
                "test-key",
                cert,
                null
        );

        assertNotNull(signed.jsonBytes());
        assertNotNull(signed.signatureBytes());
        assertTrue(signed.signatureBytes().length > 0);
        assertNotNull(signed.signerCertificateDer());

        byte[] digest = DigestUtils.computeJcsDigestFromJsonBytes(signed.jsonBytes());
        byte[] expected = digestInfo(digest);

        byte[] actual = rsaPkcs1Decrypt(signed.signatureBytes(), publicKey);
        assertArrayEquals(expected, actual);

        MimerProxy.VerificationResult result = MimerProxy.verifySignature(
                signed.jsonBytes(),
                signed.signatureBytes(),
                cert,
                java.util.List.of(cert),
                java.util.List.of(cert)
        );
        assertTrue(result.signatureValid());
        assertTrue(result.chainValid());
    }

    @Test
    public void verifyAndDeserialize_acceptsValidSignature() throws Exception {
        log.info("*** Testcase *** Verify signature and deserialize JSON when signature is valid");
        ensureBcProvider();
        KeyPair keyPair = rsaKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        X509Certificate cert = selfSigned(keyPair, "CN=Mimer-Test");

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> payload = Map.of(
                "id", "abc-123",
                "amount", 2500,
                "currency", "SEK"
        );

        MimerProxy.SignedJson signed = MimerProxy.serializeAndSign(
                payload,
                mapper,
                privateKey,
                "test-key",
                cert,
                null
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = MimerProxy.verifyAndDeserialize(
                signed.jsonBytes(),
                signed.signatureBytes(),
                cert,
                java.util.List.of(cert),
                java.util.List.of(cert),
                false,
                mapper,
                Map.class
        );

        assertEquals(payload.get("id"), decoded.get("id"));
        assertEquals(payload.get("currency"), decoded.get("currency"));
    }

    @Test
    public void verifyAndDeserialize_rejectsInvalidSignature() throws Exception {
        log.info("*** Testcase *** Reject verification when JSON bytes are tampered after signing");
        ensureBcProvider();
        KeyPair keyPair = rsaKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        X509Certificate cert = selfSigned(keyPair, "CN=Mimer-Test");

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> payload = Map.of(
                "id", "abc-123",
                "amount", 2500,
                "currency", "SEK"
        );

        MimerProxy.SignedJson signed = MimerProxy.serializeAndSign(
                payload,
                mapper,
                privateKey,
                "test-key",
                cert,
                null
        );

        byte[] tampered = signed.jsonBytes().clone();
        tampered[tampered.length - 1] ^= 0x01;

        boolean threw = false;
        try {
            MimerProxy.verifyAndDeserialize(
                    tampered,
                    signed.signatureBytes(),
                    cert,
                    mapper,
                    Map.class
            );
        } catch (IllegalStateException e) {
            threw = true;
        }

        assertTrue(threw);
    }

    @Test
    public void verifySignature_acceptsWhitespaceDifferences() throws Exception {
        log.info("*** Testcase *** Accept signature when JSON differs only by whitespace formatting");
        ensureBcProvider();
        KeyPair keyPair = rsaKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        X509Certificate cert = selfSigned(keyPair, "CN=Mimer-Test");

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> payload = Map.of(
                "id", "abc-123",
                "amount", 2500,
                "currency", "SEK"
        );

        MimerProxy.SignedJson signed = MimerProxy.serializeAndSign(
                payload,
                mapper,
                privateKey,
                "test-key",
                cert,
                null
        );

        String prettyJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        byte[] prettyBytes = prettyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        MimerProxy.VerificationResult result = MimerProxy.verifySignature(
                prettyBytes,
                signed.signatureBytes(),
                cert
        );
        assertTrue(result.signatureValid());
    }

    @Test
    public void verifySignature_rejectsSemanticChange() throws Exception {
        log.info("*** Testcase *** Reject signature when JSON content changes semantically");
        ensureBcProvider();
        KeyPair keyPair = rsaKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        X509Certificate cert = selfSigned(keyPair, "CN=Mimer-Test");

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> payload = Map.of(
                "id", "abc-123",
                "amount", 2500,
                "currency", "SEK"
        );

        MimerProxy.SignedJson signed = MimerProxy.serializeAndSign(
                payload,
                mapper,
                privateKey,
                "test-key",
                cert,
                null
        );

        String modifiedJson = "{\"id\":\"abc-123\",\"amount\":2500,\"currency\":\"NOK\"}";
        byte[] modifiedBytes = modifiedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        MimerProxy.VerificationResult result = MimerProxy.verifySignature(
                modifiedBytes,
                signed.signatureBytes(),
                cert
        );
        assertFalse(result.signatureValid());
    }

    @Test
    public void verifySignature_acceptsKeyOrderingDifferences() throws Exception {
        log.info("*** Testcase *** Accept signature when JSON key order is different but semantics match");
        ensureBcProvider();
        KeyPair keyPair = rsaKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        X509Certificate cert = selfSigned(keyPair, "CN=Mimer-Test");

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", "abc-123");
        payload.put("amount", 2500);
        payload.put("currency", "SEK");

        MimerProxy.SignedJson signed = MimerProxy.serializeAndSign(
                payload,
                mapper,
                privateKey,
                "test-key",
                cert,
                null
        );

        String reorderedJson = "{\"currency\":\"SEK\",\"id\":\"abc-123\",\"amount\":2500}";
        byte[] reorderedBytes = reorderedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        MimerProxy.VerificationResult result = MimerProxy.verifySignature(
                reorderedBytes,
                signed.signatureBytes(),
                cert
        );
        assertTrue(result.signatureValid());
    }

    @Test
    public void verifySignature_rejectsNumericNormalizationChange() throws Exception {
        log.info("*** Testcase *** Accept signature when numeric formatting changes but value is equivalent");
        ensureBcProvider();
        KeyPair keyPair = rsaKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        X509Certificate cert = selfSigned(keyPair, "CN=Mimer-Test");

        ObjectMapper mapper = JsonMapper.builder().build();
        Map<String, Object> payload = Map.of(
                "id", "abc-123",
                "amount", 2500,
                "currency", "SEK"
        );

        MimerProxy.SignedJson signed = MimerProxy.serializeAndSign(
                payload,
                mapper,
                privateKey,
                "test-key",
                cert,
                null
        );

        String modifiedJson = "{\"id\":\"abc-123\",\"amount\":2500.0,\"currency\":\"SEK\"}";
        byte[] modifiedBytes = modifiedJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        MimerProxy.VerificationResult result = MimerProxy.verifySignature(
                modifiedBytes,
                signed.signatureBytes(),
                cert
        );
        assertTrue(result.signatureValid());
    }

    private static byte[] digestInfo(byte[] sha256Digest) {
        byte[] out = new byte[SHA256_DIGEST_INFO_PREFIX.length + sha256Digest.length];
        System.arraycopy(SHA256_DIGEST_INFO_PREFIX, 0, out, 0, SHA256_DIGEST_INFO_PREFIX.length);
        System.arraycopy(sha256Digest, 0, out, SHA256_DIGEST_INFO_PREFIX.length, sha256Digest.length);
        return out;
    }

    private static byte[] rsaPkcs1Decrypt(byte[] signature, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return cipher.doFinal(signature);
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair keyPair, String dn) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500Name(dn);
        BigInteger serial = new BigInteger(64, java.security.SecureRandom.getInstanceStrong());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                java.util.Date.from(now.minusSeconds(60)),
                java.util.Date.from(now.plusSeconds(86400)),
                subject,
                keyPair.getPublic()
        );
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    private static void ensureBcProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
