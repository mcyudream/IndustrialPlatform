package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.registry.IPRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Item-model registration, plus two layers of self-healing for heavy modpacks
 * whose optimization cores (parallel model loaders, stitcher caches, model
 * caches, registry id remappers) are known to drop or mangle models:
 *
 * <ul>
 *   <li>{@link TextureStitchEvent.Pre} — make sure our texture is stitched into
 *       the block atlas no matter whether any loader ever discovered it.</li>
 *   <li>{@link ModelBakeEvent} — bake our own block model synchronously at the
 *       very end of the pipeline and overwrite both the {@code inventory} and
 *       {@code normal} registry entries. Whatever happened earlier (race,
 *       swallowed exception, stale cache) is irrelevant afterwards.</li>
 * </ul>
 *
 * Registered via {@code @Mod.EventBusSubscriber} — on the Cleanroom core used by
 * target modpacks, explicit {@code MinecraftForge.EVENT_BUS.register(instance)}
 * subscribes without ever delivering events, while the annotation-scanned path
 * (same as the common registry handlers) demonstrably works.
 */
@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID, value = net.minecraftforge.fml.relauncher.Side.CLIENT)
public final class ClientModels {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(IndustrialPlatform.MODID, "block/builder/platform_builder");
    private static final ModelResourceLocation INVENTORY_KEY =
            new ModelResourceLocation(IndustrialPlatform.MODID + ":platform_builder", "inventory");
    private static final ModelResourceLocation NORMAL_KEY =
            new ModelResourceLocation(IndustrialPlatform.MODID + ":platform_builder", "normal");

    public ClientModels() {
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(IPRegistry.ITEM_PLATFORM_BUILDER, 0,
                new ModelResourceLocation(new ResourceLocation(IndustrialPlatform.MODID, "platform_builder"), "inventory"));
        IndustrialPlatform.LOGGER.info("[IP] ModelRegistryEvent: item model variant registered");
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        event.getMap().registerSprite(TEXTURE);
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        IBakedModel baked = bakeOwnBlockModel();
        if (baked == null) {
            return;
        }
        event.getModelRegistry().putObject(INVENTORY_KEY, baked);
        event.getModelRegistry().putObject(NORMAL_KEY, baked);
        IndustrialPlatform.LOGGER.info("[IP] ModelBakeEvent: self-healed baked models for inventory + normal");
    }

    /** Synchronously load and bake our own block model, bypassing the pack's loaders. */
    private static IBakedModel bakeOwnBlockModel() {
        try {
            IModel model = ModelLoaderRegistry.getModel(
                    new ResourceLocation(IndustrialPlatform.MODID, "block/platform_builder"));
            return model.bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                    loc -> Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(loc.toString()));
        } catch (Exception e) {
            IndustrialPlatform.LOGGER.error("[IP] ModelBakeEvent: self-heal bake failed", e);
            return null;
        }
    }
}
