package se.fk.data.modell.utils;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

public final class SignatureUtils {
    public static final byte[] SHA256_DIGEST_INFO_PREFIX = new byte[] {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86,
            0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };
    public static final byte[] SHA512_DIGEST_INFO_PREFIX = new byte[] {
            0x30, 0x51, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86,
            0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x03, 0x05, 0x00, 0x04, 0x40
    };

    // SHA_512 is preferred!
    public enum DigestAlgorithm {
        SHA_256("SHA-256", "SHA-256", SHA256_DIGEST_INFO_PREFIX, 32),
        SHA_512("SHA-512", "SHA-512", SHA512_DIGEST_INFO_PREFIX, 64);

        private final String jcaName;
        private final String jsonName;
        private final byte[] digestInfoPrefix;
        private final int digestLengthBytes;

        DigestAlgorithm(
                String jcaName,
                String jsonName,
                byte[] digestInfoPrefix,
                int digestLengthBytes
        ) {
            this.jcaName = jcaName;
            this.jsonName = jsonName;
            this.digestInfoPrefix = digestInfoPrefix;
            this.digestLengthBytes = digestLengthBytes;
        }

        public String jcaName() {
            return jcaName;
        }

        public String jsonName() {
            return jsonName;
        }

        public byte[] digestInfoPrefix() {
            return digestInfoPrefix;
        }

        public int digestLengthBytes() {
            return digestLengthBytes;
        }
    }

    /*
     * RFC 8017 (from 2016) states that RSASSA PKCS #1 v1.5 is deprecated
     * and states that RSASSA-PSS is REQUIRED in new applications.
     */
    public enum SignatureScheme {
        RSASSA_PSS("RSASSA-PSS");

        private final String jsonName;

        SignatureScheme(String jsonName) {
            this.jsonName = jsonName;
        }

        public String jsonName() {
            return jsonName;
        }
    }

    private SignatureUtils() {}

    public static byte[] signJcsRsaFromJsonBytes(
            byte[] jsonBytes,
            PrivateKey privateKey,
            SignatureScheme signatureScheme,
            DigestAlgorithm digestAlgorithm
    ) {
        SignatureScheme effectiveScheme = signatureScheme == null
                ? SignatureScheme.RSASSA_PSS
                : signatureScheme;
        DigestAlgorithm effectiveDigest = digestAlgorithm == null ? DigestAlgorithm.SHA_512 : digestAlgorithm;
        return switch (effectiveScheme) {
            case RSASSA_PSS -> signJcsRsaPssFromJsonBytes(jsonBytes, privateKey, effectiveDigest);
        };
    }

    public static byte[] signJcsRsaPssFromJsonBytes(
            byte[] jsonBytes,
            PrivateKey privateKey,
            DigestAlgorithm digestAlgorithm
    ) {
        DigestAlgorithm effective = digestAlgorithm == null ? DigestAlgorithm.SHA_512 : digestAlgorithm;
        try {
            byte[] canonical = JcsUtils.canonicalize(jsonBytes);
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(pssParameterSpec(effective));
            signature.initSign(privateKey);
            signature.update(canonical);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JCS payload with " + effective.jsonName() + " RSASSA-PSS", e);
        }
    }

    public static boolean verifyJcsRsaPssFromJsonBytes(
            byte[] jsonBytes,
            byte[] signatureBytes,
            PublicKey publicKey,
            DigestAlgorithm digestAlgorithm
    ) {
        DigestAlgorithm effective = digestAlgorithm == null ? DigestAlgorithm.SHA_512 : digestAlgorithm;
        try {
            byte[] canonical = JcsUtils.canonicalize(jsonBytes);
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(pssParameterSpec(effective));
            verifier.initVerify(publicKey);
            verifier.update(canonical);
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] digestInfo(byte[] digest, DigestAlgorithm digestAlgorithm) {
        DigestAlgorithm effective = digestAlgorithm == null ? DigestAlgorithm.SHA_512 : digestAlgorithm;
        if (digest == null || digest.length != effective.digestLengthBytes()) {
            throw new IllegalArgumentException(
                    "digest must be " + effective.digestLengthBytes() + " bytes for " + effective.jsonName()
            );
        }
        byte[] prefix = effective.digestInfoPrefix();
        byte[] out = new byte[prefix.length + digest.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(digest, 0, out, prefix.length, digest.length);
        return out;
    }

    public static DigestAlgorithm detectDigestAlgorithmFromDigestInfo(byte[] digestInfo) {
        if (startsWith(digestInfo, SHA512_DIGEST_INFO_PREFIX)) {
            return DigestAlgorithm.SHA_512;
        }
        if (startsWith(digestInfo, SHA256_DIGEST_INFO_PREFIX)) {
            return DigestAlgorithm.SHA_256;
        }
        return null;
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value == null || prefix == null || value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static PSSParameterSpec pssParameterSpec(DigestAlgorithm digestAlgorithm) {
        MGF1ParameterSpec mgf1Spec = switch (digestAlgorithm) {
            case SHA_256 -> MGF1ParameterSpec.SHA256;
            case SHA_512 -> MGF1ParameterSpec.SHA512;
        };
        return new PSSParameterSpec(
                digestAlgorithm.jcaName(),
                "MGF1",
                mgf1Spec,
                digestAlgorithm.digestLengthBytes(),
                1
        );
    }
}
