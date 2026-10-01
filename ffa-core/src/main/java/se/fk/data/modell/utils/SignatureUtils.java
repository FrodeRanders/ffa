package se.fk.data.modell.utils;

import java.security.PrivateKey;

import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

/** Gemensam signeringspolicy för PoC:en: JCS och RSA-PSS med SHA-512. */
public final class SignatureUtils {
    private static final PSSParameterSpec PARAMETERS =
            new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1);
    private SignatureUtils() {}

    /** Signerar den kanoniska representationen med infrastrukturens privata nyckel. */
    public static byte[] sign(byte[] json, PrivateKey key) {
        try {
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(PARAMETERS);
            signature.initSign(key);
            signature.update(JcsUtils.canonicalize(json));
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("Dokumentet kunde inte signeras", e);
        }
    }

    /** Verifierar mot en redan betrodd publik nyckel; felaktiga underlag ger false. */
    public static boolean verify(byte[] json, byte[] signed, PublicKey key) {
        try {
            Signature verifier = Signature.getInstance("RSASSA-PSS");
            verifier.setParameter(PARAMETERS);
            verifier.initVerify(key);
            verifier.update(JcsUtils.canonicalize(json));
            return verifier.verify(signed);
        } catch (Exception e) {
            return false;
        }
    }
}
