package cn.cauccsa.flagbridge.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executors;

public final class Ret2ShellHttpServer implements AutoCloseable {
    private static final Gson GSON = new Gson();

    private final FlagBridgeService service;
    private final CsaFlagBridgeConfig config;
    private HttpServer httpServer;

    public Ret2ShellHttpServer(FlagBridgeService service, CsaFlagBridgeConfig config) {
        this.service = service;
        this.config = config;
    }

    public void start() throws IOException {
        if (!config.enableHttpServer) {
            CsaFlagBridgeServerMod.LOGGER.info("Ret2Shell HTTP registration server is disabled");
            return;
        }

        InetSocketAddress address = new InetSocketAddress(config.httpHost, config.httpPort);
        httpServer = HttpServer.create(address, 0);
        httpServer.createContext("/healthz", this::handleHealthz);
        httpServer.createContext("/ret2shell/register", this::handleRegister);
        httpServer.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "csa-flag-bridge-http");
            thread.setDaemon(true);
            return thread;
        }));
        httpServer.start();
        CsaFlagBridgeServerMod.LOGGER.info("Ret2Shell HTTP registration server listening on {}:{}", config.httpHost, config.httpPort);
    }

    @Override
    public void close() {
        if (httpServer != null) {
            httpServer.stop(1);
            httpServer = null;
        }
    }

    private void handleHealthz(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            write(exchange, 405, Map.of("ok", false, "message", "method not allowed"));
            return;
        }
        if (!isSourceAllowed(exchange)) {
            write(exchange, 403, Map.of("ok", false, "message", "forbidden"));
            return;
        }
        write(exchange, 200, Map.of("ok", true, "message", "ok"));
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            write(exchange, 405, Map.of("ok", false, "message", "method not allowed"));
            return;
        }
        if (!isAuthorized(exchange)) {
            write(exchange, 403, Map.of("ok", false, "message", "forbidden"));
            return;
        }
        if (!isSourceAllowed(exchange)) {
            write(exchange, 403, Map.of("ok", false, "message", "forbidden"));
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String token = getString(json, "token");
            String flag = getString(json, "flag");
            String teamId = getString(json, "team_id");
            String callbackUrl = getString(json, "callback_url");
            String callbackSecret = getString(json, "callback_secret");
            long ttlSeconds = getLong(json, "ttl_seconds");

            FlagBridgeService.RegisterResult result = service.registerToken(
                new FlagBridgeService.RegisterRequest(token, flag, teamId, ttlSeconds, callbackUrl, callbackSecret)
            );
            write(exchange, result.ok() ? 200 : 400, Map.of("ok", result.ok(), "message", result.message()));
        } catch (Exception e) {
            CsaFlagBridgeServerMod.LOGGER.warn("Bad Ret2Shell registration request", e);
            write(exchange, 400, Map.of("ok", false, "message", "bad request"));
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String expected = config.registrationSecret;
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String actual = exchange.getRequestHeaders().getFirst("X-CSA-Secret");
        return expected.equals(actual);
    }

    private boolean isSourceAllowed(HttpExchange exchange) {
        if (config.allowedRegistrationSourceCidrs == null || config.allowedRegistrationSourceCidrs.isEmpty()) {
            return true;
        }

        InetAddress source = exchange.getRemoteAddress().getAddress();
        for (String cidr : config.allowedRegistrationSourceCidrs) {
            try {
                if (matchesCidr(source, cidr.trim())) {
                    return true;
                }
            } catch (IllegalArgumentException | UnknownHostException e) {
                CsaFlagBridgeServerMod.LOGGER.warn("Ignoring invalid allowedRegistrationSourceCidrs entry: {}", cidr, e);
            }
        }

        CsaFlagBridgeServerMod.LOGGER.warn("Rejected Ret2Shell HTTP request from {}", source.getHostAddress());
        return false;
    }

    private static boolean matchesCidr(InetAddress source, String cidr) throws UnknownHostException {
        if (cidr == null || cidr.isBlank()) {
            return false;
        }

        String[] parts = cidr.split("/", 2);
        byte[] sourceBytes = source.getAddress();
        byte[] baseBytes = InetAddress.getByName(parts[0]).getAddress();
        if (sourceBytes.length != baseBytes.length) {
            return false;
        }
        if (parts.length == 1) {
            return Arrays.equals(sourceBytes, baseBytes);
        }

        int prefixLength = Integer.parseInt(parts[1]);
        int maxPrefixLength = sourceBytes.length * 8;
        if (prefixLength < 0 || prefixLength > maxPrefixLength) {
            throw new IllegalArgumentException("prefix length out of range: " + cidr);
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (sourceBytes[i] != baseBytes[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }

        int mask = (0xff << (8 - remainingBits)) & 0xff;
        return (sourceBytes[fullBytes] & mask) == (baseBytes[fullBytes] & mask);
    }

    private static String getString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        return json.get(key).getAsString();
    }

    private static long getLong(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return 0;
        }
        return json.get(key).getAsLong();
    }

    private static void write(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
