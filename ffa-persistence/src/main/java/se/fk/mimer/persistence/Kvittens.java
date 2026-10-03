package se.fk.mimer.persistence;

import se.fk.mimer.runtime.Dataleverans;
import tools.jackson.databind.json.JsonMapper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Positivt infrastrukturellt kvitto. Fingeravtrycket binder kvittot till hela leveransen. */
public record Kvittens(String topic, UUID dataleveransId, String korrelationsId,
                       Leveranssteg steg, Instant skapad, String fingeravtryck) {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public Kvittens {
        Objects.requireNonNull(dataleveransId);
        Objects.requireNonNull(skapad);
        if (topic == null || topic.isBlank() || korrelationsId == null || korrelationsId.isBlank()
                || (steg != Leveranssteg.RADATA_LAGRADE && steg != Leveranssteg.GRAFBEHANDLAD)
                || fingeravtryck == null || !fingeravtryck.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Ogiltig kvittens");
    }

    public static Kvittens skapa(String topic, Dataleverans d, Leveranssteg steg) {
        return new Kvittens(topic, d.id(), d.korrelationsId(), steg, Instant.now(), fingeravtryck(d));
    }

    public boolean avser(Dataleverans d) {
        return dataleveransId.equals(d.id()) && korrelationsId.equals(d.korrelationsId())
                && fingeravtryck.equals(fingeravtryck(d));
    }

    /** Längdprefix gör sammansättningen entydig även för id med skiljetecken. */
    public static String fingeravtryck(Dataleverans d) {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(d.id().toString(), d.korrelationsId(), d.objektId(),
                    Long.toString(d.forvantadVersion()), Long.toString(d.objektVersion()), d.skapad().toString()))
                mata(hash, value.getBytes(StandardCharsets.UTF_8));
            mata(hash, d.dokument().json());
            mata(hash, d.dokument().signatur());
            return HexFormat.of().formatHex(hash.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void mata(MessageDigest hash, byte[] bytes) {
        hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        hash.update(bytes);
    }

    public byte[] json() {
        return JSON.writeValueAsBytes(Map.of("formatVersion", 1, "topic", topic,
                "dataleveransId", dataleveransId.toString(), "korrelationsId", korrelationsId,
                "steg", steg.name(), "skapad", skapad.toString(), "fingeravtryck", fingeravtryck));
    }

    public static Kvittens las(byte[] bytes) {
        var root = JSON.readTree(bytes);
        if (!root.isObject() || !root.path("formatVersion").isIntegralNumber()
                || !root.path("formatVersion").canConvertToInt() || root.path("formatVersion").asInt() != 1)
            throw new IllegalArgumentException("Okänt kvittensformat");
        for (String name : List.of("topic", "dataleveransId", "korrelationsId", "steg", "skapad", "fingeravtryck"))
            if (!root.path(name).isString()) throw new IllegalArgumentException("Kvittensfält saknas: " + name);
        return new Kvittens(root.path("topic").asString(), UUID.fromString(root.path("dataleveransId").asString()),
                root.path("korrelationsId").asString(), Leveranssteg.valueOf(root.path("steg").asString()),
                Instant.parse(root.path("skapad").asString()), root.path("fingeravtryck").asString());
    }
}
