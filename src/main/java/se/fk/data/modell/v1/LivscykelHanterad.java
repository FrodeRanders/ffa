package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LivscykelHanterad {
    @JsonProperty("id")
    public String id;

    @JsonProperty("version")
    public int version = 0;

    public void stepVersion() {
        this.version++;
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
