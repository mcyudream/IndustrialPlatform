package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.block.PlatformBuilderBlock;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.PlatformLayout;
import dev.celestiacraft.industrialplatform.platform.PlatformRole;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-world hologram, Building Gadgets style: translucent block ghosts rendered
 * without depth writes (visible through terrain) at their exact build positions,
 * plus a wireframe volume. Shows while the config screen is open, while aiming
 * at a builder, or persistently when the builder's "show preview" toggle is on.
 *
 * Ghost blocks use BlockRendererDispatcher.renderBlockWithout — the same
 * "render a translucent block ghost regardless of occlusion" primitive Building
 * Gadgets uses on 1.12.2.
 */
@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID, value = Side.CLIENT)
public final class PreviewRenderer {

    public static boolean active;
    public static BlockPos pos;
    public static PlatformConfig cfg;

    private PreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            return;
        }

        BlockPos target = null;
        PlatformConfig targetCfg = null;

        if (active && pos != null && cfg != null) {
            target = pos;
            targetCfg = cfg;
        } else {
            BlockPos hovered = hoveredBuilder(mc);
            if (hovered != null) {
                TileEntity te = mc.world.getTileEntity(hovered);
                if (te instanceof TilePlatformBuilder) {
                    target = hovered;
                    targetCfg = ((TilePlatformBuilder) te).getConfig();
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

        // RenderWorldLast's modelview is relative to the player's FEET (not the
        // eyes) — using the eye position made the hologram sink ~1.6 blocks.
        Vec3d cam = new Vec3d(
                player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks,
                player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks,
                player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks);

        int anchorX = (pos.getX() >> 4) * 16 + 8 + config.offsetX;
        int anchorZ = (pos.getZ() >> 4) * 16 + 8 + config.offsetZ;
        int x0 = anchorX - layout.centerX;
        int z0 = anchorZ - layout.centerZ;
        int y0 = Math.max(0, Math.min(255, pos.getY() + config.offsetY));
        int y1 = Math.min(255, y0 + config.layers);

        double minX = x0;
        double maxX = x0 + layout.sizeX;
        double minZ = z0;
        double maxZ = z0 + layout.sizeZ;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.translate(-cam.x, -cam.y, -cam.z);
        // Cleanroom's LWJGL3 stack is strict about leaked GL state — normalize
        // everything our render relies on before touching the depth/blend paths
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // ---- ghost blocks (Building Gadgets recipe) ----
        // translucent, no depth writes, full brightness, so ghosts are visible
        // through terrain and never z-fight with real blocks
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        // depth test stays enabled — ghosts show through terrain thanks to blend
        // without depth writes, but still occlude correctly in front
        GlStateManager.enableDepth();

        Map<BlockPos, BlockRef> pending = new LinkedHashMap<BlockPos, BlockRef>();
        if (layout.sizeX * layout.sizeZ <= 4096) {
            for (int z = 0; z < layout.sizeZ; z++) {
                for (int x = 0; x < layout.sizeX; x++) {
                    PlatformRole role = layout.roleAt(config.layers - 1, x, z);
                    BlockRef ref = config.get(role);
                    if (ref.isAir()) {
                        continue;
                    }
                    BlockPos cell = new BlockPos(x0 + x, y1 - 1, z0 + z);
                    if (cell.equals(pos)) {
                        continue; // the builder block itself is never ghosted
                    }
                    pending.put(cell, ref);
                }
            }
        } else {
            // huge platforms: surface pattern quads instead
            GlStateManager.enableDepth();
            GlStateManager.disableCull();
            buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            long time = System.currentTimeMillis();
            float pulse = 0.24F + 0.10F * (float) Math.sin(time * 0.004);
            for (int z = 0; z < layout.sizeZ; z++) {
                for (int x = 0; x < layout.sizeX; x++) {
                    PlatformRole role = layout.roleAt(config.layers - 1, x, z);
                    if (config.get(role).isAir()) {
                        continue;
                    }
                    float[] rgb = roleColor(role);
                    cell(buffer, x0 + x, y1 + 0.03D, z0 + z, rgb[0], rgb[1], rgb[2], pulse);
                }
            }
            tessellator.draw();
            GlStateManager.enableCull();
        }

        if (!pending.isEmpty()) {
            // per-block GL translate + renderBlockBrightness: proven correct
            // positioning. (Batched renderBlock requires buffer.setTranslation
            // per cell — without it every ghost stacks at the buffer origin.)
            GlStateManager.enableCull();
            BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
            float ghostBrightness = 0.72F + 0.12F * (float) Math.sin(System.currentTimeMillis() * 0.004);
            for (Map.Entry<BlockPos, BlockRef> entry : pending.entrySet()) {
                BlockPos cell = entry.getKey();
                GlStateManager.pushMatrix();
                GlStateManager.translate(cell.getX(), cell.getY(), cell.getZ());
                dispatcher.renderBlockBrightness(
                        entry.getValue().block.getStateFromMeta(entry.getValue().meta), ghostBrightness);
                GlStateManager.popMatrix();
            }

            // accent cap so pending cells read as "planned", never as broken blocks
            GlStateManager.disableCull();
            buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            float capAlpha = 0.30F + 0.10F * (float) Math.sin(System.currentTimeMillis() * 0.006);
            for (BlockPos cell : pending.keySet()) {
                cell(buffer, cell.getX(), cell.getY() + 1.002D, cell.getZ(), 0.31F, 0.71F, 1.0F, capAlpha);
            }
            tessellator.draw();
            GlStateManager.enableCull();
        }

        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();

        // ---- wireframe volume, visible through terrain ----
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.disableDepth();
        GlStateManager.glLineWidth(2.0F);
        double bottom = y0 + 0.03D;
        double top = y1 - 1 + 0.03D;
        buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);
        float r = 0.25F, g = 0.78F, b = 1.0F, a = 0.55F;
        edge(buffer, minX, bottom, minZ, maxX, bottom, minZ, r, g, b, a);
        edge(buffer, maxX, bottom, minZ, maxX, bottom, maxZ, r, g, b, a);
        edge(buffer, maxX, bottom, maxZ, minX, bottom, maxZ, r, g, b, a);
        edge(buffer, minX, bottom, maxZ, minX, bottom, minZ, r, g, b, a);
        edge(buffer, minX, top, minZ, maxX, top, minZ, r, g, b, 0.95F);
        edge(buffer, maxX, top, minZ, maxX, top, maxZ, r, g, b, 0.95F);
        edge(buffer, maxX, top, maxZ, minX, top, maxZ, r, g, b, 0.95F);
        edge(buffer, minX, top, maxZ, minX, top, minZ, r, g, b, 0.95F);
        edge(buffer, minX, y0, minZ, minX, y1, minZ, r, g, b, a);
        edge(buffer, maxX, y0, minZ, maxX, y1, minZ, r, g, b, a);
        edge(buffer, maxX, y0, maxZ, maxX, y1, maxZ, r, g, b, a);
        edge(buffer, minX, y0, maxZ, minX, y1, maxZ, r, g, b, a);
        tessellator.draw();

        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    private static float[] roleColor(PlatformRole role) {
        switch (role) {
            case BORDER: return new float[]{0.78F, 0.81F, 0.86F};
            case FILL: return new float[]{0.38F, 0.52F, 0.72F};
            case CENTER: return new float[]{0.90F, 0.93F, 0.97F};
            case BOUNDARY: return new float[]{1.0F, 0.83F, 0.28F};
            case LINK: return new float[]{0.35F, 0.85F, 0.65F};
            case BODY: return new float[]{0.33F, 0.38F, 0.46F};
            case EDGE: return new float[]{0.55F, 0.62F, 0.72F};
            case CHANNEL_LINE: return new float[]{1.0F, 0.70F, 0.22F};
            case CENTER_MARK: return new float[]{1.0F, 0.86F, 0.25F};
            default: return new float[]{0.6F, 0.6F, 0.6F};
        }
    }

    private static void cell(BufferBuilder buffer, double x, double y, double z,
                             float r, float g, float b, float a) {
        buffer.pos(x, y, z).color(r, g, b, a).endVertex();
        buffer.pos(x + 1, y, z).color(r, g, b, a).endVertex();
        buffer.pos(x + 1, y, z + 1).color(r, g, b, a).endVertex();
        buffer.pos(x, y, z + 1).color(r, g, b, a).endVertex();
    }

    private static void edge(BufferBuilder buffer, double x1, double ya, double z1,
                             double x2, double yb, double z2, float r, float g, float b, float a) {
        buffer.pos(x1, ya, z1).color(r, g, b, a).endVertex();
        buffer.pos(x2, yb, z2).color(r, g, b, a).endVertex();
    }
}
