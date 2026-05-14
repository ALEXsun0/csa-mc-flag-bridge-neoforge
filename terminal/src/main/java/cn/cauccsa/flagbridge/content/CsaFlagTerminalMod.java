package cn.cauccsa.flagbridge.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CsaFlagTerminalMod.MOD_ID)
public final class CsaFlagTerminalMod {
    public static final String MOD_ID = "csa_flag_terminal";
    public static final String BLOCK_NAMESPACE = "csa_flag_bridge";
    public static final ResourceLocation FLAG_TERMINAL_ID =
        ResourceLocation.fromNamespaceAndPath(BLOCK_NAMESPACE, "flag_terminal");

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(BLOCK_NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BLOCK_NAMESPACE);

    public static final DeferredBlock<Block> FLAG_TERMINAL = BLOCKS.registerSimpleBlock(
        "flag_terminal",
        BlockBehaviour.Properties.of()
            .strength(3.0f, 1200.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
    );
    public static final DeferredItem<BlockItem> FLAG_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem(FLAG_TERMINAL);

    public CsaFlagTerminalMod(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreativeTabEntries);
    }

    private void addCreativeTabEntries(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(FLAG_TERMINAL_ITEM);
        }
    }
}
