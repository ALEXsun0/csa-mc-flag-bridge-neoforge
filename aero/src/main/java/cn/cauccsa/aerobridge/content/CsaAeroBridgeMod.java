package cn.cauccsa.aerobridge.content;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CsaAeroBridgeMod.MOD_ID)
public final class CsaAeroBridgeMod {
    public static final String MOD_ID = "csa_aero_bridge";
    public static final String FLIGHT_RECORDER_ID = "flight_recorder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final DeferredBlock<FlightRecorderBlock> FLIGHT_RECORDER = BLOCKS.register(
        FLIGHT_RECORDER_ID,
        () -> new FlightRecorderBlock(BlockBehaviour.Properties.of()
            .strength(3.0f, 1200.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion())
    );
    public static final DeferredItem<BlockItem> FLIGHT_RECORDER_ITEM = ITEMS.registerSimpleBlockItem(FLIGHT_RECORDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlightRecorderBlockEntity>> FLIGHT_RECORDER_BLOCK_ENTITY =
        BLOCK_ENTITY_TYPES.register(
            FLIGHT_RECORDER_ID,
            () -> BlockEntityType.Builder.of(FlightRecorderBlockEntity::new, FLIGHT_RECORDER.get()).build(null)
        );

    public CsaAeroBridgeMod(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabEntries);
    }

    private void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(FLIGHT_RECORDER_ITEM);
        }
    }
}
