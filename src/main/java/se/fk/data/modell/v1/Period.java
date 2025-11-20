package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import se.fk.data.modell.annotations.Context;

import java.util.Date;

@Context("https://data.fk.se/kontext/std/period/1.0")
public class Period {
    @JsonProperty("from")
    public Date from;

    @JsonProperty("tom")
    public Date tom;

    public Period() {} // Required for deserialization

    public Period(Date from, Date tom) {
        this.from = from;
        this.tom = tom;
    }

    public Period(Date date) {
        this.from = this.tom = date;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Period{");
        sb.append("from='").append(from).append('\'');
        sb.append(", tom='").append(tom).append('\'');
        sb.append('}');
        return sb.toString();
    }}
