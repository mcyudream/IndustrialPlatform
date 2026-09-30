package dev.celestiacraft.industrialplatform.client.gui;

import dev.celestiacraft.industrialplatform.client.gui.widget.DarkButton;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.platform.PlatformRole;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Block picker for a role — inventory blocks only: one-click pick straight from
 * what the player is carrying (real metadata included), searchable.
 */
@SideOnly(Side.CLIENT)
public class GuiBlockPicker extends GuiScreen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 226;
    private static final int ROW_H = 18;

    private final PlatformRole role;
    private final GuiPlatformConfig parent;
    private int meta;

    private List<BlockRef> filtered = new ArrayList<BlockRef>();
    private GuiTextField search;
    private int scroll;

    public GuiBlockPicker(PlatformRole role, BlockRef current, GuiPlatformConfig parent) {
        this.role = role;
        this.parent = parent;
        this.meta = current == null ? 0 : current.meta;
    }

    public static void open(PlatformRole role, BlockRef current, GuiPlatformConfig parent) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiBlockPicker(role, current, parent));
    }

    @Override
    public void initGui() {
        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        buttonList.clear();

        search = new GuiTextField(0, this.fontRenderer, px + 8, py + 20, 160, 14);
        search.setMaxStringLength(64);
        search.setFocused(true);

        buttonList.add(new DarkButton(32, px + 8, py + PANEL_H - 22, 20, 16, "<"));
        buttonList.add(new DarkButton(33, px + 32, py + PANEL_H - 22, 20, 16, ">"));
        buttonList.add(new DarkButton(34, px + PANEL_W - 88, py + PANEL_H - 22, 80, 16, I18n.format("ip.gui.picker.air")));

        applyMode();
    }

    /** Player inventory blocks, hotbar first, duplicates collapsed. */
    private List<BlockRef> inventoryRefs() {
        Map<BlockRef, Boolean> unique = new LinkedHashMap<BlockRef, Boolean>();
        EntityPlayer player = mc.player;
        if (player != null) {
            for (ItemStack stack : player.inventory.mainInventory) {
                if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) {
                    continue;
                }
                ItemBlock itemBlock = (ItemBlock) stack.getItem();
                BlockRef ref = new BlockRef(itemBlock.getBlock(), stack.getItemDamage());
                unique.put(ref, Boolean.TRUE);
            }
        }
        return new ArrayList<BlockRef>(unique.keySet());
    }

    private void applyMode() {
        applyFilter();
    }

    private void applyFilter() {
        String needle = search.getText().toLowerCase().trim();
        filtered.clear();
        for (BlockRef ref : inventoryRefs()) {
            String name = registryName(ref);
            if (needle.isEmpty() || name.contains(needle)
                    || displaySafe(ref).toLowerCase().contains(needle)) {
                filtered.add(ref);
            }
        }
        clampScroll();
    }

    private static String registryName(BlockRef ref) {
        ResourceLocation name = ref.block == null ? null : Block.REGISTRY.getNameForObject(ref.block);
        return name == null ? "" : name.toString();
    }

    private String displaySafe(BlockRef ref) {
        try {
            return ref.displayName();
        } catch (Exception e) {
            return registryName(ref);
        }
    }

    private int listY(int py) {
        return py + 40;
    }

    private int listBottom(int py) {
        return py + PANEL_H - 28;
    }

    private int visibleRows(int py) {
        return Math.max(0, (listBottom(py) - listY(py)) / ROW_H);
    }

    private void clampScroll() {
        int py = (height - PANEL_H) / 2;
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, filtered.size() - visibleRows(py)));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel > 0 ? -1 : 1;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC
            backToParent();
            return;
        }
        if (search.textboxKeyTyped(typedChar, keyCode)) {
            applyFilter();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        search.mouseClicked(mouseX, mouseY, mouseButton);

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;
        int lx = px + 8;
        int ly = listY(py);

        if (mouseX >= lx && mouseX < px + PANEL_W - 16 && mouseY >= ly && mouseY < listBottom(py)) {
            int row = (mouseY - ly) / ROW_H;
            int index = scroll + row;
            if (row >= 0 && index < filtered.size()) {
                pick(filtered.get(index));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 32:
                scroll -= visibleRows((height - PANEL_H) / 2);
                clampScroll();
                break;
            case 33:
                scroll += visibleRows((height - PANEL_H) / 2);
                clampScroll();
                break;
            case 34:
                parent.onRolePicked(role, BlockRef.AIR);
                backToParent();
                break;
            default:
                break;
        }
    }

    private void pick(BlockRef ref) {
        parent.onRolePicked(role, ref);
        backToParent();
    }

    private void backToParent() {
        mc.displayGuiScreen(parent);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        Theme.drawPanel(px, py, PANEL_W, PANEL_H);
        drawRect(px, py + 18, px + PANEL_W, py + 19, Theme.BORDER);

        drawCenteredString(fontRenderer,
                I18n.format("ip.gui.picker.title", I18n.format(role.langKey())),
                px + PANEL_W / 2, py + 6, Theme.TEXT);

        search.drawTextBox();
        if (search.isFocused()) {
            frame(search.x - 1, search.y - 1, search.x + search.width + 1, search.y + search.height + 1, Theme.ACCENT);
        }

        int lx = px + 8;
        int ly = listY(py);
        int bottom = listBottom(py);

        Theme.drawInset(lx, ly, px + PANEL_W - 8, bottom);

        int index = scroll;
        int rowY = ly + 1;
        while (index < filtered.size() && rowY + ROW_H <= bottom - 1) {
            BlockRef ref = filtered.get(index);
            boolean hovered = mouseX >= lx && mouseX < px + PANEL_W - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) {
                drawRect(lx + 1, rowY, px + PANEL_W - 9, rowY + ROW_H, Theme.ROW_HOVER);
                drawRect(lx + 1, rowY, lx + 3, rowY + ROW_H, Theme.ACCENT);
            }
            ItemStack stack = ref.toStack(1);
            if (!stack.isEmpty()) {
                RenderHelperHolder.enable();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, lx + 6, rowY + 1);
                RenderHelperHolder.disable();
            }
            fontRenderer.drawString(displaySafe(ref), lx + 25, rowY + 2, Theme.TEXT);
            fontRenderer.drawString(registryName(ref), lx + 25, rowY + 10, Theme.TEXT_SUB);
            index++;
            rowY += ROW_H;
        }

        // scrollbar
        if (!filtered.isEmpty()) {
            int trackX = px + PANEL_W - 12;
            int trackH = bottom - ly;
            int pages = Math.max(1, (filtered.size() + visibleRows(py) - 1) / visibleRows(py));
            int page = scroll / Math.max(1, visibleRows(py));
            int thumbH = Math.max(8, trackH / pages);
            int thumbY = ly + (trackH - thumbH) * Math.min(page, pages - 1) / Math.max(1, pages - 1);
            drawRect(trackX, ly + 1, trackX + 3, ly + trackH - 1, Theme.BORDER);
            drawRect(trackX, thumbY, trackX + 3, thumbY + thumbH, Theme.ACCENT);
        }

        String pageLabel = (filtered.isEmpty() ? 0 : scroll / Math.max(1, visibleRows(py)) + 1)
                + " / " + ((filtered.size() + Math.max(1, visibleRows(py)) - 1) / Math.max(1, visibleRows(py)));
        drawCenteredString(fontRenderer, pageLabel, px + PANEL_W / 2 - 60, py + PANEL_H - 18, Theme.TEXT_SUB);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static void frame(int x1, int y1, int x2, int y2, int color) {
        drawRect(x1, y1, x2, y1 + 1, color);
        drawRect(x1, y2 - 1, x2, y2, color);
        drawRect(x1, y1, x1 + 1, y2, color);
        drawRect(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static final class RenderHelperHolder {
        static void enable() {
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        }

        static void disable() {
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }
    }
}
