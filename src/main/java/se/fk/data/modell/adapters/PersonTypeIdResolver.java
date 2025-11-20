package se.fk.data.modell.adapters;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.data.modell.json.PropertyDeserializerModifier;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.impl.TypeIdResolverBase;
import se.fk.data.modell.v1.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PersonTypeIdResolver extends TypeIdResolverBase {
    private static final Logger log = LoggerFactory.getLogger(PersonTypeIdResolver.class);

    private static final Map<String, Class<?>> ID_TO_CLASS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> CLASS_TO_ID = new ConcurrentHashMap<>();

    private JavaType baseType;

    @Override
    public void init(JavaType baseType) {
        super.init(baseType);
        this.baseType = baseType;

        // Register all known subtypes of Person
        register(FysiskPerson.class);
        register(JuridiskPerson.class);
    }

    @Override
    public String idFromValue(DatabindContext ctxt, Object value) throws JacksonException {
        return idFromValueAndType(ctxt, value, value.getClass());
    }

    @Override
    public String idFromValueAndType(DatabindContext ctxt, Object value, Class<?> suggestedType) throws JacksonException {
        String id = CLASS_TO_ID.get(suggestedType);
        if (id == null) {
            throw new IllegalStateException(
                    "No @Context registered for subtype " + suggestedType.getName()
            );
        }
        return id;
    }

    private static void register(Class<?> subtype) {
        se.fk.data.modell.annotations.Context ctx = subtype.getAnnotation(se.fk.data.modell.annotations.Context.class);
        if (ctx == null) {
            throw new IllegalStateException(
                    "Subtype " + subtype.getName() + " is missing @Context annotation"
            );
        }

        String id = ctx.value();

        Class<?> old = ID_TO_CLASS.putIfAbsent(id, subtype);
        if (old != null && !old.equals(subtype)) {
            throw new IllegalStateException(
                    "Duplicate @Context value '" + id +
                            "' for " + subtype.getName() + " and " + old.getName()
            );
        }

        CLASS_TO_ID.putIfAbsent(subtype, id);
    }



    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        Class<?> subtype = ID_TO_CLASS.get(id);
        if (subtype == null) {
            throw new IllegalArgumentException(
                    "Unknown @context '" + id + "' for base type " +
                            (baseType != null ? baseType.toString() : "<unknown>")
            );
        }
        return context.constructType(subtype);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
