package se.fk.mimer.pipeline;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import se.fk.data.modell.utils.SignatureUtils;
import se.fk.mimer.runtime.Dataleverans;
import se.fk.mimer.runtime.LagratDokument;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;

/** Metadata binds till verifierad JSON; dokumentet omserialiseras aldrig på transportvägen. */
final class Leveransformat {
    private Leveransformat() {}

    static Dataleverans las(ConsumerRecord<String, byte[]> record, PublicKey key) {
        var delivery = new Dataleverans(UUID.fromString(text(record, "dataleverans-id")),
                text(record, "korrelations-id"), text(record, "objekt-id"),
                Long.parseLong(text(record, "forvantad-version")), Long.parseLong(text(record, "objekt-version")),
                Instant.parse(text(record, "skapad")), new LagratDokument(record.value(), header(record, "signatur")));
        if (!delivery.korrelationsId().equals(record.key())
                || !"JCS+RSA-PSS-SHA512".equals(text(record, "signatur-algoritm")))
            throw new IllegalArgumentException("Kafka-nyckel eller signaturalgoritm stämmer inte");
        verifiera(delivery, key);
        return delivery;
    }

    static void verifiera(Dataleverans delivery, PublicKey key) {
        if (!SignatureUtils.verify(delivery.dokument().json(), delivery.dokument().signatur(), key))
            throw new IllegalArgumentException("Ogiltig dokumentsignatur");
        var root = JsonMapper.builder().build().readTree(delivery.dokument().json());
        if (!root.isObject() || !delivery.objektId().equals(root.path("id").asString())
                || !root.path("version").isIntegralNumber()
                || root.path("version").asLong() != delivery.objektVersion())
            throw new IllegalArgumentException("JSON och leveransmetadata har olika identitet eller version");
    }

    private static byte[] header(ConsumerRecord<String, byte[]> record, String name) {
        var values = record.headers().headers(name).iterator();
        if (!values.hasNext()) throw new IllegalArgumentException("Kafka-header saknas: " + name);
        byte[] value = values.next().value();
        if (value == null || values.hasNext()) throw new IllegalArgumentException("Ogiltig eller dubblerad header: " + name);
        return value;
    }

    private static String text(ConsumerRecord<String, byte[]> record, String name) {
        return new String(header(record, name), StandardCharsets.UTF_8);
    }

    // S3-metadata är HTTP-headers. Base64 bevarar även svenska tecken i process- och objekt-id.
    static Map<String, String> metadata(Dataleverans d) {
        return Map.of("dataleverans-id", d.id().toString(), "korrelations-id", encode(d.korrelationsId()),
                "objekt-id", encode(d.objektId()), "objekt-version", Long.toString(d.objektVersion()),
                "forvantad-version", Long.toString(d.forvantadVersion()), "skapad", d.skapad().toString(),
                "signatur", Base64.getEncoder().encodeToString(d.dokument().signatur()),
                "signatur-algoritm", "JCS+RSA-PSS-SHA512");
    }

    static Dataleverans franObjekt(byte[] bytes, Map<String, String> m) {
        if (!"JCS+RSA-PSS-SHA512".equals(m.get("signatur-algoritm")))
            throw new IllegalArgumentException("Okänd signaturalgoritm i objektmetadata");
        return new Dataleverans(UUID.fromString(m.get("dataleverans-id")), decode(m.get("korrelations-id")),
                decode(m.get("objekt-id")), Long.parseLong(m.get("forvantad-version")),
                Long.parseLong(m.get("objekt-version")), Instant.parse(m.get("skapad")),
                new LagratDokument(bytes, Base64.getDecoder().decode(m.get("signatur"))));
    }

    static boolean samma(Dataleverans a, Dataleverans b) {
        return metadata(a).equals(metadata(b)) && Arrays.equals(a.dokument().json(), b.dokument().json());
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
