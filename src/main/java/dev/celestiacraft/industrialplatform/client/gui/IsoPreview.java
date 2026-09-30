package dev.celestiacraft.industrialplatform.client.gui;

import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.PlatformLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Patchouli-style isometric render of the platform surface, shared by the
 * full-size GUI preview and the compact hover-card thumbnail. Uses
 * {@link PlatformLayout#surfaceRefAt} so the picture always includes the
 * center overlay and the corner markers — exactly what gets built.
 */
@SideOnly(Side.CLIENT)
public final class IsoPreview {

    private IsoPreview() {
    }

    /** Base pixel budget used to scale the block field into the given view size. */
    public static void drawSurface(Minecraft mc, PlatformConfig cfg, int centerX, int centerY,
                                   int viewSize, float rotX, float rotY) {
        cfg.clamp();
        PlatformLayout layout = new PlatformLayout(cfg);

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, centerY, 100.0F);
        GlStateManager.rotate(rotX, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(rotY, 0.0F, 1.0F, 0.0F);

        float scale = Math.min(viewSize / 2.0F / Math.max(layout.sizeX, layout.sizeZ), 14.0F);
        GlStateManager.scale(scale, -scale, scale);
        GlStateManager.translate(-layout.centerX - 0.5F, -1.0F, -layout.centerZ - 0.5F);

        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();

        BlockRef torch = cfg.autoTorches
                ? new BlockRef(net.minecraft.init.Blocks.TORCH, 0) : null;
        for (int z = 0; z < layout.sizeZ; z++) {
            for (int x = 0; x < layout.sizeX; x++) {
                BlockRef ref = layout.surfaceRefAt(x, z);
                if (!ref.isAir()) {
                    drawBlock(dispatcher, ref, x, 0, z);
                }
            }
        }
        if (torch != null) {
            for (int[] spot : layout.torchSpots()) {
                drawBlock(dispatcher, torch, spot[0], 1, spot[1]);
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static void drawBlock(BlockRendererDispatcher dispatcher, BlockRef ref, int x, int y, int z) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        dispatcher.renderBlockBrightness(ref.block.getStateFromMeta(ref.meta), 1.0F);
        GlStateManager.popMatrix();
    }
}
