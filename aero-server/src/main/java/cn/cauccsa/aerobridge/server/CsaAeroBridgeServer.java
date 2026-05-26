package cn.cauccsa.aerobridge.server;

import cn.cauccsa.aerobridge.content.FlightRecorderBlockEntity;
import java.io.IOException;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CsaAeroBridgeServer.MOD_ID)
public final class CsaAeroBridgeServer {
    public static final String MOD_ID = "csa_aero_bridge_server";
    public static final String CONFIG_DIR = "csa_aero_bridge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final CsaAeroBridgeConfig DEFAULT_CONFIG = new CsaAeroBridgeConfig();
    private static volatile CsaAeroBridgeConfig config = DEFAULT_CONFIG;

    public CsaAeroBridgeServer(IEventBus modEventBus) {
        FlightRecorderBlockEntity.setRecorderHandler(CsaAeroRecorderHandler::tick);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            config = CsaAeroBridgeConfig.load(FMLPaths.CONFIGDIR.get());
        } catch (IOException e) {
            LOGGER.error("Failed to load CSA Aero Bridge config", e);
            throw new IllegalStateException("Failed to load CSA Aero Bridge config", e);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CsaAeroBridgeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        CsaAeroRecorderHandler.cleanup(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CsaAeroRecorderHandler.shutdown();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            CsaAeroStarterItems.giveOnFirstLogin(player);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        CsaAeroSheepProduction.onEntityInteract(event);
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        CsaAeroSheepProduction.onLivingDrops(event);
    }

    public static CsaAeroBridgeConfig config() {
        return config;
    }

    public static String formatMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        return config().messagePrefix + " " + message;
    }
}
