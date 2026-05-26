package cn.cauccsa.aerobridge.server;

import cn.cauccsa.aerobridge.content.FlightRecorderBlockEntity;
import cn.cauccsa.flagbridge.server.CsaFlagBridgeServerMod;
import cn.cauccsa.flagbridge.server.FlagBridgeService;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import org.joml.Vector3dc;

public final class CsaAeroRecorderHandler {
    private static final CertificationRoute STABLE_ROUTE = new CertificationRoute(
        "稳定巡航",
        "稳定",
        "aero_recorder_stable_cruise",
        CsaAeroBridgeCommands.RouteChoice.STABLE
    );
    private static final CertificationRoute HIGH_SPEED_ROUTE = new CertificationRoute(
        "极速飞行",
        "极速",
        "aero_recorder_high_speed",
        CsaAeroBridgeCommands.RouteChoice.HIGH_SPEED
    );
    private static final CertificationRoute ALTITUDE_ROUTE = new CertificationRoute(
        "高度飞行",
        "高度",
        "aero_recorder_altitude",
        CsaAeroBridgeCommands.RouteChoice.ALTITUDE
    );
    private static final int FAILURE_NOTICE_INTERVAL_TICKS = 200;
    private static final Map<BlockKey, RuntimeState> RUNTIME = new HashMap<>();
    private static int cleanupTickCounter = 0;

    private CsaAeroRecorderHandler() {
    }

    public static void tick(FlightRecorderBlockEntity recorder) {
        Level level = recorder.recorderLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockKey key = BlockKey.from(serverLevel, recorder.getBlockPos());
        if (recorder.claimed()) {
            removeRuntime(key);
            return;
        }

        CsaAeroBridgeConfig config = CsaAeroBridgeServer.config();
        RuntimeState runtime = RUNTIME.computeIfAbsent(key, ignored -> new RuntimeState());
        runtime.tickCounter++;
        if (runtime.tickCounter % config.recorderTickInterval != 0) {
            return;
        }

        UUID placerUuid = recorder.placerUuid();
        if (placerUuid == null) {
            resetProgress(recorder, runtime);
            hideBossBars(runtime);
            return;
        }

        SubLevel containing = Sable.HELPER.getContaining(level, recorder.getBlockPos());
        if (!(containing instanceof ServerSubLevel subLevel)) {
            sendHud(
                recorder,
                config,
                runtime,
                new CruiseSample(0.0, 0.0, 0.0, recorder.getBlockPos().getY()),
                new CruiseAssessment(false, 0.0, "未安装在飞行器上"),
                RouteProgress.empty(),
                CsaAeroBridgeCommands.routeFor(placerUuid)
            );
            resetProgress(recorder, runtime);
            return;
        }

        CruiseSample sample = sampleCruise(subLevel);
        CruiseAssessment assessment = assessCruise(config, sample, runtime);
        RouteProgress progress = updateProgress(recorder, config, runtime, sample, assessment);
        CsaAeroBridgeCommands.RouteChoice routeChoice = CsaAeroBridgeCommands.routeFor(placerUuid);
        sendHud(recorder, config, runtime, sample, assessment, progress, routeChoice);

        CertificationRoute completedRoute = completedRoute(config, progress);
        if (completedRoute != null) {
            if (completeCertification(recorder, runtime, completedRoute)) {
                removeRuntime(key);
            }
            return;
        }

        runtime.lastHorizontalSpeed = sample.horizontalSpeed();
    }

    public static void cleanup(MinecraftServer server) {
        cleanupTickCounter++;
        if (cleanupTickCounter % 20 != 0) {
            return;
        }

        Iterator<Map.Entry<BlockKey, RuntimeState>> iterator = RUNTIME.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockKey, RuntimeState> entry = iterator.next();
            BlockKey key = entry.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null
                || !(level.getBlockEntity(key.pos()) instanceof FlightRecorderBlockEntity recorder)
                || recorder.claimed()) {
                hideBossBars(entry.getValue());
                iterator.remove();
            }
        }
    }

    public static void shutdown() {
        for (RuntimeState runtime : RUNTIME.values()) {
            hideBossBars(runtime);
        }
        RUNTIME.clear();
    }

    private static CruiseSample sampleCruise(ServerSubLevel subLevel) {
        Vector3dc velocity = subLevel.latestLinearVelocity;
        double vx = finiteOrZero(velocity.x());
        double vy = finiteOrZero(velocity.y());
        double vz = finiteOrZero(velocity.z());

        Vector3dc position = subLevel.logicalPose().position();
        Vector3dc lastPosition = subLevel.lastPose().position();
        double poseVx = finiteOrZero((position.x() - lastPosition.x()) * 20.0);
        double poseVy = finiteOrZero((position.y() - lastPosition.y()) * 20.0);
        double poseVz = finiteOrZero((position.z() - lastPosition.z()) * 20.0);
        double velocityHorizontal = Math.sqrt(vx * vx + vz * vz);
        double poseHorizontal = Math.sqrt(poseVx * poseVx + poseVz * poseVz);
        if (poseHorizontal > velocityHorizontal + 0.05) {
            vx = poseVx;
            vy = poseVy;
            vz = poseVz;
        }

        Vector3dc angularVelocity = subLevel.latestAngularVelocity;
        double ax = finiteOrZero(angularVelocity.x());
        double ay = finiteOrZero(angularVelocity.y());
        double az = finiteOrZero(angularVelocity.z());
        return new CruiseSample(
            Math.sqrt(vx * vx + vz * vz),
            Math.abs(vy),
            Math.sqrt(ax * ax + ay * ay + az * az),
            finiteOrZero(position.y())
        );
    }

    private static CruiseAssessment assessCruise(CsaAeroBridgeConfig config, CruiseSample sample, RuntimeState runtime) {
        double horizontalJitter = runtime.lastHorizontalSpeed >= 0.0
            ? Math.abs(sample.horizontalSpeed() - runtime.lastHorizontalSpeed)
            : 0.0;

        if (sample.horizontalSpeed() < config.minHorizontalSpeed) {
            return new CruiseAssessment(false, horizontalJitter, "水平不足");
        }
        if (sample.verticalSpeed() > config.maxVerticalSpeed) {
            return new CruiseAssessment(false, horizontalJitter, "垂直过快");
        }
        if (sample.angularSpeed() > config.maxAngularSpeed) {
            return new CruiseAssessment(false, horizontalJitter, "转动过大");
        }
        if (runtime.lastHorizontalSpeed >= 0.0 && horizontalJitter > config.maxHorizontalSpeedJitter) {
            return new CruiseAssessment(false, horizontalJitter, "速度波动");
        }
        return new CruiseAssessment(true, horizontalJitter, "达标中");
    }

    private static RouteProgress updateProgress(
        FlightRecorderBlockEntity recorder,
        CsaAeroBridgeConfig config,
        RuntimeState runtime,
        CruiseSample sample,
        CruiseAssessment assessment
    ) {
        if (assessment.stable()) {
            recorder.setStableTicks(recorder.stableTicks() + config.recorderTickInterval);
        } else if (recorder.stableTicks() != 0) {
            recorder.setStableTicks(0);
        }

        if (config.highSpeedClaimEnabled && sample.horizontalSpeed() >= config.highSpeedHorizontalSpeed) {
            runtime.highSpeedTicks += config.recorderTickInterval;
        } else {
            runtime.highSpeedTicks = 0;
        }

        if (config.altitudeClaimEnabled
            && sample.altitudeY() >= config.altitudeY
            && sample.horizontalSpeed() >= config.altitudeMinHorizontalSpeed) {
            runtime.altitudeTicks += config.recorderTickInterval;
        } else {
            runtime.altitudeTicks = 0;
        }

        return new RouteProgress(
            Math.min(recorder.stableTicks(), config.stableCruiseTicks),
            Math.min(runtime.highSpeedTicks, config.highSpeedTicks),
            Math.min(runtime.altitudeTicks, config.altitudeTicks)
        );
    }

    private static CertificationRoute completedRoute(CsaAeroBridgeConfig config, RouteProgress progress) {
        for (CertificationRoute route : new CertificationRoute[] {STABLE_ROUTE, HIGH_SPEED_ROUTE, ALTITUDE_ROUTE}) {
            if (routeCompleted(config, progress, route)) {
                return route;
            }
        }
        return null;
    }

    private static boolean routeCompleted(CsaAeroBridgeConfig config, RouteProgress progress, CertificationRoute route) {
        return switch (route.choice()) {
            case STABLE -> progress.stableTicks() >= config.stableCruiseTicks;
            case HIGH_SPEED -> config.highSpeedClaimEnabled && progress.highSpeedTicks() >= config.highSpeedTicks;
            case ALTITUDE -> config.altitudeClaimEnabled && progress.altitudeTicks() >= config.altitudeTicks;
            case AUTO -> false;
        };
    }

    private static void sendHud(
        FlightRecorderBlockEntity recorder,
        CsaAeroBridgeConfig config,
        RuntimeState runtime,
        CruiseSample sample,
        CruiseAssessment assessment,
        RouteProgress progress,
        CsaAeroBridgeCommands.RouteChoice routeChoice
    ) {
        Level level = recorder.recorderLevel();
        UUID placerUuid = recorder.placerUuid();
        if (!(level instanceof ServerLevel serverLevel) || placerUuid == null) {
            return;
        }

        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(placerUuid);
        if (player == null) {
            hideBossBars(runtime);
            return;
        }

        long gameTime = serverLevel.getGameTime();
        sendLegendOnce(player, runtime);
        sendBossBarStatus(player, config, runtime, sample, assessment, progress, routeChoice, gameTime);
    }

    private static void sendLegendOnce(ServerPlayer player, RuntimeState runtime) {
        if (runtime.legendSent) {
            return;
        }
        runtime.legendSent = true;
        player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage(
            "飞行记录仪 HUD 只显示当前路线。用 /csaero route stable|speed|altitude 切换，/csaero route auto 自动判定。"
        )).withStyle(ChatFormatting.AQUA));
    }

    private static void sendBossBarStatus(
        ServerPlayer player,
        CsaAeroBridgeConfig config,
        RuntimeState runtime,
        CruiseSample sample,
        CruiseAssessment assessment,
        RouteProgress progress,
        CsaAeroBridgeCommands.RouteChoice routeChoice,
        long gameTime
    ) {
        if (gameTime - runtime.lastHudTick < config.recorderHudIntervalTicks) {
            return;
        }
        runtime.lastHudTick = gameTime;

        ensureBossBars(player, runtime);
        CertificationRoute displayRoute = displayRoute(config, progress, routeChoice);
        String actual = actualText(config, sample, assessment, progress, routeChoice, displayRoute);
        String requirement = requirementText(config, displayRoute);

        runtime.actualBar.setName(Component.literal(actual));
        float routeProgress = routeProgress(config, progress, displayRoute);
        runtime.actualBar.setProgress(routeProgress);
        runtime.actualBar.setColor(routeProgress >= 1.0F ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.YELLOW);
        runtime.requirementBar.setName(Component.literal(requirement));
        runtime.requirementBar.setProgress(1.0F);
    }

    private static CertificationRoute displayRoute(
        CsaAeroBridgeConfig config,
        RouteProgress progress,
        CsaAeroBridgeCommands.RouteChoice routeChoice
    ) {
        CertificationRoute selected = routeForChoice(routeChoice);
        if (selected != null) {
            return selected;
        }

        CertificationRoute best = STABLE_ROUTE;
        float bestProgress = routeProgress(config, progress, STABLE_ROUTE);
        if (config.highSpeedClaimEnabled) {
            float highSpeedProgress = routeProgress(config, progress, HIGH_SPEED_ROUTE);
            if (highSpeedProgress > bestProgress) {
                best = HIGH_SPEED_ROUTE;
                bestProgress = highSpeedProgress;
            }
        }
        if (config.altitudeClaimEnabled) {
            float altitudeProgress = routeProgress(config, progress, ALTITUDE_ROUTE);
            if (altitudeProgress > bestProgress) {
                best = ALTITUDE_ROUTE;
            }
        }
        return best;
    }

    private static CertificationRoute routeForChoice(CsaAeroBridgeCommands.RouteChoice routeChoice) {
        return switch (routeChoice) {
            case STABLE -> STABLE_ROUTE;
            case HIGH_SPEED -> HIGH_SPEED_ROUTE;
            case ALTITUDE -> ALTITUDE_ROUTE;
            case AUTO -> null;
        };
    }

    private static String actualText(
        CsaAeroBridgeConfig config,
        CruiseSample sample,
        CruiseAssessment assessment,
        RouteProgress progress,
        CsaAeroBridgeCommands.RouteChoice routeChoice,
        CertificationRoute route
    ) {
        String routeName = routeChoice == CsaAeroBridgeCommands.RouteChoice.AUTO
            ? "自动-" + route.shortName()
            : route.shortName();
        String status = routeStatus(config, sample, assessment, route);
        return switch (route.choice()) {
            case STABLE -> String.format(
                Locale.ROOT,
                "CSA %s: %s | %.1f/%.1fs | H %.1f V %.1f R %.2f D %.1f",
                routeName,
                status,
                progress.stableTicks() / 20.0,
                config.stableCruiseTicks / 20.0,
                sample.horizontalSpeed(),
                sample.verticalSpeed(),
                sample.angularSpeed(),
                assessment.horizontalJitter()
            );
            case HIGH_SPEED -> String.format(
                Locale.ROOT,
                "CSA %s: %s | %.1f/%.1fs | H %.1f",
                routeName,
                status,
                progress.highSpeedTicks() / 20.0,
                config.highSpeedTicks / 20.0,
                sample.horizontalSpeed()
            );
            case ALTITUDE -> String.format(
                Locale.ROOT,
                "CSA %s: %s | %.1f/%.1fs | Y %.1f H %.1f",
                routeName,
                status,
                progress.altitudeTicks() / 20.0,
                config.altitudeTicks / 20.0,
                sample.altitudeY(),
                sample.horizontalSpeed()
            );
            case AUTO -> "";
        };
    }

    private static String requirementText(CsaAeroBridgeConfig config, CertificationRoute route) {
        return switch (route.choice()) {
            case STABLE -> String.format(
                Locale.ROOT,
                "CSA 要求: H>=%.1f V<=%.1f R<=%.2f D<=%.1f",
                config.minHorizontalSpeed,
                config.maxVerticalSpeed,
                config.maxAngularSpeed,
                config.maxHorizontalSpeedJitter
            );
            case HIGH_SPEED -> String.format(
                Locale.ROOT,
                "CSA 要求: H>=%.1f",
                config.highSpeedHorizontalSpeed
            );
            case ALTITUDE -> String.format(
                Locale.ROOT,
                "CSA 要求: Y>=%.1f H>=%.1f",
                config.altitudeY,
                config.altitudeMinHorizontalSpeed
            );
            case AUTO -> "";
        };
    }

    private static String routeStatus(
        CsaAeroBridgeConfig config,
        CruiseSample sample,
        CruiseAssessment assessment,
        CertificationRoute route
    ) {
        return switch (route.choice()) {
            case STABLE -> assessment.stable() ? "达标中" : "未达标:" + assessment.reason();
            case HIGH_SPEED -> sample.horizontalSpeed() >= config.highSpeedHorizontalSpeed ? "达标中" : "未达标:速度不足";
            case ALTITUDE -> {
                if (sample.altitudeY() < config.altitudeY) {
                    yield "未达标:高度不足";
                }
                yield sample.horizontalSpeed() >= config.altitudeMinHorizontalSpeed ? "达标中" : "未达标:水平不足";
            }
            case AUTO -> "";
        };
    }

    private static float routeProgress(CsaAeroBridgeConfig config, RouteProgress progress, CertificationRoute route) {
        return switch (route.choice()) {
            case STABLE -> clampProgress((float) progress.stableTicks() / (float) config.stableCruiseTicks);
            case HIGH_SPEED -> config.highSpeedClaimEnabled
                ? clampProgress((float) progress.highSpeedTicks() / (float) config.highSpeedTicks)
                : 0.0F;
            case ALTITUDE -> config.altitudeClaimEnabled
                ? clampProgress((float) progress.altitudeTicks() / (float) config.altitudeTicks)
                : 0.0F;
            case AUTO -> 0.0F;
        };
    }

    private static void ensureBossBars(ServerPlayer player, RuntimeState runtime) {
        if (runtime.actualBar == null) {
            runtime.actualBar = new ServerBossEvent(
                Component.literal("CSA 实际"),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS
            );
        }
        if (runtime.requirementBar == null) {
            runtime.requirementBar = new ServerBossEvent(
                Component.literal("CSA 要求"),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS
            );
        }

        if (!player.getUUID().equals(runtime.bossBarPlayerUuid)) {
            runtime.actualBar.removeAllPlayers();
            runtime.requirementBar.removeAllPlayers();
            runtime.actualBar.addPlayer(player);
            runtime.requirementBar.addPlayer(player);
            runtime.bossBarPlayerUuid = player.getUUID();
        } else if (!runtime.actualBar.getPlayers().contains(player)) {
            runtime.actualBar.addPlayer(player);
            runtime.requirementBar.addPlayer(player);
        }
    }

    private static void hideBossBars(RuntimeState runtime) {
        if (runtime.actualBar != null) {
            runtime.actualBar.removeAllPlayers();
        }
        if (runtime.requirementBar != null) {
            runtime.requirementBar.removeAllPlayers();
        }
        runtime.bossBarPlayerUuid = null;
    }

    private static void removeRuntime(BlockKey key) {
        RuntimeState runtime = RUNTIME.remove(key);
        if (runtime != null) {
            hideBossBars(runtime);
        }
    }

    private static boolean completeCertification(FlightRecorderBlockEntity recorder, RuntimeState runtime, CertificationRoute route) {
        FlagBridgeService.CommandResult result = CsaFlagBridgeServerMod.claimForUuid(
            recorder.placerUuid(),
            recorder.placerName(),
            route.reason(),
            recorder.getBlockPos()
        );
        if (!result.ok()) {
            resetProgress(recorder, runtime);
            notifyOwnerThrottled(recorder, runtime, result.message());
            return false;
        }

        recorder.markClaimed(route.finalStableTicks(CsaAeroBridgeServer.config()));
        hideBossBars(runtime);
        notifyOwner(recorder, route.displayName() + "认证通过。" + result.message());
        return true;
    }

    private static void resetProgress(FlightRecorderBlockEntity recorder, RuntimeState runtime) {
        if (recorder.stableTicks() != 0) {
            recorder.setStableTicks(0);
        }
        runtime.lastHorizontalSpeed = -1.0;
        runtime.highSpeedTicks = 0;
        runtime.altitudeTicks = 0;
    }

    private static void notifyOwnerThrottled(FlightRecorderBlockEntity recorder, RuntimeState runtime, String message) {
        Level level = recorder.recorderLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long gameTime = serverLevel.getGameTime();
        if (gameTime - runtime.lastFailureNoticeTick < FAILURE_NOTICE_INTERVAL_TICKS) {
            return;
        }
        runtime.lastFailureNoticeTick = gameTime;
        notifyOwner(recorder, message);
    }

    private static void notifyOwner(FlightRecorderBlockEntity recorder, String message) {
        Level level = recorder.recorderLevel();
        UUID placerUuid = recorder.placerUuid();
        if (!(level instanceof ServerLevel serverLevel) || placerUuid == null || message == null || message.isBlank()) {
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(placerUuid);
        if (player != null) {
            player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage(message)));
        }
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static float clampProgress(float progress) {
        if (!Float.isFinite(progress)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    private record CruiseSample(double horizontalSpeed, double verticalSpeed, double angularSpeed, double altitudeY) {
    }

    private record CruiseAssessment(boolean stable, double horizontalJitter, String reason) {
    }

    private record RouteProgress(int stableTicks, int highSpeedTicks, int altitudeTicks) {
        static RouteProgress empty() {
            return new RouteProgress(0, 0, 0);
        }
    }

    private record CertificationRoute(
        String displayName,
        String shortName,
        String reason,
        CsaAeroBridgeCommands.RouteChoice choice
    ) {
        int finalStableTicks(CsaAeroBridgeConfig config) {
            return config.stableCruiseTicks;
        }
    }

    private record BlockKey(ResourceKey<Level> dimension, BlockPos pos) {
        static BlockKey from(ServerLevel level, BlockPos pos) {
            return new BlockKey(level.dimension(), pos.immutable());
        }
    }

    private static final class RuntimeState {
        private int tickCounter = 0;
        private double lastHorizontalSpeed = -1.0;
        private int highSpeedTicks = 0;
        private int altitudeTicks = 0;
        private long lastFailureNoticeTick = -FAILURE_NOTICE_INTERVAL_TICKS;
        private long lastHudTick = -20;
        private boolean legendSent = false;
        private UUID bossBarPlayerUuid;
        private ServerBossEvent actualBar;
        private ServerBossEvent requirementBar;
    }
}
