package cn.cauccsa.aerobridge.content;

import java.time.Instant;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FlightRecorderBlockEntity extends BlockEntity {
    private static volatile RecorderHandler recorderHandler = RecorderHandler.NOOP;

    private UUID placerUuid;
    private String placerName = "";
    private long placedAtMillis = 0;
    private boolean claimed = false;
    private int stableTicks = 0;

    public FlightRecorderBlockEntity(BlockPos pos, BlockState blockState) {
        super(CsaAeroBridgeMod.FLIGHT_RECORDER_BLOCK_ENTITY.get(), pos, blockState);
    }

    public void setPlacer(LivingEntity placer) {
        placerUuid = placer.getUUID();
        placerName = placer.getName().getString();
        placedAtMillis = Instant.now().toEpochMilli();
        claimed = false;
        stableTicks = 0;
        setChanged();
    }

    public void serverTick() {
        recorderHandler.tick(this);
    }

    public UUID placerUuid() {
        return placerUuid;
    }

    public String placerName() {
        return placerName;
    }

    public long placedAtMillis() {
        return placedAtMillis;
    }

    public boolean claimed() {
        return claimed;
    }

    public int stableTicks() {
        return stableTicks;
    }

    public void setStableTicks(int stableTicks) {
        this.stableTicks = Math.max(0, stableTicks);
        setChanged();
    }

    public void markClaimed(int finalStableTicks) {
        claimed = true;
        stableTicks = Math.max(stableTicks, finalStableTicks);
        setChanged();
    }

    public Level recorderLevel() {
        return level;
    }

    public static void setRecorderHandler(RecorderHandler handler) {
        recorderHandler = handler == null ? RecorderHandler.NOOP : handler;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (placerUuid != null) {
            tag.putUUID("PlacerUuid", placerUuid);
        }
        tag.putString("PlacerName", placerName);
        tag.putLong("PlacedAtMillis", placedAtMillis);
        tag.putBoolean("Claimed", claimed);
        tag.putInt("StableTicks", stableTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        placerUuid = tag.hasUUID("PlacerUuid") ? tag.getUUID("PlacerUuid") : null;
        placerName = tag.getString("PlacerName");
        placedAtMillis = tag.getLong("PlacedAtMillis");
        claimed = tag.getBoolean("Claimed");
        stableTicks = tag.getInt("StableTicks");
    }

    @FunctionalInterface
    public interface RecorderHandler {
        RecorderHandler NOOP = recorder -> {
        };

        void tick(FlightRecorderBlockEntity recorder);
    }
}
