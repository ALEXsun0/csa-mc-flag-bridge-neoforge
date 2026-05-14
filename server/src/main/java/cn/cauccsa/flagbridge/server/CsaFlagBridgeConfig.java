package cn.cauccsa.flagbridge.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class CsaFlagBridgeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enableHttpServer = true;
    public String httpHost = "127.0.0.1";
    public int httpPort = 18080;
    public List<String> allowedRegistrationSourceCidrs = new ArrayList<>();
    public String registrationSecret = "";
    public int maxPlayersPerToken = 5;
    public boolean allowTokenRebind = false;
    public boolean claimOncePerPlayer = true;
    public boolean consumeTokenOnFirstClaim = false;
    public boolean enableClaimCallback = true;
    public int claimCallbackTimeoutMillis = 2500;
    public boolean notifyOpsOnRegistration = true;
    public boolean notifyPlayerWhenUnbound = true;
    public Boolean bindGateEnabled = true;
    public Boolean bindGateBypassOps = true;
    public int bindGateIntervalTicks = 5;
    public int bindGateMessageIntervalTicks = 100;
    public int bindGateEffectDurationTicks = 80;
    public double bindGateRadius = 1.5;
    public String messagePrefix = "[CSA]";

    public static CsaFlagBridgeConfig load(Path configDir) throws IOException {
        Path dir = configDir.resolve(CsaFlagBridgeServerMod.CONFIG_DIR);
        Files.createDirectories(dir);

        Path path = dir.resolve("config.json");
        if (!Files.exists(path)) {
            CsaFlagBridgeConfig config = new CsaFlagBridgeConfig();
            config.registrationSecret = generateSecret();
            config.save(path);
            return config;
        }

        String raw = Files.readString(path, StandardCharsets.UTF_8);
        CsaFlagBridgeConfig config = GSON.fromJson(raw, CsaFlagBridgeConfig.class);
        if (config == null) {
            config = new CsaFlagBridgeConfig();
        }
        if (config.registrationSecret == null || config.registrationSecret.isBlank()) {
            config.registrationSecret = generateSecret();
            config.save(path);
        }
        config.normalize();
        return config;
    }

    public void save(Path path) throws IOException {
        Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    private void normalize() {
        if (httpHost == null || httpHost.isBlank()) {
            httpHost = "127.0.0.1";
        }
        if (httpPort <= 0 || httpPort > 65535) {
            httpPort = 18080;
        }
        if (allowedRegistrationSourceCidrs == null) {
            allowedRegistrationSourceCidrs = new ArrayList<>();
        }
        allowedRegistrationSourceCidrs.removeIf(entry -> entry == null || entry.isBlank());
        if (maxPlayersPerToken < 1) {
            maxPlayersPerToken = 1;
        }
        if (claimCallbackTimeoutMillis < 500) {
            claimCallbackTimeoutMillis = 500;
        }
        if (messagePrefix == null || messagePrefix.isBlank()) {
            messagePrefix = "[CSA]";
        }
        if (bindGateEnabled == null) {
            bindGateEnabled = true;
        }
        if (bindGateBypassOps == null) {
            bindGateBypassOps = true;
        }
        if (bindGateIntervalTicks < 1) {
            bindGateIntervalTicks = 5;
        }
        if (bindGateMessageIntervalTicks < 20) {
            bindGateMessageIntervalTicks = 100;
        }
        if (bindGateEffectDurationTicks < 20) {
            bindGateEffectDurationTicks = 80;
        }
        if (bindGateRadius < 0.0) {
            bindGateRadius = 1.5;
        }
    }

    private static String generateSecret() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
