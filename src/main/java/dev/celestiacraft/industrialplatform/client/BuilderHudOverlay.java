package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.block.PlatformBuilderBlock;
import dev.celestiacraft.industrialplatform.client.gui.Theme;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.MaterialScanner;
import dev.celestiacraft.industrialplatform.platform.PlatformLayout;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Crosshair hover popup: aiming at a platform builder shows a compact dark card
 * summarizing the stored configuration AND the material bill compared against
 * the player's inventory (enough = green, lacking = red with the shortfall).
 */
@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID, value = Side.CLIENT)
public final class BuilderHudOverlay extends Gui {

    private BuilderHudOverlay() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        RayTraceResult trace = mc.objectMouseOver;
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK || trace.getBlockPos() == null) {
            return;
        }
        BlockPos pos = trace.getBlockPos();
        if (!(mc.world.getBlockState(pos).getBlock() instanceof PlatformBuilderBlock)) {
            return;
        }
        TileEntity te = mc.world.getTileEntity(pos);
        if (!(te instanceof TilePlatformBuilder)) {
            return;
        }

        PlatformConfig cfg = ((TilePlatformBuilder) te).getConfig();
        cfg.clamp();
        PlatformLayout layout = new PlatformLayout(cfg);

        List<String> lines = new ArrayList<String>();
        lines.add(TextFormatting.AQUA + I18n.format("ip.hover.title"));
        lines.add(TextFormatting.WHITE + I18n.format("ip.hover.size", layout.sizeX, layout.sizeZ));
        lines.add(TextFormatting.WHITE + I18n.format("ip.hover.layers", cfg.layers,
                Math.max(0, Math.min(255, pos.getY())), Math.min(255, pos.getY() + cfg.layers - 1)));

        // material bill compared against the player's inventory
        Map<BlockRef, Integer> bill = MaterialScanner.required(mc.world, pos, cfg);
        if (!bill.isEmpty()) {
            lines.add(TextFormatting.GRAY + I18n.format("ip.gui.bill"));
            Map<BlockRef, Integer> missing = MaterialScanner.missing(mc.player, bill);
            int shown = 0;
            for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
                if (shown++ == 5) {
                    lines.add(TextFormatting.GRAY + " …");
                    break;
                }
                Integer lack = missing.get(entry.getKey());
                if (lack != null) {
                    lines.add(TextFormatting.RED + " " + entry.getKey().displayName() + " x" + entry.getValue()
                            + " (" + I18n.format("ip.hover.lack") + " " + lack + ")");
                } else {
                    lines.add(TextFormatting.WHITE + " " + entry.getKey().displayName() + " x" + entry.getValue()
                            + TextFormatting.GREEN + " (" + I18n.format("ip.hover.enough") + ")");
                }
            }
        }

        lines.add(TextFormatting.GRAY + I18n.format("ip.hover.preview",
                I18n.format(cfg.previewOn ? "ip.gui.on" : "ip.gui.off")));
        lines.add(TextFormatting.GRAY + I18n.format("ip.hover.open"));

        FontRenderer font = mc.fontRenderer;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        ScaledResolution resolution = event.getResolution();
        int px = resolution.getScaledWidth() / 2 + 14;
        int py = resolution.getScaledHeight() / 2 - 26;
        int pw = width + 12;
        int ph = lines.size() * 10 + 7;

        drawRect(px, py, px + pw, py + ph, 0xE014161D);
        drawRect(px, py, px + pw, py + 1, Theme.ACCENT);
        drawRect(px, py + ph - 1, px + pw, py + ph, Theme.ACCENT);
        drawRect(px, py, px + 1, py + ph, Theme.ACCENT);
        drawRect(px + pw - 1, py, px + pw, py + ph, Theme.ACCENT);

        for (int i = 0; i < lines.size(); i++) {
            // colors are embedded as formatting codes; base color white
            font.drawStringWithShadow(lines.get(i), px + 6, py + 4 + i * 10, 0xFFFFFF);
        }
    }
}
