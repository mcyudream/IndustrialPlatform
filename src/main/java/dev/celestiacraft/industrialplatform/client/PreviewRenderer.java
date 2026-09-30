package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.block.PlatformBuilderBlock;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.PlatformLayout;
import dev.celestiacraft.industrialplatform.platform.PlatformRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.ArrayList;
import java.util.List;

/**
 * In-world hologram preview.
 *
 * The rendering recipe is ported verbatim from Building Gadgets 2.8.4
 * (ToolRenders#renderBuilderOverlay), which is proven to display correctly on
 * the Cleanroom + OptiFine + StellarCore stack used by target modpacks. The
 * three essential ingredients our first implementation was missing:
 *
 * <ul>
 *   <li>explicitly binding {@link TextureMap#LOCATION_BLOCKS_TEXTURE} before
 *       any ghost rendering;</li>
 *   <li>the {@code rotate(-90°, 0, 1, 0)} prerequisite of
 *       {@code renderBlockBrightness} — without it every face is culled and
 *       the ghosts are simply invisible;</li>
 *   <li>{@code blendFunc(CONSTANT_COLOR, CONSTANT_ALPHA)} with
 *       {@link GL14#glBlendColor} driving the translucency.</li>
 * </ul>
 *
 * Registered via {@code @Mod.EventBusSubscriber} (see {@link ClientModels} for
 * why explicit registration is not usable on the Cleanroom core); the builder
 * config resolves through {@link ClientConfigCache} so a flaky client-side
 * tile entity can't kill the hologram.
 */
@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID, value = Side.CLIENT)
public final class PreviewRenderer {

    /** Keep ghost counts bounded; huge platforms render their surface only. */
    private static final int MAX_GHOSTS = 16384;

    public static boolean active;
    public static BlockPos pos;
    public static PlatformConfig cfg;

    private static boolean firedLog;

    public PreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return;
        }
        ModelHealer.ensureHealed(mc);
        if (!firedLog) {
            firedLog = true;
            IndustrialPlatform.LOGGER.info("[IP] RenderWorldLast handler alive");
        }

        BlockPos target = null;
        PlatformConfig targetCfg = null;

        if (active && pos != null && cfg != null) {
            target = pos;
            targetCfg = cfg;
        } else {
            // a persistent preview must die with its builder — breakBlock clears
            // the statics, but world edits / chunk edge cases can slip past it
            if (pos != null && !(mc.world.getBlockState(pos).getBlock() instanceof PlatformBuilderBlock)) {
                IndustrialPlatform.LOGGER.info("[IP] persistent preview dropped: builder at {} is gone", pos);
                ClientConfigCache.forget(pos);
                active = false;
                pos = null;
                cfg = null;
            }
            BlockPos hovered = hoveredBuilder(mc);
            if (hovered != null) {
                targetCfg = ClientConfigCache.resolve(mc.world, hovered);
                target = targetCfg != null ? hovered : null;
                if (targetCfg == null) {
                    // no TE, no cache: ask the authoritative server once
                    ClientSyncer.maybeRequest(hovered);
                }
            }
            if (target == null && pos != null && cfg != null && cfg.previewOn) {
                target = pos;
                targetCfg = cfg;
            }
        }

        if (target == null || targetCfg == null) {
            return;
        }
        renderHologram(mc, target, targetCfg, event.getPartialTicks());
    }

    private static BlockPos hoveredBuilder(Minecraft mc) {
        if (mc.currentScreen != null) {
            return null; // no raytrace while a GUI is open
        }
        RayTraceResult trace = mc.objectMouseOver;
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK || trace.getBlockPos() == null) {
            return null;
        }
        BlockPos p = trace.getBlockPos();
        return mc.world.getBlockState(p).getBlock() instanceof PlatformBuilderBlock ? p : null;
    }

    private static void renderHologram(Minecraft mc, BlockPos pos, PlatformConfig config, float partialTicks) {
        EntityPlayer player = mc.player;

        config.clamp();
        PlatformLayout layout = new PlatformLayout(config);

        // Building Gadgets uses the interpolated FEET position — RenderWorldLast's
        // modelview is feet-relative (eye position sinks the hologram ~1.6 blocks).
        Vec3d playerPos = new Vec3d(
                player.prevPosX + (player.posX - player.prevPosX) * partialTicks,
                player.prevPosY + (player.posY - player.prevPosY) * partialTicks,
                player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks);

        int anchorX = (pos.getX() >> 4) * 16 + 8 + config.offsetX;
        int anchorZ = (pos.getZ() >> 4) * 16 + 8 + config.offsetZ;
        int x0 = anchorX - layout.centerX;
        int z0 = anchorZ - layout.centerZ;
        int y0 = Math.max(0, Math.min(255, pos.getY() + config.offsetY));
        int y1 = Math.min(255, y0 + config.layers);
        int surfaceY = y1 - 1;

        // surface cells to ghost — surfaceRefAt includes the center overlay, so
        // the hologram shows exactly what the generator builds. The builder's
        // own block is never ghosted.
        List<BlockRefCell> cells = new ArrayList<BlockRefCell>();
        for (int z = 0; z < layout.sizeZ && cells.size() < MAX_GHOSTS; z++) {
            for (int x = 0; x < layout.sizeX && cells.size() < MAX_GHOSTS; x++) {
                BlockRef ref = layout.surfaceRefAt(x, z);
                if (ref.isAir()) {
                    continue;
                }
                BlockPos cell = new BlockPos(x0 + x, surfaceY, z0 + z);
                if (cell.equals(pos)) {
                    continue;
                }
                cells.add(new BlockRefCell(cell, ref));
            }
        }
        if (config.autoTorches && surfaceY + 1 <= 255) {
            BlockRef torch = new BlockRef(net.minecraft.init.Blocks.TORCH, 0);
            for (int[] spot : layout.torchSpots()) {
                if (cells.size() >= MAX_GHOSTS) {
                    break;
                }
                cells.add(new BlockRefCell(new BlockPos(x0 + spot[0], surfaceY + 1, z0 + spot[1]), torch));
            }
        }

        // ---- Building Gadgets 2.8.4 ghost recipe (ToolRenders#renderBuilderOverlay) ----
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(32771, 32772); // CONSTANT_COLOR, CONSTANT_ALPHA

        float ghostAlpha = 0.42F + 0.10F * (float) Math.sin(System.currentTimeMillis() * 0.003);
        for (BlockRefCell cell : cells) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(cell.pos.getX() - playerPos.x, cell.pos.getY() - playerPos.y, cell.pos.getZ() - playerPos.z);
            GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(1.0F, 1.0F, 1.0F);
            GL14.glBlendColor(1.0F, 1.0F, 1.0F, ghostAlpha);
            mc.getBlockRendererDispatcher().renderBlockBrightness(
                    cell.ref.block.getStateFromMeta(cell.ref.meta), 1.0F);
            GlStateManager.popMatrix();
        }

        // ---- wireframe volume (Building Gadgets renderDestructionOverlay box recipe:
        //      global translate by -playerPos, world coordinates, no texture) ----
        double minX = x0, maxX = x0 + layout.sizeX;
        double minZ = z0, maxZ = z0 + layout.sizeZ;
        double bottom = y0 + 0.03D, top = y1 - 1 + 0.03D;
        GlStateManager.pushMatrix();
        GlStateManager.translate(-playerPos.x, -playerPos.y, -playerPos.z);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        float r = 0.25F, g = 0.78F, b = 1.0F;
        edge(buffer, minX, bottom, minZ, maxX, bottom, minZ, r, g, b);
        edge(buffer, maxX, bottom, minZ, maxX, bottom, maxZ, r, g, b);
        edge(buffer, maxX, bottom, maxZ, minX, bottom, maxZ, r, g, b);
        edge(buffer, minX, bottom, maxZ, minX, bottom, minZ, r, g, b);
        edge(buffer, minX, top, minZ, maxX, top, minZ, r, g, b);
        edge(buffer, maxX, top, minZ, maxX, top, maxZ, r, g, b);
        edge(buffer, maxX, top, maxZ, minX, top, maxZ, r, g, b);
        edge(buffer, minX, top, maxZ, minX, top, minZ, r, g, b);
        edge(buffer, minX, y0, minZ, minX, y1, minZ, r, g, b);
        edge(buffer, maxX, y0, minZ, maxX, y1, minZ, r, g, b);
        edge(buffer, maxX, y0, maxZ, maxX, y1, maxZ, r, g, b);
        edge(buffer, minX, y0, maxZ, minX, y1, maxZ, r, g, b);
        tessellator.draw();
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();

        // ---- restore (exactly what Building Gadgets restores, in the same order) ----
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        ForgeHooksClient.setRenderLayer(MinecraftForgeClient.getRenderLayer());
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static final class BlockRefCell {
        final BlockPos pos;
        final BlockRef ref;

        BlockRefCell(BlockPos pos, BlockRef ref) {
            this.pos = pos;
            this.ref = ref;
        }
    }

    private static void edge(BufferBuilder buffer, double x1, double ya, double z1,
                             double x2, double yb, double z2, float r, float g, float b) {
        buffer.pos(x1 - 0, ya, z1).color(r, g, b, 1.0F).endVertex();
        buffer.pos(x2, yb, z2).color(r, g, b, 1.0F).endVertex();
    }
}
