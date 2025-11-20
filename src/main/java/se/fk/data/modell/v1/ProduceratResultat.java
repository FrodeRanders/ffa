package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.annotation.JsonTypeIdResolver;
import se.fk.data.modell.adapters.ProduceratResultatTypeIdResolver;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.CUSTOM,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "@context",
        visible = true
)
@JsonTypeIdResolver(ProduceratResultatTypeIdResolver.class)
public class ProduceratResultat extends LivscykelHanterad {

    public ProduceratResultat() {} // Required for deserialization

    public ProduceratResultat(String id) {
        super(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ProduceratResultat{");
        sb.append(super.toString());
        sb.append('}');
        return sb.toString();
    }
}
