package dev.celestiacraft.industrialplatform.registry;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.block.PlatformBuilderBlock;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID)
public final class IPRegistry {

    public static final Block PLATFORM_BUILDER = new PlatformBuilderBlock();
    public static final Item ITEM_PLATFORM_BUILDER = new ItemBlock(PLATFORM_BUILDER)
            .setRegistryName(PLATFORM_BUILDER.getRegistryName());

    private IPRegistry() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(PLATFORM_BUILDER);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(ITEM_PLATFORM_BUILDER);
    }
}
