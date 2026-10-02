package se.fk.mimer.runtime;

import se.fk.data.modell.internal.LifecycleCopies;
import se.fk.data.modell.json.DeserializationSnooper;
import se.fk.data.modell.json.Modifiers;
import se.fk.data.modell.utils.SignatureUtils;
import se.fk.data.modell.v1.Yrkande;
import se.fk.mimer.api.Yrkanden;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.security.PrivateKey;

import java.security.PublicKey;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Gemensam gräns: modellvalidering, livscykel, JSON, signering, verifiering och migrering.
 * Den betrodda verifieringsnyckeln konfigureras av infrastrukturen, aldrig från dokumentet.
 */
public final class ForvaltadeYrkanden<T extends Yrkande> implements Yrkanden<T> {
    private final Class<T> typ;
    private final Dokumentlager lager;
    private final Dokumentkalla backend;
    private final PrivateKey signeringsnyckel;
    private final PublicKey verifieringsnyckel;
    private final ModellCodec codec = ModellCodec.instance();

    // Ingen livscykelserialisering vid kopiering: originalet får inga metadataändringar.
    private final ObjectMapper kopiering = JsonMapper.builder()
            .addModule(Modifiers.ANNOTATED_CLASSES_MODULE)
            .addModule(Modifiers.ANNOTATED_PROPERTIES_MODULE)
            .addHandler(new DeserializationSnooper()).build();

    /** Kopplar en modelltyp till lagring och nycklar som infrastrukturen redan litar på. */
    public ForvaltadeYrkanden(Class<T> typ, Dokumentlager lager,
                            PrivateKey signeringsnyckel, PublicKey verifieringsnyckel) {
        this(typ, lager, null, signeringsnyckel, verifieringsnyckel);
    }

    /** Backend läses bara vid cachemiss. Verifiering sker före återställning av cachen. */
    public ForvaltadeYrkanden(Class<T> typ, Dokumentlager lager, Dokumentkalla backend,
                            PrivateKey signeringsnyckel, PublicKey verifieringsnyckel) {
        this.backend = backend;
        this.typ = Objects.requireNonNull(typ);
        this.lager = Objects.requireNonNull(lager);
        this.signeringsnyckel = Objects.requireNonNull(signeringsnyckel);
        this.verifieringsnyckel = Objects.requireNonNull(verifieringsnyckel);
    }

    /** Återläsning lämnar bara ut ett verifierat och modellvaliderat objekt. */
    @Override
    public synchronized T las(String id) {
        LagratDokument dokument = hamtaObjekt(id);
        if (dokument == null)
            throw new NoSuchElementException("Yrkande saknas: " + id);
        return lasVerifierat(id, dokument);
    }

    /**
     * Förbereder ett nytt lagrat tillstånd utan att ändra det inlämnade objektet.
     * Versionskontrollen här kompletteras av den beständiga adapterns transaktionskontroll.
     */
    @Override
    public synchronized T lagra(T yrkande) {
        // Bakåtkompatibel bekvämlighet för äldre exempel; processmotorn anger ett eget id.
        return lagra(yrkande.getId(), yrkande);
    }

    @Override
    public synchronized T lasProcess(String korrelationsId) {
        var leverans = lager.lasProcess(korrelationsId);
        if (leverans == null && backend != null) {
            leverans = backend.lasProcess(korrelationsId);
            if (leverans != null) {
                if (!korrelationsId.equals(leverans.korrelationsId()))
                    throw new IllegalStateException("Backend returnerade fel process");
                aterstallVerifierat(leverans);
                leverans = lager.lasProcess(korrelationsId);
            }
        }
        if (leverans == null) throw new NoSuchElementException("Process saknas: " + korrelationsId);
        return lasVerifierat(leverans.objektId(), leverans.dokument());
    }

    @Override
    public synchronized T lasLeverans(String dataleveransId) {
        var id = java.util.UUID.fromString(dataleveransId);
        var leverans = lager.lasLeverans(id);
        if (leverans == null && backend != null) {
            leverans = backend.lasLeverans(id);
            if (leverans != null) {
                if (!id.equals(leverans.id()))
                    throw new IllegalStateException("Backend returnerade fel dataleverans");
                aterstallVerifierat(leverans);
            }
        }
        if (leverans == null) throw new NoSuchElementException("Dataleverans saknas: " + dataleveransId);
        return lasVerifierat(leverans.objektId(), leverans.dokument());
    }

    @Override
    public synchronized T lagra(String korrelationsId, T yrkande) {
        if (korrelationsId == null || korrelationsId.isBlank())
            throw new IllegalArgumentException("Korrelations-id måste anges");
        Modellvalidering.kontrollera(yrkande);

        // Jämför med det verifierade tillståndet, så att ett äldre objekt inte skriver över ett nyare.
        LagratDokument tidigare = hamtaObjekt(yrkande.getId());
        int lagradVersion = tidigare == null ? 0 : lasVerifierat(yrkande.getId(), tidigare).getVersion();
        if (lagradVersion != yrkande.getVersion()) {
            throw new IllegalStateException("Yrkandet har en inaktuell version; läs om före lagring");
        }

        // Kopiera verksamhetsdata och bevara den tidigare kontrollsumman för ändringsjämförelsen.
        T kopia = kopiering.readValue(kopiering.writeValueAsBytes(yrkande), typ);
        LifecycleCopies.copy(yrkande, kopia);

        // Livscykeländringar görs i kopian. Formatversion och signatur tillhör representationen.
        byte[] json = codec.serialize(kopia);
        byte[] signatur = SignatureUtils.sign(json, signeringsnyckel);
        LagratDokument dokument = new LagratDokument(json, signatur);

        // Kontrollera även den färdiga representationen innan lagringsadaptern anropas.
        T lagrat = lasVerifierat(kopia.getId(), dokument);

        // Returnera det nya objekttillståndet först när lagringsadaptern har lyckats.
        lager.lagra(new Dataleverans(Dataleverans.nyttId(), korrelationsId, kopia.getId(),
                lagradVersion, lagrat.getVersion(), java.time.Instant.now(), dokument));
        return lagrat;
    }

    private LagratDokument hamtaObjekt(String id) {
        var dokument = lager.las(id);
        if (dokument == null && backend != null) {
            var leverans = backend.lasObjekt(id);
            if (leverans != null) {
                if (!id.equals(leverans.objektId()))
                    throw new IllegalStateException("Backend returnerade fel objekt");
                aterstallVerifierat(leverans);
                dokument = lager.las(id);
            }
        }
        return dokument;
    }

    private void aterstallVerifierat(Dataleverans leverans) {
        var objekt = lasVerifierat(leverans.objektId(), leverans.dokument());
        if (objekt.getVersion() != leverans.objektVersion())
            throw new IllegalStateException("Leveransens version stämmer inte med dokumentet");
        lager.aterstall(leverans);
    }

    private T lasVerifierat(String id, LagratDokument dokument) {
        byte[] json = dokument.json();
        if (!SignatureUtils.verify(json, dokument.signatur(), verifieringsnyckel)) {
            throw new IllegalStateException("Dokumentets signatur är ogiltig");
        }

        // Verifiera originalet först. Migrering får inte ändra det signerade underlaget.
        var root = kopiering.readTree(json);
        if (!root.isObject() || !typ.getName().equals(root.path("@type").asString())) {
            throw new IllegalArgumentException("Dokumentets typ stämmer inte med yrkandemodellen");
        }

        // Codecen migrerar formatet före bindning. Signaturen kontrollerades mot originalet ovan.
        T yrkande = codec.deserialize(json, typ);
        if (!id.equals(yrkande.getId()))
            throw new IllegalStateException("Dokumentets identitet stämmer inte med lagringsnyckeln");

        Modellvalidering.kontrollera(yrkande);
        return yrkande;
    }
}
