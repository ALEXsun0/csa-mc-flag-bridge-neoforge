package cn.cauccsa.flagbridge.server;

import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CsaFlagBridgeServerMod.MOD_ID)
public final class CsaFlagBridgeServerMod {
    public static final String MOD_ID = "csa_flag_bridge_server";
    public static final String CONFIG_DIR = "csa_flag_bridge";
    public static final ResourceLocation FLAG_TERMINAL_BLOCK_ID =
        ResourceLocation.fromNamespaceAndPath("csa_flag_bridge", "flag_terminal");
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile FlagBridgeService service;
    private static volatile Ret2ShellHttpServer httpServer;

    public CsaFlagBridgeServerMod(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CsaCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            CsaFlagBridgeConfig config = CsaFlagBridgeConfig.load(FMLPaths.CONFIGDIR.get());
            CsaFlagBridgeState state = CsaFlagBridgeState.load(FMLPaths.CONFIGDIR.get());
            service = new FlagBridgeService(event.getServer(), config, state);
            httpServer = new Ret2ShellHttpServer(service, config);
            httpServer.start();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize CSA Flag Bridge server", e);
            throw new IllegalStateException("Failed to initialize CSA Flag Bridge server", e);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (httpServer != null) {
            httpServer.close();
            httpServer = null;
        }
        service = null;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        FlagBridgeService current = service;
        if (current != null) {
            current.tickBindGate();
        }
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        FlagBridgeService current = service;
        if (current == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
        if (FLAG_TERMINAL_BLOCK_ID.equals(blockId)) {
            BlockPos pos = event.getPos();
            current.handleTerminalPlaced(player, pos);
        }
    }

    public static FlagBridgeService service() {
        return service;
    }
}
