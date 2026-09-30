package dev.celestiacraft.industrialplatform.proxy;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.blueprint.BlueprintLibrary;
import dev.celestiacraft.industrialplatform.registry.IPRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Client event handlers (ClientModels / PreviewRenderer / BuilderHudOverlay /
        // ClientConfigCache) subscribe via @Mod.EventBusSubscriber. Do NOT switch them
        // to explicit MinecraftForge.EVENT_BUS.register(instance) calls: on the
        // Cleanroom core used by target modpacks those subscriptions succeed but
        // never receive events (verified via [IP] breadcrumbs in 2.1.2).
        IndustrialPlatform.LOGGER.info("[IP] client preInit: annotation subscribers active");
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        IndustrialPlatform.LOGGER.info("[IP] client init: hologram / HUD handlers active");

        // Legacy direct mesher registration: extra insurance for tools that
        // remap item registry ids after baking (e.g. RoughlyEnoughIDs) and
        // break icon lookups keyed by id.
        Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(IPRegistry.ITEM_PLATFORM_BUILDER, 0,
                new ModelResourceLocation(new ResourceLocation(IndustrialPlatform.MODID, "platform_builder"), "inventory"));

        // Load local blueprints so the builder GUI can offer them in singleplayer.
        BlueprintLibrary.load(Minecraft.getMinecraft().gameDir, "client-init");
    }
}
