package cn.cauccsa.aerobridge.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CsaAeroBridgeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int recorderCooldownSeconds = 5;
    public int maxRecordersInInventory = 8;
    public int stableCruiseTicks = 60;
    public double minHorizontalSpeed = 1.0;
    public double maxVerticalSpeed = 4.0;
    public double maxAngularSpeed = 1.5;
    public double maxHorizontalSpeedJitter = 8.0;
    public boolean highSpeedClaimEnabled = true;
    public int highSpeedTicks = 40;
    public double highSpeedHorizontalSpeed = 8.0;
    public boolean altitudeClaimEnabled = true;
    public int altitudeTicks = 40;
    public double altitudeY = 180.0;
    public double altitudeMinHorizontalSpeed = 0.0;
    public int recorderTickInterval = 5;
    public int recorderHudIntervalTicks = 5;
    public boolean starterHoneyGlueEnabled = true;
    public String starterHoneyGlueItemId = "simulated:honey_glue";
    public int starterHoneyGlueCount = 8;
    public boolean starterGogglesEnabled = true;
    public String starterGogglesItemId = "create:goggles";
    public int starterGogglesCount = 1;
    public boolean sheepProductionBoostEnabled = true;
    public int sheepShearingWoolCount = 16;
    public int sheepDeathWoolDropMultiplier = 8;
    public String messagePrefix = "[CSA Aero]";

    public static CsaAeroBridgeConfig load(Path configDir) throws IOException {
        Path dir = configDir.resolve(CsaAeroBridgeServer.CONFIG_DIR);
        Files.createDirectories(dir);

        Path path = dir.resolve("config.json");
        CsaAeroBridgeConfig config;
        if (Files.exists(path)) {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            config = GSON.fromJson(raw, CsaAeroBridgeConfig.class);
            if (config == null) {
                config = new CsaAeroBridgeConfig();
            }
        } else {
            config = new CsaAeroBridgeConfig();
        }

        config.normalize();
        config.save(path);
        return config;
    }

    private void save(Path path) throws IOException {
        Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    private void normalize() {
        if (recorderCooldownSeconds < 0) {
            recorderCooldownSeconds = 5;
        }
        if (maxRecordersInInventory < 1) {
            maxRecordersInInventory = 8;
        }
        if (stableCruiseTicks < 20) {
            stableCruiseTicks = 60;
        }
        if (minHorizontalSpeed < 0.1) {
            minHorizontalSpeed = 1.0;
        }
        if (maxVerticalSpeed < 0.0) {
            maxVerticalSpeed = 4.0;
        }
        if (maxAngularSpeed < 0.0) {
            maxAngularSpeed = 1.5;
        }
        if (maxHorizontalSpeedJitter < 0.0) {
            maxHorizontalSpeedJitter = 8.0;
        }
        if (highSpeedTicks < 20) {
            highSpeedTicks = 40;
        }
        if (highSpeedHorizontalSpeed < 0.1) {
            highSpeedHorizontalSpeed = 8.0;
        }
        if (altitudeTicks < 20) {
            altitudeTicks = 40;
        }
        if (altitudeY < -64.0) {
            altitudeY = 180.0;
        }
        if (altitudeMinHorizontalSpeed < 0.0) {
            altitudeMinHorizontalSpeed = 0.0;
        }
        if (recorderTickInterval < 1) {
            recorderTickInterval = 5;
        }
        if (recorderHudIntervalTicks < 1) {
            recorderHudIntervalTicks = 5;
        }
        if (starterHoneyGlueItemId == null || starterHoneyGlueItemId.isBlank()) {
            starterHoneyGlueItemId = "simulated:honey_glue";
        }
        if (starterHoneyGlueCount < 0) {
            starterHoneyGlueCount = 8;
        }
        if (starterGogglesItemId == null || starterGogglesItemId.isBlank()) {
            starterGogglesItemId = "create:goggles";
        }
        if (starterGogglesCount < 0) {
            starterGogglesCount = 1;
        }
        if (sheepShearingWoolCount < 1) {
            sheepShearingWoolCount = 16;
        }
        if (sheepDeathWoolDropMultiplier < 1) {
            sheepDeathWoolDropMultiplier = 8;
        }
        if (messagePrefix == null || messagePrefix.isBlank()) {
            messagePrefix = "[CSA Aero]";
        }
    }
}
