package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.uuid.Generators;

import java.util.UUID;

public class LivscykelHanterad {
    @JsonProperty("id")
    public String id;

    @JsonProperty("version")
    public int version;

    public void stepVersion() {
        this.version++;
    }

    protected LivscykelHanterad() {} // Required for deserialization

    protected LivscykelHanterad(String id) {
        if (null == id || id.isEmpty()) {
            UUID uuid = Generators.timeBasedEpochGenerator().generate(); // Version 7
            this.id = uuid.toString();
        } else {
            this.id = id;
        }
        this.version = 0;
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
