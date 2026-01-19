package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;

import java.security.MessageDigest;

public class Livscykelhanterad {
    @JsonIgnore
    private transient byte[] __digest;

    @JsonIgnore
    public Boolean __attention = null;

    @JsonProperty("id")
    public String id = Generators.timeBasedEpochGenerator().generate().toString(); // Always set here, but may be reset

    @JsonProperty("version")
    public int version = 0;

    @JsonIgnore
    public void stepVersion() {
        this.version++;
    }

    protected Livscykelhanterad() {} // Required for deserialization

    protected Livscykelhanterad(String id) {
        if (null != id && !id.isEmpty()) {
            this.id = id;
        }
    }

    @JsonIgnore
    public boolean compareDigest(byte[] current) {
        return MessageDigest.isEqual(current, __digest);
    }

    @JsonIgnore
    public void resetDigest(byte[] current) {
        __digest = current;
    }

    @JsonIgnore
    public byte[] getDigest() {
        return __digest;
    }

    @JsonIgnore
    public void flagAttention() {
        this.__attention = true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id='").append(id).append('\'');
        sb.append(", version=");
        sb.append(0 == version ? "N/A" : version);
        return sb.toString();
    }
}
