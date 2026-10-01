package se.fk.demo;

import se.fk.hundbidrag.Applikation;
import se.fk.hundbidrag.modell.YrkandeOmHundbidrag;
import se.fk.mimer.runtime.ForvaltadeYrkanden;
import se.fk.mimer.runtime.Minneslager;

import java.nio.file.Files;

import java.nio.file.Path;
import java.security.KeyPairGenerator;

/** Infrastruktur för den körbara demon. Nycklarna och minneslagret är tillfälliga. */
public final class Demo {
    private Demo() {}

    public static void main(String[] args) throws Exception {
        // Endast uppstartskoden känner till nycklar och lagringsadapter.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var nycklar = generator.generateKeyPair();

        var lager = new Minneslager();
        var yrkanden = new ForvaltadeYrkanden<>(YrkandeOmHundbidrag.class, lager,
                nycklar.getPrivate(), nycklar.getPublic());

        // Förmånen får objektgränsen och kan därmed handlägga utan transport- eller nyckelkunskap.
        var yrkande = new Applikation(yrkanden).handlagg();
        System.out.printf("Lagring och återläsning klara. Yrkande: version %d, ersättning: version %d, beslut: version %d.%n",
                yrkande.getVersion(), yrkande.produceratResultat.iterator().next().getVersion(), yrkande.beslut.getVersion());

        Path ut = Path.of(args.length == 0 ? "target/demo-yrkande.json" : args[0]);
        if (ut.toAbsolutePath().getParent() != null)
            Files.createDirectories(ut.toAbsolutePath().getParent());

        // Endast infrastrukturen exporterar representationen till grafdemonstrationen.
        yrkanden.las(yrkande.getId()); // kontrollera det lagrade dokumentet före export
        Files.write(ut, lager.las(yrkande.getId()).json());
        System.out.println("Verifierat underlag för separat grafprojektion: " + ut);
    }
}
