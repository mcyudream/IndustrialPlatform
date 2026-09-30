package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.model.ModelRotation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;

/**
 * Late-stage model self-healing.
 *
 * Target modpacks run StellarCore's ParallelModelLoader, which wraps the model
 * registry in a concurrent implementation during loading and swaps it back to
 * the default implementation AFTER {@code ModelBakeEvent} — writes made inside
 * that event are silently discarded. So instead of fighting the load pipeline,
 * this healer runs on the first rendered frame, inspects the FINAL live
 * {@code ModelManager} registry, and bakes + injects our models directly into
 * it if they are missing or broken (missingno quads). The per-blockstate
 * Guava cache in BlockModelShapes is invalidated too, otherwise chunk
 * renderers keep serving the stale cached missing model.
 *
 * Re-runs automatically if the registry object is replaced by a later
 * resource reload (F3+T).
 */
public final class ModelHealer {

    private static final ModelResourceLocation INVENTORY_KEY =
            new ModelResourceLocation(IndustrialPlatform.MODID + ":platform_builder", "inventory");
    private static final ModelResourceLocation NORMAL_KEY =
            new ModelResourceLocation(IndustrialPlatform.MODID + ":platform_builder", "normal");

    private static IRegistry<ModelResourceLocation, IBakedModel> lastRegistry;
    private static boolean loggedOnce;

    private ModelHealer() {
    }

    /** Called from the render loop (both alive handlers) once per frame until healed. */
    public static void ensureHealed(Minecraft mc) {
        try {
            IRegistry<ModelResourceLocation, IBakedModel> live = findLiveRegistry(mc);
            if (live == null) {
                return;
            }
            if (live == lastRegistry) {
                return; // same registry object as the one we already healed
            }
            lastRegistry = live;

            boolean healedInventory = heal(mc, live, INVENTORY_KEY, "inventory");
            boolean healedNormal = heal(mc, live, NORMAL_KEY, "normal");

            if (healedInventory || healedNormal) {
                invalidateBlockStateCache(mc);
                // ItemModelMesher keeps its own baked-model cache (simpleShapesCache)
                // which does not follow registry writes — rebuild it from the healed registry.
                try {
                    mc.getRenderItem().getItemModelMesher().rebuildCache();
                    IndustrialPlatform.LOGGER.info("[IP] ModelHealer: item mesher cache rebuilt");
                } catch (Throwable t) {
                    IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: mesher rebuild failed: {}", Objects.toString(t));
                }
                mc.renderGlobal.loadRenderers();
                IndustrialPlatform.LOGGER.info("[IP] ModelHealer: injected into live registry (inv={} normal={}) and rebuilt chunks",
                        healedInventory, healedNormal);
            }
            if (!loggedOnce) {
                loggedOnce = true;
                IndustrialPlatform.LOGGER.info("[IP] ModelHealer: live registry checked (inv={} normal={})",
                        describe(live.getObject(INVENTORY_KEY)), describe(live.getObject(NORMAL_KEY)));
            }
        } catch (Throwable t) {
            IndustrialPlatform.LOGGER.error("[IP] ModelHealer: unexpected failure", t);
            lastRegistry = null; // let a later frame try again
        }
    }

    /**
     * 1.12.2 has no Minecraft#getModelManager; reach it through the item mesher,
     * then read its registry field reflectively (runtime names are SRG, so match
     * by exact field type).
     */
    private static IRegistry<ModelResourceLocation, IBakedModel> findLiveRegistry(Minecraft mc) {
        ModelManager manager = mc.getRenderItem().getItemModelMesher().getModelManager();
        if (manager == null) {
            return null;
        }
        for (Class<?> cls = manager.getClass(); cls != null; cls = cls.getSuperclass()) {
            for (Field field : cls.getDeclaredFields()) {
                if (field.getType() == IRegistry.class) {
                    try {
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        IRegistry<ModelResourceLocation, IBakedModel> registry =
                                (IRegistry<ModelResourceLocation, IBakedModel>) field.get(manager);
                        return registry;
                    } catch (Throwable t) {
                        IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: registry field unreadable: {}", Objects.toString(t));
                        return null;
                    }
                }
            }
        }
        if (!loggedOnce) {
            IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: no IRegistry field found on {}", manager.getClass().getName());
        }
        return null;
    }

    private static boolean heal(Minecraft mc, IRegistry<ModelResourceLocation, IBakedModel> registry,
                                ModelResourceLocation key, String label) {
        IBakedModel existing = registry.getObject(key);
        if (existing != null && !isBroken(existing)) {
            return false;
        }
        IBakedModel fresh = bake(mc);
        if (fresh == null) {
            IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: cannot bake model for {}", label);
            return false;
        }
        if (isBroken(fresh)) {
            // atlas lacks our sprite — a full resource reload re-stitches textures
            // and the healer re-runs automatically afterwards (registry identity changes)
            IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: fresh bake for {} is still missingno (atlas sprite: '{}') — triggering reload",
                    label, mc.getTextureMapBlocks()
                            .getAtlasSprite(IndustrialPlatform.MODID + ":block/builder/platform_builder").getIconName());
            fallbackReloadOnce(mc);
            return false;
        }
        registry.putObject(key, fresh);
        IndustrialPlatform.LOGGER.info("[IP] ModelHealer: {} was {} — replaced", label,
                existing == null ? "absent" : "broken");
        return true;
    }

    /** True when the model is Forge's missing model or its quads reference the missingno sprite. */
    private static boolean isBroken(IBakedModel model) {
        String cls = model.getClass().getName();
        if (cls.contains("Missing") || cls.contains("missing")) {
            return true;
        }
        for (EnumFacing side : EnumFacing.values()) {
            List<BakedQuad> quads = model.getQuads(null, side, 0L);
            if (quads == null || quads.isEmpty()) {
                continue;
            }
            for (BakedQuad quad : quads) {
                String icon = quad.getSprite() == null ? null : quad.getSprite().getIconName();
                if (icon != null && icon.contains("missingno")) {
                    return true;
                }
            }
            return false; // first non-empty side has healthy sprites
        }
        return false;
    }

    private static IBakedModel bake(Minecraft mc) {
        try {
            IModel model = ModelLoaderRegistry.getModel(
                    new ResourceLocation(IndustrialPlatform.MODID, "block/platform_builder"));
            IBakedModel baked = model.bake(ModelRotation.X0_Y0, DefaultVertexFormats.BLOCK,
                    loc -> mc.getTextureMapBlocks().getAtlasSprite(loc.toString()));
            boolean hasQuads = false;
            for (EnumFacing side : EnumFacing.values()) {
                List<BakedQuad> quads = baked.getQuads(null, side, 0L);
                if (quads != null && !quads.isEmpty()) {
                    IndustrialPlatform.LOGGER.info("[IP] ModelHealer: baked model uses sprite '{}'",
                            quads.get(0).getSprite().getIconName());
                    hasQuads = true;
                    break;
                }
            }
            if (!hasQuads) {
                IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: baked model has no quads on any side");
            }
            return baked;
        } catch (Exception e) {
            IndustrialPlatform.LOGGER.error("[IP] ModelHealer: bake failed", e);
            return null;
        }
    }

    private static String describe(IBakedModel model) {
        return model == null ? "absent" : model.getClass().getSimpleName();
    }

    /** BlockModelShapes keeps a per-IBlockState Guava cache; drop it so chunks re-lookup.
     *  The field is declared on BlockModelShapes itself, but cores like OptiFine swap in
     *  a SUBCLASS at runtime — walk the whole superclass chain or the field is missed. */
    private static void invalidateBlockStateCache(Minecraft mc) {
        try {
            Object shapes = mc.getBlockRendererDispatcher().getBlockModelShapes();
            for (Class<?> cls = shapes.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                for (Field field : cls.getDeclaredFields()) {
                    if (field.getType().getName().endsWith("LoadingCache")) {
                        field.setAccessible(true);
                        Object cache = field.get(shapes);
                        if (cache != null) {
                            cache.getClass().getMethod("invalidateAll").invoke(cache);
                            IndustrialPlatform.LOGGER.info("[IP] ModelHealer: blockstate model cache invalidated (declared on {})",
                                    cls.getName());
                            return;
                        }
                    }
                }
            }
            IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: blockstate cache field not found anywhere in {} — falling back to full resource reload once",
                    shapes.getClass().getName());
            fallbackReloadOnce(mc);
        } catch (Throwable t) {
            IndustrialPlatform.LOGGER.warn("[IP] ModelHealer: blockstate cache invalidation failed: {}", Objects.toString(t));
            fallbackReloadOnce(mc);
        }
    }

    private static boolean fallbackReloadTried;

    /** Last resort when the cache cannot be reached: one full resource reload.
     *  The healer re-runs automatically afterwards (registry identity changes). */
    private static void fallbackReloadOnce(Minecraft mc) {
        if (fallbackReloadTried) {
            return;
        }
        fallbackReloadTried = true;
        IndustrialPlatform.LOGGER.info("[IP] ModelHealer: triggering one full resource reload");
        mc.refreshResources();
    }
}
