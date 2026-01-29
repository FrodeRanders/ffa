package se.fk.data.modell.json;

import com.apicatalog.jcs.Jcs;
import com.apicatalog.tree.io.NativeAdapter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class JcsUtils {
    private JcsUtils() {}

    public static byte[] canonicalize(byte[] json) {
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        try {
            ObjectMapper mapper = JsonMapper.builder().build();
            Object tree = mapper.readValue(json, Object.class);
            String canonical = Jcs.canonize(tree, NativeAdapter.instance());
            return canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to canonicalize JSON with JCS", e);
        }
    }
}
