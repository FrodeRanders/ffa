package se.fk.mimer.pipeline;

import com.sun.net.httpserver.*;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

/** Lokalt PoC-kontrakt: indexuppslag och verifierad återläsning av originaldokument. */
public final class BackendRestServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Radatalager backend;

    public BackendRestServer(InetSocketAddress address, Radatalager backend) throws IOException {
        this.backend = java.util.Objects.requireNonNull(backend);
        server = HttpServer.create(address, 0);
        server.setExecutor(executor);
        server.createContext("/v1/", this::hantera);
        server.start();
    }

    public URI uri() { return URI.create("http://localhost:" + server.getAddress().getPort() + "/"); }

    private void hantera(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                if (!exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    svar(exchange, 405, "Metoden stöds inte");
                    return;
                }
                String[] path = exchange.getRequestURI().getRawPath().split("/", -1);
                if (path.length < 4 || !path[1].equals("v1")) { svar(exchange, 404, "Okänd resurs"); return; }
                String typ = path[2];
                String id;
                try { id = URLDecoder.decode(path[3].replace("+", "%2B"), StandardCharsets.UTF_8); }
                catch (IllegalArgumentException e) { svar(exchange, 400, "Ogiltigt uppslag"); return; }
                boolean delivery = typ.equals("leveranser");
                if ((!delivery && !typ.equals("processer") && !typ.equals("objekt")) || id.isBlank()
                        || (!delivery && (path.length < 5 || !path[4].equals("senaste")))) {
                    svar(exchange, 404, "Okänd resurs"); return;
                }
                int length = delivery ? 4 : 5;
                boolean metadata = path.length == length + 1 && path[length].equals("metadata");
                if (path.length != length && !metadata) { svar(exchange, 404, "Okänd resurs"); return; }
                if (delivery) {
                    try { UUID.fromString(id); }
                    catch (IllegalArgumentException e) { svar(exchange, 400, "Ogiltigt leverans-id"); return; }
                }
                if (metadata) {
                    var index = backend.metadata(typ, id);
                    if (index == null) { svar(exchange, 404, "Leveransen saknas"); return; }
                    var bytes = JsonMapper.builder().build().writeValueAsBytes(index);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    skicka(exchange, 200, bytes);
                } else {
                    var d = switch (typ) {
                        case "leveranser" -> backend.lasLeverans(UUID.fromString(id));
                        case "processer" -> backend.lasProcess(id);
                        default -> backend.lasObjekt(id);
                    };
                    if (d == null) { svar(exchange, 404, "Leveransen saknas"); return; }
                    Leveransformat.metadata(d).forEach((key, value) -> exchange.getResponseHeaders().set("X-FFA-" + key, value));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    skicka(exchange, 200, d.dokument().json());
                }
            } catch (RuntimeException e) { svar(exchange, 503, "Rådatalagret är inte tillgängligt eller kan inte verifieras"); }
        }
    }

    private static void svar(HttpExchange exchange, int status, String text) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        skicka(exchange, status, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void skicka(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override public void close() { server.stop(0); executor.close(); }
}
