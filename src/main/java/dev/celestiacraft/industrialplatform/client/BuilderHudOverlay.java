package dev.celestiacraft.industrialplatform.client;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.block.PlatformBuilderBlock;
import dev.celestiacraft.industrialplatform.client.gui.Theme;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.MaterialScanner;
import dev.celestiacraft.industrialplatform.platform.PlatformLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
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
 *
 * Registered via {@code @Mod.EventBusSubscriber} (see {@link ClientModels} for
 * why explicit registration is not usable on the Cleanroom core); the builder
 * config resolves through {@link ClientConfigCache} so a flaky client-side tile
 * entity can't kill the card.
 */
@Mod.EventBusSubscriber(modid = IndustrialPlatform.MODID, value = Side.CLIENT)
public final class BuilderHudOverlay extends Gui {

    private static boolean firedLog;

    public BuilderHudOverlay() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (!firedLog) {
            firedLog = true;
            IndustrialPlatform.LOGGER.info("[IP] HUD overlay handler alive");
        }
        Minecraft mc = Minecraft.getMinecraft();
        ModelHealer.ensureHealed(mc);
        if (mc.world == null || mc.player == null || mc.currentScreen != null) {
            return;
        }
        RayTraceResult trace = mc.objectMouseOver;
        if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK || trace.getBlockPos() == null) {
            return;
        }
        BlockPos pos = trace.getBlockPos();
        if (!(mc.world.getBlockState(pos).getBlock() instanceof PlatformBuilderBlock)) {
            logForeignHoverOnce(mc, pos);
            return;
        }
        PlatformConfig cfg = ClientConfigCache.resolve(mc.world, pos);
        if (cfg == null) {
            // no TE, no cache: ask the authoritative server once
            ClientSyncer.maybeRequest(pos);
            return;
        }
        cfg.clamp();
        PlatformLayout layout = new PlatformLayout(cfg);

        List<String> lines = new ArrayList<String>();
        lines.add(TextFormatting.AQUA + I18n.format("ip.hover.title"));
        lines.add(TextFormatting.WHITE + I18n.format("ip.hover.size", layout.sizeX, layout.sizeZ));
        lines.add(TextFormatting.WHITE + I18n.format("ip.hover.layers", cfg.layers,
                Math.max(0, Math.min(255, pos.getY())), Math.min(255, pos.getY() + cfg.layers - 1)));

        // material bill: the server's combined-source verdict (inventory +
        // containers + AE) whenever a fresh answer exists; inventory-only
        // guess, clearly labeled, until the reply lands
        Map<BlockRef, Integer> bill = MaterialScanner.required(mc.world, pos, cfg);
        if (!bill.isEmpty()) {
            lines.add(TextFormatting.GRAY + I18n.format("ip.gui.bill"));
            Map<BlockRef, Integer> missing = MaterialStatusCache.fresh(pos);
            boolean authoritative = missing != null;
            if (!authoritative) {
                ClientSyncer.requestMaterialStatus(pos);
                missing = MaterialScanner.missing(mc.player, bill);
            }
            int shown = 0;
            for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
                if (shown++ == 5) {
                    lines.add(TextFormatting.GRAY + " …");
                    break;
                }
                Integer lack = missing.get(entry.getKey());
                if (lack != null) {
                    lines.add(TextFormatting.RED + " " + entry.getKey().displayName() + " x" + entry.getValue()
                            + " (" + I18n.format(authoritative ? "ip.hover.lack" : "ip.hover.lack_inv") + " " + lack + ")");
                } else {
                    lines.add(TextFormatting.WHITE + " " + entry.getKey().displayName() + " x" + entry.getValue()
                            + TextFormatting.GREEN + " (" + I18n.format("ip.hover.enough") + ")");
                }
            }
            if (authoritative && missing.isEmpty()) {
                lines.add(TextFormatting.GREEN + " " + I18n.format("ip.hover.covered"));
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

        // compact isometric thumbnail floating to the right of the text — no
        // frame, no background, and the card never runs off the screen edge
        final int thumb = 58;
        int pw = width + 12 + thumb + 4;
        int ph = Math.max(lines.size() * 10 + 7, thumb);
        ScaledResolution resolution = event.getResolution();
        int px = Math.min(resolution.getScaledWidth() / 2 + 14,
                resolution.getScaledWidth() - pw - 4);
        int py = Math.min(resolution.getScaledHeight() / 2 - 26,
                resolution.getScaledHeight() - ph - 4);

        drawRect(px, py, px + pw, py + ph, 0xE014161D);
        drawRect(px, py, px + pw, py + 1, Theme.ACCENT);
        drawRect(px, py + ph - 1, px + pw, py + ph, Theme.ACCENT);
        drawRect(px, py, px + 1, py + ph, Theme.ACCENT);
        drawRect(px + pw - 1, py, px + pw, py + ph, Theme.ACCENT);

        for (int i = 0; i < lines.size(); i++) {
            // colors are embedded as formatting codes; base color white
            font.drawStringWithShadow(lines.get(i), px + 6, py + 4 + i * 10, 0xFFFFFF);
        }

        try {
            dev.celestiacraft.industrialplatform.client.gui.IsoPreview.drawSurface(
                    mc, cfg, px + pw - thumb / 2 - 2, py + ph / 2 + 8, thumb * 2, 35.0F, 45.0F);
        } catch (Throwable t) {
            IndustrialPlatform.LOGGER.warn("[IP] hover thumbnail render failed: {}", t.toString());
        }
    }

    /** Diagnostic: when a block that LOOKS like ours fails the instanceof check,
     *  log its real identity once — catches registry remapping instantly. */
    private static void logForeignHoverOnce(Minecraft mc, BlockPos pos) {
        if (loggedForeign.contains(pos)) {
            return;
        }
        if (loggedForeign.size() > 32) {
            return;
        }
        String name = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS
                .getKey(mc.world.getBlockState(pos).getBlock()).toString();
        if (name.contains("industrial_platform") || name.contains("platform")) {
            loggedForeign.add(pos);
            IndustrialPlatform.LOGGER.warn(
                    "[IP] hover-check failed at {}: registry says '{}' but class is {} (remapped?)",
                    pos, name, mc.world.getBlockState(pos).getBlock().getClass().getName());
        }
    }

    private static final java.util.Set<BlockPos> loggedForeign = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<BlockPos, Boolean>());
}
