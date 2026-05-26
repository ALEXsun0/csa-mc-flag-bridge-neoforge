package cn.cauccsa.aerobridge.server;

import static net.minecraft.commands.Commands.literal;

import cn.cauccsa.aerobridge.content.CsaAeroBridgeMod;
import cn.cauccsa.flagbridge.server.CsaFlagBridgeServerMod;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class CsaAeroBridgeCommands {
    private static final Map<UUID, Long> LAST_RECORDER_ISSUED_AT = new HashMap<>();

    private CsaAeroBridgeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("csaero")
            .then(literal("recorder")
                .executes(context -> giveRecorder(context.getSource().getPlayerOrException())))
            .then(literal("requirements")
                .executes(context -> showRequirements(context.getSource()))));
    }

    private static int giveRecorder(ServerPlayer player) {
        CsaAeroBridgeConfig config = CsaAeroBridgeServer.config();
        boolean op = player.getServer().getPlayerList().isOp(player.getGameProfile());

        if (!op && !CsaFlagBridgeServerMod.isUuidBound(player.getUUID())) {
            player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage("请先执行 /csa bind <ret2shell 给你的 CSA_TOKEN>")));
            return 0;
        }

        if (!op) {
            long now = Instant.now().toEpochMilli();
            long lastIssuedAt = LAST_RECORDER_ISSUED_AT.getOrDefault(player.getUUID(), 0L);
            long cooldownMillis = config.recorderCooldownSeconds * 1000L;
            long remainingMillis = cooldownMillis - (now - lastIssuedAt);
            if (remainingMillis > 0) {
                long remainingSeconds = Math.max(1, (remainingMillis + 999L) / 1000L);
                player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage("飞行记录仪领取冷却中，剩余 " + remainingSeconds + " 秒")));
                return 0;
            }

            int count = countRecorders(player);
            if (count >= config.maxRecordersInInventory) {
                player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage(
                    "背包内最多持有 " + config.maxRecordersInInventory + " 个 CSA 飞行记录仪"
                )));
                return 0;
            }
        }

        ItemStack stack = new ItemStack(CsaAeroBridgeMod.FLIGHT_RECORDER_ITEM.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, false);
        }

        LAST_RECORDER_ISSUED_AT.put(player.getUUID(), Instant.now().toEpochMilli());
        player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage(
            "已发放 CSA 飞行记录仪。将它安装在航空学飞行器上，满足任一路线即可完成认证。"
        )));
        player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage(requirementsText(config))));
        return Command.SINGLE_SUCCESS;
    }

    private static int showRequirements(CommandSourceStack source) {
        source.sendSuccess(
            () -> Component.literal(CsaAeroBridgeServer.formatMessage(requirementsText(CsaAeroBridgeServer.config()))),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static String requirementsText(CsaAeroBridgeConfig config) {
        String stableRoute = String.format(
            Locale.ROOT,
            "稳定路线：连续 %.1f 秒，水平速度 >= %.1f，垂直速度 <= %.1f，角速度 <= %.2f，水平速度波动 <= %.1f",
            config.stableCruiseTicks / 20.0,
            config.minHorizontalSpeed,
            config.maxVerticalSpeed,
            config.maxAngularSpeed,
            config.maxHorizontalSpeedJitter
        );
        String highSpeedRoute = config.highSpeedClaimEnabled
            ? String.format(
                Locale.ROOT,
                "极速路线：水平速度 >= %.1f，连续 %.1f 秒",
                config.highSpeedHorizontalSpeed,
                config.highSpeedTicks / 20.0
            )
            : "极速路线：关闭";
        String altitudeRoute = config.altitudeClaimEnabled
            ? String.format(
                Locale.ROOT,
                "高度路线：高度 Y >= %.1f，水平速度 >= %.1f，连续 %.1f 秒",
                config.altitudeY,
                config.altitudeMinHorizontalSpeed,
                config.altitudeTicks / 20.0
            )
            : "高度路线：关闭";
        return "获取 flag 条件：满足任一路线即可。" + stableRoute + "；" + highSpeedRoute + "；" + altitudeRoute + "。飞行时顶部 HUD 会显示当前进度和阈值。";
    }

    private static int countRecorders(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(CsaAeroBridgeMod.FLIGHT_RECORDER_ITEM.get())) {
                count += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(CsaAeroBridgeMod.FLIGHT_RECORDER_ITEM.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
