package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.registry.IPRegistry;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID, value = Side.CLIENT)
public final class ClientModels {

    private ClientModels() {
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(IPRegistry.ITEM_PLATFORM_BUILDER, 0,
                new ModelResourceLocation(IndustrialPlatform.MODID + ":platform_builder", "inventory"));
    }
}
