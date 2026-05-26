package cn.cauccsa.aerobridge.server;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class CsaAeroSheepProduction {
    private CsaAeroSheepProduction() {
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        CsaAeroBridgeConfig config = CsaAeroBridgeServer.config();
        if (!config.sheepProductionBoostEnabled || !(event.getTarget() instanceof Sheep sheep)) {
            return;
        }

        Level level = event.getLevel();
        Player player = event.getEntity();
        ItemStack tool = event.getItemStack();
        if (level.isClientSide() || !tool.is(Items.SHEARS) || !sheep.readyForShearing()) {
            return;
        }

        level.playSound(null, sheep, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
        sheep.setSheared(true);

        ItemEntity itemEntity = sheep.spawnAtLocation(new ItemStack(woolFor(sheep.getColor()), config.sheepShearingWoolCount));
        if (itemEntity != null) {
            itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().add(randomScatter(level), level.random.nextFloat() * 0.05F, randomScatter(level)));
        }

        sheep.gameEvent((Holder<GameEvent>) GameEvent.SHEAR, player);
        tool.hurtAndBreak(1, player, LivingEntity.getSlotForHand(event.getHand()));
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        event.setCanceled(true);
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        CsaAeroBridgeConfig config = CsaAeroBridgeServer.config();
        if (!config.sheepProductionBoostEnabled || config.sheepDeathWoolDropMultiplier <= 1 || !(event.getEntity() instanceof Sheep)) {
            return;
        }

        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (isWool(stack.getItem())) {
                int boostedCount = Math.min(stack.getMaxStackSize(), stack.getCount() * config.sheepDeathWoolDropMultiplier);
                stack.setCount(boostedCount);
            }
        }
    }

    private static double randomScatter(Level level) {
        return (level.random.nextFloat() - level.random.nextFloat()) * 0.1F;
    }

    private static boolean isWool(Item item) {
        return item == Items.WHITE_WOOL
            || item == Items.ORANGE_WOOL
            || item == Items.MAGENTA_WOOL
            || item == Items.LIGHT_BLUE_WOOL
            || item == Items.YELLOW_WOOL
            || item == Items.LIME_WOOL
            || item == Items.PINK_WOOL
            || item == Items.GRAY_WOOL
            || item == Items.LIGHT_GRAY_WOOL
            || item == Items.CYAN_WOOL
            || item == Items.PURPLE_WOOL
            || item == Items.BLUE_WOOL
            || item == Items.BROWN_WOOL
            || item == Items.GREEN_WOOL
            || item == Items.RED_WOOL
            || item == Items.BLACK_WOOL;
    }

    private static Item woolFor(DyeColor color) {
        return switch (color) {
            case WHITE -> Items.WHITE_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case LIME -> Items.LIME_WOOL;
            case PINK -> Items.PINK_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case RED -> Items.RED_WOOL;
            case BLACK -> Items.BLACK_WOOL;
        };
    }
}
