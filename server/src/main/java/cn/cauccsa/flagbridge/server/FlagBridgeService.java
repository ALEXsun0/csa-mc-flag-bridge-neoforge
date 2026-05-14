package cn.cauccsa.flagbridge.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

public final class FlagBridgeService {
    private static final Gson GSON = new Gson();

    private final MinecraftServer server;
    private final CsaFlagBridgeConfig config;
    private final CsaFlagBridgeState state;
    private final Map<String, BindGateAnchor> bindGateAnchors = new LinkedHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(2500))
        .build();
    private long bindGateTicks = 0;

    public FlagBridgeService(MinecraftServer server, CsaFlagBridgeConfig config, CsaFlagBridgeState state) {
        this.server = server;
        this.config = config;
        this.state = state;
    }

    public CsaFlagBridgeConfig config() {
        return config;
    }

    public synchronized RegisterResult registerToken(RegisterRequest request) throws IOException {
        String token = normalizeToken(request.token);
        if (token.isEmpty()) {
            return RegisterResult.error("missing token");
        }
        if (request.flag == null || request.flag.isBlank()) {
            return RegisterResult.error("missing flag");
        }

        CsaFlagBridgeState.Registration registration = new CsaFlagBridgeState.Registration();
        registration.token = token;
        registration.flag = request.flag.trim();
        registration.teamId = request.teamId == null ? "" : request.teamId.trim();
        registration.claimCallbackUrl = request.callbackUrl == null ? "" : request.callbackUrl.trim();
        registration.claimCallbackSecret = request.callbackSecret == null ? "" : request.callbackSecret.trim();
        registration.registeredAtMillis = Instant.now().toEpochMilli();
        if (request.ttlSeconds > 0) {
            registration.expiresAtMillis = registration.registeredAtMillis + request.ttlSeconds * 1000L;
        }

        CsaFlagBridgeState.Registration old = state.registrations.get(token);
        if (old != null) {
            registration.boundPlayers.addAll(old.boundPlayers);
            registration.consumed = old.consumed;
            if (registration.claimCallbackUrl.isEmpty()) {
                registration.claimCallbackUrl = old.claimCallbackUrl;
            }
            if (registration.claimCallbackSecret.isEmpty()) {
                registration.claimCallbackSecret = old.claimCallbackSecret;
            }
        }

        state.putRegistration(registration);
        if (config.notifyOpsOnRegistration) {
            logToOps("registered token for team=" + registration.teamId);
        }
        return RegisterResult.success();
    }

    public synchronized CommandResult bindPlayer(ServerPlayer player, String rawToken) {
        String token = normalizeToken(rawToken);
        if (token.isEmpty()) {
            return CommandResult.failure("token 不能为空");
        }

        CsaFlagBridgeState.Registration registration = state.getRegistration(token);
        if (registration == null) {
            return CommandResult.failure("这个 token 还没有注册，确认 ret2shell 实例已经启动并完成注册");
        }
        if (registration.consumed) {
            return CommandResult.failure("这个 token 已经被使用完毕");
        }

        String playerUuid = player.getUUID().toString();
        String current = state.getBinding(playerUuid);
        if (current != null && !current.equals(token) && !config.allowTokenRebind) {
            return CommandResult.failure("你已经绑定过 token，需要管理员开启 allowTokenRebind 或先执行 /csa unbind");
        }
        if (!registration.boundPlayers.contains(playerUuid) && registration.boundPlayers.size() >= config.maxPlayersPerToken) {
            return CommandResult.failure("这个 token 的绑定人数已达到上限");
        }

        try {
            state.bind(playerUuid, token);
            bindGateAnchors.remove(playerUuid);
            return CommandResult.success("绑定成功。注意续期 Ret2Shell 靶机，靶机过期或重开后请重新绑定新 token");
        } catch (IOException e) {
            CsaFlagBridgeServerMod.LOGGER.error("Failed to save binding", e);
            return CommandResult.failure("绑定已处理，但保存状态失败，请联系管理员");
        }
    }

    public synchronized CommandResult unbindPlayer(ServerPlayer player) {
        String playerUuid = player.getUUID().toString();
        try {
            state.unbind(playerUuid);
            bindGateAnchors.put(playerUuid, BindGateAnchor.from(player));
            return CommandResult.success("已解绑当前 Minecraft 账号，并固定在当前位置。需要继续时，请续期或重开 Ret2Shell 靶机后重新绑定新 token");
        } catch (IOException e) {
            CsaFlagBridgeServerMod.LOGGER.error("Failed to unbind player", e);
            return CommandResult.failure("解绑失败，请联系管理员");
        }
    }

    public synchronized CommandResult status(ServerPlayer player) {
        String token = state.getBinding(player.getUUID().toString());
        if (token == null) {
            return CommandResult.failure("当前 Minecraft 账号还没有绑定 token");
        }
        CsaFlagBridgeState.Registration registration = state.getRegistration(token);
        if (registration == null) {
            return CommandResult.failure("已绑定 token，但 token 未注册或已过期。请续期或重新启动 Ret2Shell 靶机后，再绑定新的 token");
        }
        if (shouldApplyClaimOnceLimit(player) && state.hasClaimed(player.getUUID().toString())) {
            return CommandResult.success("已绑定，且已经领取过 flag");
        }
        return CommandResult.success("已绑定。注意续期 Ret2Shell 靶机，靶机过期或重开后请重新绑定新 token");
    }

    public void tickBindGate() {
        if (!config.bindGateEnabled) {
            return;
        }

        bindGateTicks++;
        if (bindGateTicks % config.bindGateIntervalTicks != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isBindGateBypassed(player) || isPlayerBound(player)) {
                continue;
            }
            keepUnboundPlayerInPlace(player);
            player.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS,
                config.bindGateEffectDurationTicks,
                0,
                false,
                false,
                true
            ));

            if (bindGateTicks % config.bindGateMessageIntervalTicks == 0) {
                player.sendSystemMessage(Component.literal(formatMessage("请先执行 /csa bind <ret2shell 给你的 CSA_TOKEN>")));
            }
        }
    }

    public void handleTerminalPlaced(ServerPlayer player, BlockPos pos) {
        CommandResult result = claimForPlayer(player, pos);
        if (!result.message().isEmpty()) {
            player.sendSystemMessage(Component.literal(formatMessage(result.message())));
        }
    }

    private synchronized CommandResult claimForPlayer(ServerPlayer player, BlockPos pos) {
        String playerUuid = player.getUUID().toString();
        String token = state.getBinding(playerUuid);
        if (token == null) {
            if (config.notifyPlayerWhenUnbound) {
                return CommandResult.failure("未绑定 ret2shell token，先执行 /csa bind <token>");
            }
            return CommandResult.failure("");
        }

        CsaFlagBridgeState.Registration registration = state.getRegistration(token);
        if (registration == null) {
            return CommandResult.failure("你的 token 未注册或已过期。请续期或重新启动 Ret2Shell 靶机后，再绑定新的 token");
        }
        if (registration.consumed && !bypassesClaimLimits(player)) {
            return CommandResult.failure("你的 token 已经使用完毕");
        }
        if (shouldApplyClaimOnceLimit(player) && state.hasClaimed(playerUuid)) {
            return CommandResult.success("你已经领取过 flag");
        }

        ClaimCallbackResult callbackResult = deliverFlagToCallback(registration, player, pos);
        if (callbackResult.configured() && !callbackResult.ok()) {
            return CommandResult.failure("flag 回传到靶机页面失败，请稍后重试或联系管理员");
        }

        try {
            if (!bypassesClaimLimits(player)) {
                state.markClaimed(playerUuid);
            }
            if (config.consumeTokenOnFirstClaim && !bypassesClaimLimits(player)) {
                registration.consumed = true;
                state.save();
            }
        } catch (IOException e) {
            CsaFlagBridgeServerMod.LOGGER.error("Failed to persist claim state", e);
            return CommandResult.failure("flag 已判定可领取，但保存状态失败，请联系管理员");
        }

        CsaFlagBridgeServerMod.LOGGER.info(
            "Flag claimed by {} ({}) at {} {} {}",
            player.getName().getString(),
            playerUuid,
            pos.getX(),
            pos.getY(),
            pos.getZ()
        );
        if (callbackResult.configured()) {
            return CommandResult.success("flag 已回传到你的 Ret2Shell 靶机页面，请回浏览器复制提交");
        }
        return CommandResult.success("你的 flag: " + registration.flag);
    }

    private ClaimCallbackResult deliverFlagToCallback(CsaFlagBridgeState.Registration registration, ServerPlayer player, BlockPos pos) {
        if (!config.enableClaimCallback || registration.claimCallbackUrl.isBlank() || registration.claimCallbackSecret.isBlank()) {
            return ClaimCallbackResult.notConfigured();
        }

        try {
            URI uri = URI.create(registration.claimCallbackUrl);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                CsaFlagBridgeServerMod.LOGGER.warn("Rejected unsupported claim callback scheme for team={}", registration.teamId);
                return ClaimCallbackResult.failure();
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("token", registration.token);
            payload.put("flag", registration.flag);
            payload.put("team_id", registration.teamId);
            payload.put("player_uuid", player.getUUID().toString());
            payload.put("player_name", player.getName().getString());
            payload.put("x", pos.getX());
            payload.put("y", pos.getY());
            payload.put("z", pos.getZ());

            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.claimCallbackTimeoutMillis))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-CSA-Callback-Secret", registration.claimCallbackSecret)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ClaimCallbackResult.success();
            }

            CsaFlagBridgeServerMod.LOGGER.warn(
                "Claim callback failed for team={} status={}",
                registration.teamId,
                response.statusCode()
            );
            return ClaimCallbackResult.failure();
        } catch (Exception e) {
            CsaFlagBridgeServerMod.LOGGER.warn("Claim callback failed for team={}", registration.teamId, e);
            return ClaimCallbackResult.failure();
        }
    }

    private synchronized boolean isPlayerBound(ServerPlayer player) {
        String token = state.getBinding(player.getUUID().toString());
        return token != null && state.getRegistration(token) != null;
    }

    private boolean shouldApplyClaimOnceLimit(ServerPlayer player) {
        return config.claimOncePerPlayer && !bypassesClaimLimits(player);
    }

    private boolean bypassesClaimLimits(ServerPlayer player) {
        return server.getPlayerList().isOp(player.getGameProfile());
    }

    private boolean isBindGateBypassed(ServerPlayer player) {
        return config.bindGateBypassOps && server.getPlayerList().isOp(player.getGameProfile());
    }

    private void keepUnboundPlayerInPlace(ServerPlayer player) {
        BindGateAnchor anchor = bindGateAnchors.get(player.getUUID().toString());
        if (anchor != null) {
            keepPlayerAtAnchor(player, anchor);
            return;
        }
        keepPlayerAtSpawn(player);
    }

    private void keepPlayerAtSpawn(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() + 0.5;
        double y = spawn.getY();
        double z = spawn.getZ() + 0.5;
        double dx = player.getX() - x;
        double dy = player.getY() - y;
        double dz = player.getZ() - z;
        double radiusSquared = config.bindGateRadius * config.bindGateRadius;

        if (dx * dx + dy * dy + dz * dz > radiusSquared) {
            player.teleportTo(level, x, y, z, level.getSharedSpawnAngle(), 0.0f);
            player.setDeltaMovement(0.0, 0.0, 0.0);
        }
    }

    private void keepPlayerAtAnchor(ServerPlayer player, BindGateAnchor anchor) {
        ServerLevel level = server.getLevel(anchor.dimension());
        if (level == null) {
            bindGateAnchors.remove(player.getUUID().toString());
            keepPlayerAtSpawn(player);
            return;
        }

        boolean wrongDimension = !player.serverLevel().dimension().equals(anchor.dimension());
        double dx = player.getX() - anchor.x();
        double dy = player.getY() - anchor.y();
        double dz = player.getZ() - anchor.z();
        double radiusSquared = config.bindGateRadius * config.bindGateRadius;

        if (wrongDimension || dx * dx + dy * dy + dz * dz > radiusSquared) {
            player.teleportTo(level, anchor.x(), anchor.y(), anchor.z(), anchor.yRot(), anchor.xRot());
            player.setDeltaMovement(0.0, 0.0, 0.0);
        }
    }

    private void logToOps(String message) {
        CsaFlagBridgeServerMod.LOGGER.info(message);
        server.getPlayerList().getPlayers().stream()
            .filter(player -> server.getPlayerList().isOp(player.getGameProfile()))
            .forEach(player -> player.sendSystemMessage(Component.literal(formatMessage(message))));
    }

    public String formatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return config.messagePrefix + " " + message;
    }

    public static String normalizeToken(String token) {
        if (token == null) {
            return "";
        }
        return token.trim().toLowerCase(Locale.ROOT);
    }

    public record RegisterRequest(String token, String flag, String teamId, long ttlSeconds, String callbackUrl, String callbackSecret) {
    }

    public record RegisterResult(boolean ok, String message) {
        public static RegisterResult success() {
            return new RegisterResult(true, "ok");
        }

        public static RegisterResult error(String message) {
            return new RegisterResult(false, message);
        }
    }

    public record CommandResult(boolean ok, String message) {
        public static CommandResult success(String message) {
            return new CommandResult(true, message);
        }

        public static CommandResult failure(String message) {
            return new CommandResult(false, message);
        }
    }

    private record ClaimCallbackResult(boolean configured, boolean ok) {
        static ClaimCallbackResult notConfigured() {
            return new ClaimCallbackResult(false, false);
        }

        static ClaimCallbackResult success() {
            return new ClaimCallbackResult(true, true);
        }

        static ClaimCallbackResult failure() {
            return new ClaimCallbackResult(true, false);
        }
    }

    private record BindGateAnchor(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {
        static BindGateAnchor from(ServerPlayer player) {
            return new BindGateAnchor(
                player.serverLevel().dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
            );
        }
    }
}
