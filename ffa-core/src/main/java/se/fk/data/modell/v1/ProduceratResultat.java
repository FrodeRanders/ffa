package se.fk.data.modell.v1;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import se.fk.data.modell.adapters.ProduceratResultatTypeIdResolver;
import tools.jackson.databind.annotation.JsonTypeIdResolver;

/** Gemensam bas för handläggningens resultat, med centralt förvaltad identitet och version. */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CUSTOM,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "@type",
        visible = true
)
@JsonTypeIdResolver(ProduceratResultatTypeIdResolver.class)
public abstract class ProduceratResultat extends Livscykelhanterad {

    public ProduceratResultat() {} // Behövs vid återläsning med Jackson.

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ProduceratResultat{");
        sb.append(super.toString());
        sb.append('}');
        return sb.toString();
    }
}
