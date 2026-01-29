package se.fk.mimer.klient;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;

import static org.junit.Assert.*;

public class KeyMaterialLoaderTest {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void loadFromPemStrings_parsesKeyAndCert() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        X509Certificate cert = selfSigned(keyPair, "CN=Test");

        String keyPem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String certPem = pem("CERTIFICATE", cert.getEncoded());

        KeyMaterialLoader.KeyMaterial material =
                KeyMaterialLoader.loadFromPemStrings(keyPem, certPem, null, null);

        assertNotNull(material.privateKey());
        assertNotNull(material.signerCertificate());
        assertEquals(cert.getSubjectX500Principal(), material.signerCertificate().getSubjectX500Principal());
        assertEquals(cert.getPublicKey(), material.signerCertificate().getPublicKey());
    }

    @Test
    public void loadFromFiles_parsesChainAndTrustAnchors() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        X509Certificate cert1 = selfSigned(keyPair, "CN=ChainOne");
        X509Certificate cert2 = selfSigned(keyPair, "CN=ChainTwo");

        String keyPem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String certPem = pem("CERTIFICATE", cert1.getEncoded());
        String chainPem = pem("CERTIFICATE", cert1.getEncoded()) + "\n" + pem("CERTIFICATE", cert2.getEncoded());
        String trustPem = pem("CERTIFICATE", cert2.getEncoded());

        Path dir = Files.createTempDirectory("mimer-keymaterial");
        Path keyPath = Files.writeString(dir.resolve("key.pem"), keyPem);
        Path certPath = Files.writeString(dir.resolve("cert.pem"), certPem);
        Path chainPath = Files.writeString(dir.resolve("chain.pem"), chainPem);
        Path trustPath = Files.writeString(dir.resolve("trust.pem"), trustPem);

        KeyMaterialLoader.KeyMaterial material =
                KeyMaterialLoader.loadFromFiles(keyPath, certPath, chainPath, trustPath);

        assertNotNull(material.privateKey());
        assertNotNull(material.signerCertificate());
        assertNotNull(material.certificateChain());
        assertEquals(2, material.certificateChain().size());
        assertNotNull(material.trustAnchors());
        assertEquals(1, material.trustAnchors().size());
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

    private static String pem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
