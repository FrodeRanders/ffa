package se.fk.data.modell.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;

import java.security.MessageDigest;

/** Intern livscykelmetadata. Förmånskoden får bara läsa identitet och version. */
public abstract class LifecycleState {
    // Beständig metadata: identiteten behålls och versionen beskriver objektets innehåll.
    @JsonProperty("id")
    private String id;

    @JsonProperty("version")
    private int version;

    // Intern jämförelsereferens och tillfällig skrivflagga ingår inte som modellfält i JSON.
    @JsonIgnore
    private byte[] digest;

    @JsonIgnore
    private Boolean attention;

    protected LifecycleState() {
        this.id = Generators.timeBasedEpochGenerator().generate().toString();
    }

    public final String getId() {
        return id;
    }

    public final int getVersion() {
        return version;
    }

    byte[] digest() {
        return digest;
    }

    boolean compareDigest(byte[] current) {
        return MessageDigest.isEqual(current, digest);
    }

    void resetDigest(byte[] current) {
        digest = current.clone();
    }

    void stepVersion() {
        version = Math.incrementExact(version);
        attention = true;
    }

    // Flaggan gäller denna skrivning och måste vara återställd inför nästa serialisering.
    Boolean consumeAttention() {
        Boolean value = attention;
        attention = null;
        return value;
    }

    // Verksamhetskopian behöver originalets referenskontrollsumma för att upptäcka ändringar.
    void copyLifecycleFrom(LifecycleState source) {
        version = source.version;
        digest = source.digest == null ? null : source.digest.clone();
        attention = source.attention;
    }
}
