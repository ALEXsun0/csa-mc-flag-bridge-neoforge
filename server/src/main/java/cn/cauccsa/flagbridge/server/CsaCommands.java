package cn.cauccsa.flagbridge.server;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CsaCommands {
    private CsaCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("csa")
            .then(literal("bind")
                .then(argument("token", greedyString())
                    .executes(context -> bind(context.getSource().getPlayerOrException(), getString(context, "token")))))
            .then(literal("status")
                .executes(context -> status(context.getSource().getPlayerOrException())))
            .then(literal("unbind")
                .executes(context -> unbind(context.getSource().getPlayerOrException())))
            .then(literal("http")
                .requires(source -> source.hasPermission(3))
                .executes(context -> httpStatus(context.getSource()))));
    }

    private static int bind(ServerPlayer player, String token) throws CommandSyntaxException {
        FlagBridgeService service = CsaFlagBridgeServerMod.service();
        if (service == null) {
            player.sendSystemMessage(Component.literal("[CSA] 服务还没有初始化完成"));
            return 0;
        }
        FlagBridgeService.CommandResult result = service.bindPlayer(player, token);
        player.sendSystemMessage(Component.literal(service.formatMessage(result.message())));
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int status(ServerPlayer player) throws CommandSyntaxException {
        FlagBridgeService service = CsaFlagBridgeServerMod.service();
        if (service == null) {
            player.sendSystemMessage(Component.literal("[CSA] 服务还没有初始化完成"));
            return 0;
        }
        FlagBridgeService.CommandResult result = service.status(player);
        player.sendSystemMessage(Component.literal(service.formatMessage(result.message())));
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int unbind(ServerPlayer player) throws CommandSyntaxException {
        FlagBridgeService service = CsaFlagBridgeServerMod.service();
        if (service == null) {
            player.sendSystemMessage(Component.literal("[CSA] 服务还没有初始化完成"));
            return 0;
        }
        FlagBridgeService.CommandResult result = service.unbindPlayer(player);
        player.sendSystemMessage(Component.literal(service.formatMessage(result.message())));
        return result.ok() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int httpStatus(CommandSourceStack source) {
        FlagBridgeService service = CsaFlagBridgeServerMod.service();
        if (service == null) {
            source.sendFailure(Component.literal("[CSA] 服务还没有初始化完成"));
            return 0;
        }
        CsaFlagBridgeConfig config = service.config();
        source.sendSuccess(
            () -> Component.literal("[CSA] HTTP registration endpoint: " + config.httpHost + ":" + config.httpPort),
            false
        );
        return Command.SINGLE_SUCCESS;
    }
}
