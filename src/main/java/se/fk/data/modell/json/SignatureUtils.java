package se.fk.data.modell.json;

import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import java.security.PrivateKey;

public final class SignatureUtils {
    public static final byte[] SHA256_DIGEST_INFO_PREFIX = new byte[] {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86,
            0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };

    private SignatureUtils() {}

    public static byte[] signJcsDigestSha256RsaPkcs1(
            byte[] sha256Digest,
            PrivateKey privateKey
    ) {
        if (sha256Digest == null || sha256Digest.length != 32) {
            throw new IllegalArgumentException("sha256Digest must be 32 bytes");
        }

        try {
            byte[] digestInfo = new byte[SHA256_DIGEST_INFO_PREFIX.length + sha256Digest.length];
            System.arraycopy(SHA256_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA256_DIGEST_INFO_PREFIX.length);
            System.arraycopy(sha256Digest, 0, digestInfo, SHA256_DIGEST_INFO_PREFIX.length, sha256Digest.length);

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            return cipher.doFinal(digestInfo);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign SHA-256 digest with RSA PKCS#1 v1.5", e);
        }
    }

    public static byte[] signJcsDigestSha256RsaPkcs1(
            Object bean,
            ObjectMapper mapper,
            PrivateKey privateKey
    ) {
        byte[] digest = DigestUtils.computeDigest(bean, mapper);
        return signJcsDigestSha256RsaPkcs1(digest, privateKey);
    }

    public static byte[] signJcsDigestSha256RsaPkcs1FromJsonBytes(
            byte[] jsonBytes,
            PrivateKey privateKey
    ) {
        byte[] digest = DigestUtils.computeJcsDigestFromJsonBytes(jsonBytes);
        return signJcsDigestSha256RsaPkcs1(digest, privateKey);
    }
}
