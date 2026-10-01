package se.fk.data.modell.utils;

import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;

/** Stabil kontrollsumma för jämförelse av objektinnehåll, separat från dokumentsignaturen. */
public final class DigestUtils {
    private DigestUtils() {}
    public static byte[] computeDigest(Object bean, ObjectMapper mapper) {
        try {
            byte[] canonical = JcsUtils.canonicalize(mapper.writeValueAsBytes(bean));
            return MessageDigest.getInstance("SHA-256").digest(canonical);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Kontrollsumman kunde inte beräknas", e);
        }
    }
}
