package dev.celestiacraft.industrialplatform.client.gui;

import dev.celestiacraft.industrialplatform.client.PreviewRenderer;
import dev.celestiacraft.industrialplatform.client.gui.widget.DarkButton;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.platform.MaterialScanner;
import dev.celestiacraft.industrialplatform.platform.PlatformLayout;
import dev.celestiacraft.industrialplatform.platform.PlatformRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.Map;

/**
 * 3D isometric preview of the platform surface (Patchouli-style, rotating with
 * the mouse) plus the live, world-aware material bill on the right.
 */
@SideOnly(Side.CLIENT)
public class GuiPreview extends GuiScreen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 230;

    private final PlatformConfig cfg;
    private final GuiPlatformConfig parent;
    private final BlockPos anchor;
    private final int baseY;

    private float rotY = 30.0F;
    private float rotX = 55.0F;
    private int draggingButton = -1;
    private int lastMouseX;
    private int lastMouseY;

    public GuiPreview(PlatformConfig cfg, BlockPos anchor, GuiPlatformConfig parent) {
        this.cfg = cfg;
        this.anchor = anchor;
        this.baseY = anchor.getY();
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new DarkButton(40, width / 2 - 40, (height + PANEL_H) / 2 - 20, 80, 16,
                I18n.format("ip.gui.done")));
        PreviewRenderer.cfg = cfg;
        PreviewRenderer.active = true;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            backToParent();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 40) {
            backToParent();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        draggingButton = mouseButton;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingButton = -1;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingButton >= 0) {
            rotY += (mouseX - lastMouseX) * 0.6F;
            rotX -= (mouseY - lastMouseY) * 0.6F;
            rotX = net.minecraft.util.math.MathHelper.clamp(rotX, 20.0F, 80.0F);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    private void backToParent() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        Theme.drawPanel(px, py, PANEL_W, PANEL_H);
        drawRect(px, py + 18, px + PANEL_W, py + 19, Theme.BORDER);

        drawCenteredString(fontRenderer, I18n.format("ip.gui.preview.title"),
                px + PANEL_W / 2, py + 6, Theme.TEXT);

        cfg.clamp();
        PlatformLayout layout = new PlatformLayout(cfg);

        // ---- left: 3D isometric block preview
        int mapX = px + 8;
        int mapW = 150;
        int mapY = py + 26;
        int mapH = PANEL_H - 70;
        drawRect(mapX - 1, mapY - 1, mapX + mapW + 1, mapY + mapH + 1, Theme.BORDER);
        drawRect(mapX, mapY, mapX + mapW, mapY + mapH, Theme.INSET);
        drawIsometric(mapX + mapW / 2, mapY + mapH / 2 + 8, layout);

        // ---- right: world-aware material bill
        int billX = px + 168;
        int billW = PANEL_W - 168 - 10;
        drawRect(billX - 4, py + 24, px + PANEL_W - 8, py + PANEL_H - 24, Theme.INSET);
        drawRect(billX - 4, py + 24, px + PANEL_W - 8, py + 25, Theme.BORDER);

        fontRenderer.drawString(I18n.format("ip.gui.bill"), billX, py + 30, Theme.TEXT_SUB);

        Map<BlockRef, Integer> bill = mc.world != null
                ? MaterialScanner.required(mc.world, anchor, cfg)
                : MaterialScanner.required(cfg);
        int row = 0;
        int maxRows = (PANEL_H - 24 - 24) / 16;
        for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
            if (row >= maxRows) {
                fontRenderer.drawString("…", billX, py + 44 + row * 16, Theme.TEXT_SUB);
                break;
            }
            int ry = py + 44 + row * 16;
            ItemStack stack = entry.getKey().toStack(1);
            if (!stack.isEmpty()) {
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, billX, ry);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            }
            String name = fontRenderer.trimStringToWidth(entry.getKey().displayName(), billW - 46);
            fontRenderer.drawString(name, billX + 18, ry + 5, Theme.TEXT);
            String count = "x" + entry.getValue();
            fontRenderer.drawString(count, billX + billW - fontRenderer.getStringWidth(count), ry + 5, Theme.ACCENT_TEXT);
            row++;
        }
        if (bill.isEmpty()) {
            fontRenderer.drawString(I18n.format("ip.gui.bill_empty"), billX, py + 44, Theme.TEXT_SUB);
        }

        String caption = I18n.format("ip.gui.preview.caption", layout.sizeX, layout.sizeZ,
                cfg.layers, baseY);
        drawCenteredString(fontRenderer, caption, px + PANEL_W / 2, py + PANEL_H - 36, Theme.TEXT_SUB);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Patchouli-style isometric render of the surface layer's blocks. */
    private void drawIsometric(int centerX, int centerY, PlatformLayout layout) {
        IsoPreview.drawSurface(mc, cfg, centerX, centerY, 180, rotX, rotY);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
