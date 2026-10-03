package se.fk.mimer.persistence;

import se.fk.mimer.runtime.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** REST-adapter utan beroende till objektlager eller graf. Endast 404 betyder cachemiss. */
public final class RestDokumentkalla implements Dokumentkalla, AutoCloseable {
    private final URI base;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public RestDokumentkalla(URI base) {
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                || base.getQuery() != null || base.getFragment() != null)
            throw new IllegalArgumentException("Ogiltig backendadress");
        this.base = URI.create(base.toString().endsWith("/") ? base.toString() : base + "/");
    }

    @Override public Dataleverans lasLeverans(UUID id) { return hamta("leveranser/" + id, "leverans", id.toString()); }
    @Override public Dataleverans lasProcess(String id) { return hamta("processer/" + segment(id) + "/senaste", "process", id); }
    @Override public Dataleverans lasObjekt(String id) { return hamta("objekt/" + segment(id) + "/senaste", "objekt", id); }

    private Dataleverans hamta(String path, String typ, String id) {
        try {
            var response = client.send(HttpRequest.newBuilder(base.resolve("v1/" + path))
                    .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() != 200) throw new IllegalStateException("Backenduppslag misslyckades: HTTP " + response.statusCode());
            if (!header(response, "signatur-algoritm").equals("JCS+RSA-PSS-SHA512"))
                throw new IllegalArgumentException("Okänd signaturalgoritm");
            var d = new Dataleverans(UUID.fromString(header(response, "dataleverans-id")),
                    decode(header(response, "korrelations-id")), decode(header(response, "objekt-id")),
                    Long.parseLong(header(response, "forvantad-version")), Long.parseLong(header(response, "objekt-version")),
                    Instant.parse(header(response, "skapad")),
                    new LagratDokument(response.body(), Base64.getDecoder().decode(header(response, "signatur"))));
            String actual = switch (typ) { case "leverans" -> d.id().toString(); case "process" -> d.korrelationsId(); default -> d.objektId(); };
            if (!actual.equals(id)) throw new IllegalArgumentException("Backend returnerade fel identitet");
            // ForvaltadeYrkanden verifierar signaturen före deserialisering och cacheåterställning.
            return d;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Backenduppslag avbröts", e);
        } catch (java.io.IOException e) { throw new IllegalStateException("Backend kunde inte nås", e); }
    }

    private static String header(HttpResponse<?> response, String name) {
        var values = response.headers().allValues("X-FFA-" + name);
        if (values.size() != 1) throw new IllegalArgumentException("Metadataheader saknas eller är dubblerad: " + name);
        return values.getFirst();
    }

    private static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace(".", "%2E"); }
    private static String decode(String value) { return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8); }
    @Override public void close() { client.close(); }
}
