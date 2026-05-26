package cn.cauccsa.aerobridge.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class CsaAeroStarterItems {
    private static final String STARTER_GLUE_TAG = "csa_aero_starter_honey_glue_v1";
    private static final String STARTER_GOGGLES_TAG = "csa_aero_starter_goggles_v1";

    private CsaAeroStarterItems() {
    }

    public static void giveOnFirstLogin(ServerPlayer player) {
        CsaAeroBridgeConfig config = CsaAeroBridgeServer.config();
        giveStarterItemIfNeeded(
            player,
            config.starterHoneyGlueEnabled,
            config.starterHoneyGlueCount,
            config.starterHoneyGlueItemId,
            STARTER_GLUE_TAG,
            "蜂蜜胶"
        );
        giveStarterItemIfNeeded(
            player,
            config.starterGogglesEnabled,
            config.starterGogglesCount,
            config.starterGogglesItemId,
            STARTER_GOGGLES_TAG,
            "工程师护目镜"
        );
    }

    private static void giveStarterItemIfNeeded(
        ServerPlayer player,
        boolean enabled,
        int count,
        String itemIdRaw,
        String playerTag,
        String displayName
    ) {
        if (!enabled || count <= 0 || player.getTags().contains(playerTag)) {
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(itemIdRaw);
        if (itemId == null) {
            CsaAeroBridgeServer.LOGGER.warn("Invalid starter item id for {}: {}", displayName, itemIdRaw);
            return;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            CsaAeroBridgeServer.LOGGER.warn("Starter item for {} is not registered: {}", displayName, itemId);
            return;
        }

        giveItems(player, item, count);
        player.addTag(playerTag);
        player.sendSystemMessage(Component.literal(CsaAeroBridgeServer.formatMessage(
            "已发放开局物资：" + displayName + " x" + count
        )));
    }

    private static void giveItems(ServerPlayer player, Item item, int count) {
        int remaining = count;
        int maxStackSize = Math.max(1, new ItemStack(item).getMaxStackSize());
        while (remaining > 0) {
            int stackSize = Math.min(maxStackSize, remaining);
            ItemStack stack = new ItemStack(item, stackSize);
            remaining -= stackSize;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, false);
            }
        }
    }
}
