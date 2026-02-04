package se.fk.mimer.klient;

import se.fk.data.modell.json.DeserializationSnooper;
import se.fk.data.modell.json.DigestUtils;
import se.fk.data.modell.json.SignatureUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.security.cert.*;
import java.time.Instant;
import java.util.*;

import static se.fk.data.modell.json.Modifiers.getModules;

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

    public enum SignatureEncoding {
        BASE64("base64"),
        BASE64_URL("base64url"),
        HEX("hex"),
        PEM("pem");

        private static final String PEM_BEGIN = "-----BEGIN SIGNATURE-----";
        private static final String PEM_END = "-----END SIGNATURE-----";
        private final String wireName;

        SignatureEncoding(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
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
    ) {
        public String signatureText() {
            return signatureText(SignatureEncoding.BASE64_URL);
        }

        public String signatureText(SignatureEncoding encoding) {
            return encodeSignature(signatureBytes, encoding);
        }

        public static byte[] decodeSignature(String signatureText, SignatureEncoding encoding) {
            return decodeSignatureText(signatureText, encoding);
        }
    }

    public record VerificationResult(
            boolean signatureValid,
            boolean chainValid,
            String message
    ) {}

    public static String encodeSignature(byte[] signatureBytes, SignatureEncoding encoding) {
        if (signatureBytes == null) {
            throw new IllegalArgumentException("signatureBytes must not be null");
        }
        SignatureEncoding effective = encoding == null ? SignatureEncoding.BASE64_URL : encoding;
        return switch (effective) {
            case BASE64 -> Base64.getEncoder().encodeToString(signatureBytes);
            case BASE64_URL -> Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            case HEX -> toHex(signatureBytes);
            case PEM -> toPem(signatureBytes);
        };
    }

    public static byte[] decodeSignatureText(String signatureText, SignatureEncoding encoding) {
        if (signatureText == null || signatureText.isBlank()) {
            throw new IllegalArgumentException("signatureText must not be null/blank");
        }
        SignatureEncoding effective = encoding == null ? SignatureEncoding.BASE64_URL : encoding;
        return switch (effective) {
            case BASE64 -> Base64.getDecoder().decode(signatureText);
            case BASE64_URL -> Base64.getUrlDecoder().decode(signatureText);
            case HEX -> fromHex(signatureText);
            case PEM -> fromPem(signatureText);
        };
    }

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
        return serializeAndSign(bean, mapper, keyMaterial, keyId, SignatureUtils.DigestAlgorithm.SHA_512);
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            KeyMaterialLoader.KeyMaterial keyMaterial,
            String keyId,
            SignatureUtils.DigestAlgorithm digestAlgorithm
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
                keyMaterial.trustAnchors(),
                digestAlgorithm
        );
    }

    public SignedJson serializeAndSign(
            Object bean,
            KeyMaterialLoader.KeyMaterial keyMaterial,
            String keyId
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, keyMaterial, keyId);
    }

    public SignedJson serializeAndSign(
            Object bean,
            KeyMaterialLoader.KeyMaterial keyMaterial,
            String keyId,
            SignatureUtils.DigestAlgorithm digestAlgorithm
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, keyMaterial, keyId, digestAlgorithm);
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

    public static <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            String signatureText,
            SignatureEncoding signatureEncoding,
            X509Certificate signerCertificate,
            ObjectMapper mapper,
            Class<T> type
    ) throws JacksonException {
        VerificationResult result = verifySignature(jsonBytes, signatureText, signatureEncoding, signerCertificate);
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

    public <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            String signatureText,
            SignatureEncoding signatureEncoding,
            X509Certificate signerCertificate,
            Class<T> type
    ) throws JacksonException {
        return verifyAndDeserialize(jsonBytes, signatureText, signatureEncoding, signerCertificate, mapper, type);
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

    public static <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            String signatureText,
            SignatureEncoding signatureEncoding,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors,
            boolean enableRevocation,
            ObjectMapper mapper,
            Class<T> type
    ) throws JacksonException {
        VerificationResult result = verifySignature(
                jsonBytes,
                signatureText,
                signatureEncoding,
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

    public <T> T verifyAndDeserialize(
            byte[] jsonBytes,
            String signatureText,
            SignatureEncoding signatureEncoding,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors,
            boolean enableRevocation,
            Class<T> type
    ) throws JacksonException {
        return verifyAndDeserialize(
                jsonBytes,
                signatureText,
                signatureEncoding,
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
        return serializeAndSign(
                bean,
                mapper,
                privateKey,
                keyId,
                null,
                null,
                SignatureUtils.DigestAlgorithm.SHA_512
        );
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey,
            String keyId,
            SignatureUtils.DigestAlgorithm digestAlgorithm
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, privateKey, keyId, null, null, digestAlgorithm);
    }

    public SignedJson serializeAndSign(
            Object bean,
            PrivateKey privateKey,
            String keyId
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, privateKey, keyId);
    }

    public SignedJson serializeAndSign(
            Object bean,
            PrivateKey privateKey,
            String keyId,
            SignatureUtils.DigestAlgorithm digestAlgorithm
    ) throws JacksonException {
        return serializeAndSign(bean, mapper, privateKey, keyId, digestAlgorithm);
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey,
            String keyId,
            X509Certificate signerCertificate,
            List<X509Certificate> certificateChain
    ) throws JacksonException {
        return serializeAndSign(
                bean,
                mapper,
                privateKey,
                keyId,
                signerCertificate,
                certificateChain,
                SignatureUtils.DigestAlgorithm.SHA_512
        );
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey,
            String keyId,
            X509Certificate signerCertificate,
            List<X509Certificate> certificateChain,
            SignatureUtils.DigestAlgorithm digestAlgorithm
    ) throws JacksonException {
        SignatureUtils.DigestAlgorithm effective = digestAlgorithm == null
                ? SignatureUtils.DigestAlgorithm.SHA_512
                : digestAlgorithm;
        byte[] json = mapper.writeValueAsBytes(bean);
        byte[] signature = SignatureUtils.signJcsDigestRsaPkcs1FromJsonBytes(json, privateKey, effective);
        List<X509Certificate> chain = certificateChain;
        if ((chain == null || chain.isEmpty()) && signerCertificate != null) {
            chain = List.of(signerCertificate);
        }
        return new SignedJson(
                json,
                signature,
                "RSASSA-PKCS1-v1_5",
                effective.jsonName(),
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
        return serializeAndSign(
                bean,
                mapper,
                privateKey,
                keyId,
                signerCertificate,
                intermediates,
                trustAnchors,
                SignatureUtils.DigestAlgorithm.SHA_512
        );
    }

    public static SignedJson serializeAndSign(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey,
            String keyId,
            X509Certificate signerCertificate,
            List<X509Certificate> intermediates,
            List<X509Certificate> trustAnchors,
            SignatureUtils.DigestAlgorithm digestAlgorithm
    ) throws JacksonException {
        List<X509Certificate> builtChain = buildCertificateChain(signerCertificate, intermediates, trustAnchors);
        return serializeAndSign(bean, mapper, privateKey, keyId, signerCertificate, builtChain, digestAlgorithm);
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
            String signatureText,
            SignatureEncoding signatureEncoding,
            X509Certificate signerCertificate
    ) {
        return verifySignature(jsonBytes, signatureText, signatureEncoding, signerCertificate, null, null, false);
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
            String signatureText,
            SignatureEncoding signatureEncoding,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors
    ) {
        return verifySignature(
                jsonBytes,
                signatureText,
                signatureEncoding,
                signerCertificate,
                chain,
                trustAnchors,
                false
        );
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

    public static VerificationResult verifySignature(
            byte[] jsonBytes,
            String signatureText,
            SignatureEncoding signatureEncoding,
            X509Certificate signerCertificate,
            List<X509Certificate> chain,
            List<X509Certificate> trustAnchors,
            boolean enableRevocation
    ) {
        final byte[] signatureBytes;
        try {
            signatureBytes = decodeSignatureText(signatureText, signatureEncoding);
        } catch (IllegalArgumentException e) {
            return new VerificationResult(false, false, "Invalid signature encoding/text");
        }
        return verifySignature(
                jsonBytes,
                signatureBytes,
                signerCertificate,
                chain,
                trustAnchors,
                enableRevocation
        );
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
            byte[] actual = rsaPkcs1Decrypt(signatureBytes, signerCertificate.getPublicKey());
            SignatureUtils.DigestAlgorithm digestAlgorithm = SignatureUtils.detectDigestAlgorithmFromDigestInfo(actual);
            if (digestAlgorithm == null) {
                return false;
            }
            byte[] digest = DigestUtils.computeJcsDigestFromJsonBytes(jsonBytes, digestAlgorithm);
            byte[] expected = SignatureUtils.digestInfo(digest, digestAlgorithm);
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

    private static String toHex(byte[] value) {
        char[] out = new char[value.length * 2];
        final char[] hex = "0123456789abcdef".toCharArray();
        for (int i = 0; i < value.length; i++) {
            int v = value[i] & 0xFF;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0F];
        }
        return new String(out);
    }

    private static byte[] fromHex(String hex) {
        String cleaned = hex.replaceAll("\\s+", "");
        if ((cleaned.length() % 2) != 0) {
            throw new IllegalArgumentException("HEX value must have even length");
        }
        byte[] out = new byte[cleaned.length() / 2];
        for (int i = 0; i < cleaned.length(); i += 2) {
            int hi = Character.digit(cleaned.charAt(i), 16);
            int lo = Character.digit(cleaned.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("HEX value contains invalid characters");
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private static String toPem(byte[] signatureBytes) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(signatureBytes);
        return SignatureEncoding.PEM_BEGIN + "\n" + base64 + "\n" + SignatureEncoding.PEM_END;
    }

    private static byte[] fromPem(String pem) {
        String body = pem
                .replace(SignatureEncoding.PEM_BEGIN, "")
                .replace(SignatureEncoding.PEM_END, "")
                .replaceAll("\\s+", "");
        if (body.isEmpty()) {
            throw new IllegalArgumentException("PEM value does not contain a signature body");
        }
        return Base64.getDecoder().decode(body);
    }
}
