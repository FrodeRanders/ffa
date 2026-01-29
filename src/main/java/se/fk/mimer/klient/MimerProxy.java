package se.fk.mimer.klient;

import java.time.Instant;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertPathBuilderResult;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.Cipher;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import se.fk.data.modell.json.DeserializationSnooper;
import static se.fk.data.modell.json.Modifiers.getModules;

import se.fk.data.modell.json.SignatureUtils;
import se.fk.data.modell.json.DigestUtils;

public final class MimerProxy {
    private static final ObjectMapper DEFAULT_MAPPER = buildMapper();
    private static final MimerProxy DEFAULT_INSTANCE = new MimerProxy(DEFAULT_MAPPER);

    private final ObjectMapper mapper;

    private MimerProxy(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public static MimerProxy defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public static MimerProxy withMapper(ObjectMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper must not be null");
        }
        return new MimerProxy(mapper);
    }

    private static ObjectMapper buildMapper() {
        return JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .addModules(getModules())
                .addHandler(new DeserializationSnooper())
                .build();
    }

    public record SignedJson(
            byte[] jsonBytes,
            byte[] signatureBytes,
            String signatureAlgorithm,
            String digestAlgorithm,
            String canonicalization,
            Instant signingTime,
            String keyId,
            byte[] signerCertificateDer,
            byte[][] certificateChainDer
    ) {}

    public record VerificationResult(
            boolean signatureValid,
            boolean chainValid,
            String message
    ) {}

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, privateKey, null);
    }

    public SignedJson serializeAndSign(
            Object bean,
            PrivateKey privateKey
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, privateKey, null);
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            KeyMaterialLoader.KeyMaterial keyMaterial,
            String keyId
    ) throws JacksonException {
        if (keyMaterial == null) {
            throw new IllegalArgumentException("keyMaterial must not be null");
        }
        return serializeAndSign(
                bean,
                mapper,
                keyMaterial.privateKey(),
                keyId,
                keyMaterial.signerCertificate(),
                keyMaterial.certificateChain(),
                keyMaterial.trustAnchors()
        );
    }

    public SignedJson serializeAndSign(
            Object bean,
            KeyMaterialLoader.KeyMaterial keyMaterial,
            String keyId
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, keyMaterial, keyId);
    }

    public static byte[] serialize(
            Object bean,
            ObjectMapper mapper
    ) throws JacksonException {
        return mapper.writeValueAsBytes(bean);
    }

    public byte[] serialize(
            Object bean
    ) throws JacksonException {
        return serialize(bean, mapper);
    }

    public static String serializePretty(
            Object bean,
            ObjectMapper mapper
    ) throws JacksonException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bean);
    }

    public String serializePretty(
            Object bean
    ) throws JacksonException {
        return serializePretty(bean, mapper);
    }

    public static <T> T deserialize(
            byte[] jsonBytes,
            ObjectMapper mapper,
            Class<T> type
    ) throws JacksonException {
        return mapper.readValue(jsonBytes, type);
    }

    public <T> T deserialize(
            byte[] jsonBytes,
            Class<T> type
    ) throws JacksonException {
        return deserialize(jsonBytes, mapper, type);
    }

    public static <T> T deserialize(
            String json,
            ObjectMapper mapper,
            Class<T> type
    ) throws JacksonException {
        return mapper.readValue(json, type);
    }

    public <T> T deserialize(
            String json,
            Class<T> type
    ) throws JacksonException {
        return deserialize(json, mapper, type);
    }

    public static <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate,
            ObjectMapper mapper,
            Class<T> type
    ) throws JacksonException {
        VerificationResult result = verifySignature(jsonBytes, signatureBytes, signerCertificate);
        if (!result.signatureValid()) {
            throw new IllegalStateException("Signature verification failed: " + result.message());
        }
        return deserialize(jsonBytes, mapper, type);
    }

    public <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate,
            Class<T> type
    ) throws JacksonException {
        return verifyAndDeserialize(jsonBytes, signatureBytes, signerCertificate, mapper, type);
    }

    public static <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors,
            boolean enableRevocation,
            ObjectMapper mapper,
            Class<T> type
    ) throws JacksonException {
        VerificationResult result = verifySignature(
                jsonBytes,
                signatureBytes,
                signerCertificate,
                chain,
                trustAnchors,
                enableRevocation
        );
        if (!result.signatureValid()) {
            throw new IllegalStateException("Signature verification failed: " + result.message());
        }
        if (!result.chainValid()) {
            throw new IllegalStateException("Certificate validation failed: " + result.message());
        }
        return deserialize(jsonBytes, mapper, type);
    }

    public <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors,
            boolean enableRevocation,
            Class<T> type
    ) throws JacksonException {
        return verifyAndDeserialize(
                jsonBytes,
                signatureBytes,
                signerCertificate,
                chain,
                trustAnchors,
                enableRevocation,
                mapper,
                type
        );
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey,
            String keyId
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, privateKey, keyId, null, null);
    }

    public SignedJson serializeAndSign(
            Object bean,
            PrivateKey privateKey,
            String keyId
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, privateKey, keyId, null, null);
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey,
            String keyId,
            X509Certificate signerCertificate,
            List<X509Certificate> certificateChain
    ) throws JacksonException {
        byte[] json = mapper.writeValueAsBytes(bean);
        byte[] signature = SignatureUtils.signJcsDigestSha256RsaPkcs1FromJsonBytes(json, privateKey);
        List<X509Certificate> chain = certificateChain;
        if ((chain == null || chain.isEmpty()) && signerCertificate != null) {
            chain = List.of(signerCertificate);
        }
        return new SignedJson(
                json,
                signature,
                "RSASSA-PKCS1-v1_5",
                "SHA-256",
                "JCS",
                Instant.now(),
                keyId,
                encodeCertificate(signerCertificate),
                encodeCertificateChain(chain)
        );
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey,
            String keyId,
            X509Certificate signerCertificate,
            List<X509Certificate> intermediates,
            List<X509Certificate> trustAnchors
    ) throws JacksonException {
        List<X509Certificate> builtChain = buildCertificateChain(signerCertificate, intermediates, trustAnchors);
        return serializeAndSign(bean, mapper, privateKey, keyId, signerCertificate, builtChain);
    }

    public static VerificationResult verifySignature(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate
    ) {
        return verifySignature(jsonBytes, signatureBytes, signerCertificate, null, null, false);
    }

    public static VerificationResult verifySignature(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors
    ) {
        return verifySignature(jsonBytes, signatureBytes, signerCertificate, chain, trustAnchors, false);
    }

    public static VerificationResult verifySignature(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors,
            boolean enableRevocation
    ) {
        if (jsonBytes == null || signatureBytes == null || signerCertificate == null) {
            return new VerificationResult(false, false, "Missing data for verification");
        }

        boolean signatureValid = verifyRsaPkcs1Signature(jsonBytes, signatureBytes, signerCertificate);
        boolean chainValid = validateCertificateChain(signerCertificate, chain, trustAnchors, enableRevocation);

        String message;
        if (!signatureValid) {
            message = "Signature check failed";
        } else if (!chainValid) {
            message = "Signature valid, but certificate chain validation failed or was not possible";
        } else {
            message = "Signature and certificate chain valid";
        }
        return new VerificationResult(signatureValid, chainValid, message);
    }

    private static byte[] encodeCertificate(X509Certificate certificate) {
        if (certificate == null) {
            return null;
        }
        try {
            return certificate.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode signer certificate", e);
        }
    }

    private static byte[][] encodeCertificateChain(List<X509Certificate> chain) {
        if (chain == null || chain.isEmpty()) {
            return null;
        }
        byte[][] encoded = new byte[chain.size()][];
        for (int i = 0; i < chain.size(); i++) {
            encoded[i] = encodeCertificate(chain.get(i));
        }
        return encoded;
    }

    private static boolean verifyRsaPkcs1Signature(
            byte[] jsonBytes,
            byte[] signatureBytes,
            X509Certificate signerCertificate
    ) {
        try {
            byte[] digest = DigestUtils.computeJcsDigestFromJsonBytes(jsonBytes);
            byte[] expected = digestInfo(digest);
            byte[] actual = rsaPkcs1Decrypt(signatureBytes, signerCertificate.getPublicKey());
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] rsaPkcs1Decrypt(byte[] signature, java.security.PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return cipher.doFinal(signature);
    }

    private static byte[] digestInfo(byte[] sha256Digest) {
        byte[] prefix = SignatureUtils.SHA256_DIGEST_INFO_PREFIX;
        byte[] out = new byte[prefix.length + sha256Digest.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(sha256Digest, 0, out, prefix.length, sha256Digest.length);
        return out;
    }

    private static boolean validateCertificateChain(
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors,
            boolean enableRevocation
    ) {
        if (signerCertificate == null) {
            return false;
        }

        if (trustAnchors == null || trustAnchors.isEmpty()) {
            return false;
        }

        if (isTrustAnchor(signerCertificate, trustAnchors)) {
            return true;
        }

        try {
            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate cert : trustAnchors) {
                anchors.add(new TrustAnchor(cert, null));
            }

            List<X509Certificate> effectiveChain = chain == null || chain.isEmpty()
                    ? List.of(signerCertificate)
                    : chain;

            List<X509Certificate> pathCerts = new ArrayList<>();
            for (X509Certificate cert : effectiveChain) {
                if (!isTrustAnchor(cert, trustAnchors)) {
                    pathCerts.add(cert);
                }
            }

            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.cert.CertPath path = cf.generateCertPath(pathCerts);
            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(enableRevocation);

            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult) validator.validate(path, params);
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<X509Certificate> buildCertificateChain(
            X509Certificate signerCertificate,
            List<X509Certificate> intermediates,
            List<X509Certificate> trustAnchors
    ) {
        if (signerCertificate == null) {
            return null;
        }

        try {
            Set<TrustAnchor> anchors = new HashSet<>();
            if (trustAnchors != null) {
                for (X509Certificate cert : trustAnchors) {
                    anchors.add(new TrustAnchor(cert, null));
                }
            }
            if (intermediates != null) {
                for (X509Certificate cert : intermediates) {
                    if (isSelfSigned(cert)) {
                        anchors.add(new TrustAnchor(cert, null));
                    }
                }
            }

            if (anchors.isEmpty()) {
                return List.of(signerCertificate);
            }

            Collection<X509Certificate> storeCerts = new ArrayList<>();
            storeCerts.add(signerCertificate);
            if (intermediates != null) {
                storeCerts.addAll(intermediates);
            }
            CertStore store = CertStore.getInstance("Collection", new CollectionCertStoreParameters(storeCerts));

            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(signerCertificate);

            PKIXBuilderParameters params = new PKIXBuilderParameters(anchors, selector);
            params.addCertStore(store);
            params.setRevocationEnabled(false);

            CertPathBuilder builder = CertPathBuilder.getInstance("PKIX");
            CertPathBuilderResult result = builder.build(params);

            List<X509Certificate> chain = new ArrayList<>();
            for (java.security.cert.Certificate cert : result.getCertPath().getCertificates()) {
                chain.add((X509Certificate) cert);
            }
            TrustAnchor anchor = ((java.security.cert.PKIXCertPathBuilderResult) result).getTrustAnchor();
            if (anchor.getTrustedCert() != null) {
                chain.add(anchor.getTrustedCert());
            }
            return chain;
        } catch (Exception e) {
            return List.of(signerCertificate);
        }
    }

    private static boolean isSelfSigned(X509Certificate certificate) {
        try {
            certificate.verify(certificate.getPublicKey());
            return certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isTrustAnchor(X509Certificate certificate, List<X509Certificate> trustAnchors) {
        if (certificate == null || trustAnchors == null) {
            return false;
        }
        for (X509Certificate anchor : trustAnchors) {
            if (certificate.equals(anchor)) {
                return true;
            }
        }
        return false;
    }
}
