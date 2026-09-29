package dev.celestiacraft.industrialplatform.client.gui;

import dev.celestiacraft.industrialplatform.IndustrialPlatform;
import dev.celestiacraft.industrialplatform.blueprint.Blueprint;
import dev.celestiacraft.industrialplatform.blueprint.BlueprintLibrary;
import dev.celestiacraft.industrialplatform.client.PreviewRenderer;
import dev.celestiacraft.industrialplatform.client.gui.widget.DarkButton;
import dev.celestiacraft.industrialplatform.config.BlockRef;
import dev.celestiacraft.industrialplatform.config.PlatformConfig;
import dev.celestiacraft.industrialplatform.network.PacketConfig;
import dev.celestiacraft.industrialplatform.platform.MaterialScanner;
import dev.celestiacraft.industrialplatform.platform.PlatformRole;
import dev.celestiacraft.industrialplatform.tile.TilePlatformBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Builder configuration screen, dark theme, deliberately minimal:
 * pick four materials (border / fill / road / center), set the cell size,
 * grid counts and link width — one builder builds the whole thing.
 */
@SideOnly(Side.CLIENT)
public class GuiPlatformConfig extends GuiScreen {

    private static final int PANEL_W = 344;
    private static final int PANEL_H = 212;

    private static final int COL_A = 146;
    private static final int COL_B = 242;
    private static final int COL_W = 92;
    private static final int FIELD_W = 52;

    /** The only roles the simplified GUI exposes, in display order. */
    private static final PlatformRole[] GUI_ROLES = {
            PlatformRole.BORDER, PlatformRole.FILL, PlatformRole.LINK, PlatformRole.CENTER
    };

    private static final int[] FIELD_MIN = {0, 1, 1, 0, -64, -64, -64};
    private static final int[] FIELD_MAX = {126, 8, 8, 31, 64, 64, 64};

    private final BlockPos pos;
    private PlatformConfig cfg;

    private final GuiTextField[] fields = new GuiTextField[7];
    private GuiButton btnPreviewToggle, btnReplaceExisting, btnBlueprint, btnRefresh, btnPreview, btnBuild, btnDone;
    private final GuiButton[] roleButtons = new GuiButton[GUI_ROLES.length];

    private int blueprintIndex = -1;
    private String statusText = "";
    private int statusColor = Theme.OK;

    public GuiPlatformConfig(BlockPos pos) {
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPlatformConfig(pos));
    }

    // ------------------------------------------------------------------ setup

    @Override
    public void initGui() {
        if (cfg == null) {
            TileEntity tile = mc.world.getTileEntity(pos);
            PlatformConfig stored = tile instanceof TilePlatformBuilder
                    ? ((TilePlatformBuilder) tile).getConfig() : null;
            cfg = stored != null ? stored.copy() : PlatformConfig.defaults();
            cfg.clamp();
        }

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;
        buttonList.clear();

        for (int i = 0; i < GUI_ROLES.length; i++) {
            roleButtons[i] = new IconRowButton(100 + i, px + 8, py + 22 + i * 20, 132, 18, GUI_ROLES[i]);
            buttonList.add(roleButtons[i]);
        }

        // numeric fields with inline steppers:
        // 0 cell size | 1 countX | 2 countZ | 3 linkWidth | 4 offsetX | 5 offsetZ | 6 offsetY
        addField(px + COL_A, py + 34, 0);
        addField(px + COL_B, py + 34, 1);
        addField(px + COL_A, py + 66, 2);
        addField(px + COL_B, py + 66, 3);
        // offsets grouped in an inset panel on the bottom-left, labels inline
        addField(px + 40, py + 122, 4);
        addField(px + 40, py + 144, 5);
        addField(px + 40, py + 166, 6);

        btnPreviewToggle = addBtn(29, px + COL_A, py + 94, COL_W, 16);
        btnReplaceExisting = addBtn(30, px + COL_B, py + 94, COL_W, 16);
        btnBlueprint = addBtn(24, px + COL_A, py + 114, COL_W, 16);
        btnRefresh = addBtn(25, px + COL_B, py + 114, COL_W, 16);
        btnPreview = addBtn(28, px + COL_A, py + 134, COL_W, 16);
        btnDone = addBtn(27, px + COL_B, py + 134, COL_W, 16);
        btnBuild = addBtn(26, px + COL_A, py + 154, COL_B + COL_W - COL_A, 18);
        ((DarkButton) btnBuild).primary = true;
        ((DarkButton) btnBuild).primary = true;

        refreshWidgets();
        syncFieldsToConfig();

        PreviewRenderer.pos = pos;
        PreviewRenderer.cfg = cfg;
        PreviewRenderer.active = true;
    }

    /** Field (52px) with a -/+ stepper pair right beside it; ids 2i / 2i+1. */
    private void addField(int x, int y, int index) {
        GuiTextField field = new GuiTextField(0, this.fontRenderer, x, y, FIELD_W, 14);
        field.setMaxStringLength(7);
        fields[index] = field;
        buttonList.add(new DarkButton(index * 2, x + FIELD_W + 2, y + 1, 18, 12, "-"));
        buttonList.add(new DarkButton(index * 2 + 1, x + FIELD_W + 22, y + 1, 18, 12, "+"));
    }

    private GuiButton addBtn(int id, int x, int y, int w, int h) {
        DarkButton button = new DarkButton(id, x, y, w, h, "");
        buttonList.add(button);
        return button;
    }

    private void refreshWidgets() {
        for (int i = 0; i < fields.length; i++) {
            fields[i].setText(String.valueOf(fieldValue(i)));
        }
        btnPreviewToggle.displayString = toggleLabel(I18n.format("ip.gui.preview_toggle"), cfg.previewOn);
        ((DarkButton) btnPreviewToggle).accent = cfg.previewOn;
        btnReplaceExisting.displayString = toggleLabel(I18n.format("ip.gui.replace"), cfg.replaceExisting);
        ((DarkButton) btnReplaceExisting).accent = cfg.replaceExisting;
        btnBlueprint.displayString = blueprintLabel();
        btnRefresh.displayString = I18n.format("ip.gui.refresh");
        btnPreview.displayString = I18n.format("ip.gui.preview");
        btnBuild.displayString = I18n.format("ip.gui.build");
        btnDone.displayString = I18n.format("ip.gui.done");
        for (GuiButton roleButton : roleButtons) {
            ((IconRowButton) roleButton).refreshLabel();
        }
    }

    private static String toggleLabel(String base, boolean on) {
        return base + ": " + I18n.format(on ? "ip.gui.on" : "ip.gui.off");
    }

    private String blueprintLabel() {
        String name = cfg.blueprintName == null || cfg.blueprintName.isEmpty()
                ? I18n.format("ip.gui.blueprint_custom") : cfg.blueprintName;
        if (name.length() > 8) {
            name = name.substring(0, 8) + "…";
        }
        return I18n.format("ip.gui.blueprint", name);
    }

    // ------------------------------------------------------------------ fields <-> config

    /** 0 = cell size (both intervals), 1 = countX, 2 = countZ, 3 = linkWidth,
     *  4/5 = horizontal offsets, 6 = vertical offset. */
    private int fieldValue(int index) {
        switch (index) {
            case 0: return cfg.intervalX;
            case 1: return cfg.countX;
            case 2: return cfg.countZ;
            case 3: return cfg.linkWidth;
            case 4: return cfg.offsetX;
            case 5: return cfg.offsetZ;
            default: return cfg.offsetY;
        }
    }

    private void setFieldValue(int index, int value) {
        value = MathHelper.clamp(value, FIELD_MIN[index], FIELD_MAX[index]);
        switch (index) {
            case 0:
                cfg.intervalX = value;
                cfg.intervalZ = value;
                break;
            case 1: cfg.countX = value; break;
            case 2: cfg.countZ = value; break;
            case 3: cfg.linkWidth = value; break;
            case 4: cfg.offsetX = value; break;
            case 5: cfg.offsetZ = value; break;
            default: cfg.offsetY = value; break;
        }
    }

    private void syncFieldsToConfig() {
        for (int i = 0; i < fields.length; i++) {
            try {
                setFieldValue(i, Integer.parseInt(fields[i].getText().trim()));
            } catch (NumberFormatException ignored) {
                // keep the previous value while the text is not a valid number
            }
        }
    }

    @Override
    public void updateScreen() {
        syncFieldsToConfig();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        boolean capture = false;
        for (GuiTextField field : fields) {
            if (field.isFocused() && field.textboxKeyTyped(typedChar, keyCode)) {
                capture = true;
            }
        }
        if (capture) {
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            tryBuild();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        boolean hitField = false;
        for (GuiTextField field : fields) {
            field.mouseClicked(mouseX, mouseY, mouseButton);
            if (field.isFocused()) {
                hitField = true;
            }
        }
        if (!hitField) {
            for (GuiTextField field : fields) {
                field.setFocused(false);
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        int id = button.id;
        if (id >= 0 && id <= 13) {
            int field = id / 2;
            int delta = (id % 2) == 1 ? 1 : -1;
            setFieldValue(field, fieldValue(field) + delta);
            fields[field].setText(String.valueOf(fieldValue(field)));
            return;
        }
        switch (id) {
            case 29:
                cfg.previewOn = !cfg.previewOn;
                btnPreviewToggle.displayString = toggleLabel(I18n.format("ip.gui.preview_toggle"), cfg.previewOn);
                ((DarkButton) btnPreviewToggle).accent = cfg.previewOn;
                break;
            case 30:
                cfg.replaceExisting = !cfg.replaceExisting;
                btnReplaceExisting.displayString = toggleLabel(I18n.format("ip.gui.replace"), cfg.replaceExisting);
                ((DarkButton) btnReplaceExisting).accent = cfg.replaceExisting;
                break;
            case 24:
                cycleBlueprint();
                break;
            case 25:
                blueprintIndex = -1;
                int count = BlueprintLibrary.load(Minecraft.getMinecraft().gameDir);
                setStatus(I18n.format("ip.cmd.blueprints_loaded", count), Theme.OK);
                refreshWidgets();
                break;
            case 26:
                tryBuild();
                break;
            case 27:
                close();
                break;
            case 28:
                syncFieldsToConfig();
                cfg.clamp();
                mc.displayGuiScreen(new GuiPreview(cfg, pos, this));
                break;
            default:
                if (id >= 100 && id < 100 + GUI_ROLES.length) {
                    PlatformRole role = GUI_ROLES[id - 100];
                    GuiBlockPicker.open(role, cfg.get(role), this);
                }
                break;
        }
    }

    private void cycleBlueprint() {
        List<Blueprint> all = BlueprintLibrary.all();
        if (all.isEmpty()) {
            setStatus(I18n.format("ip.gui.no_blueprint"), Theme.WARN);
            return;
        }
        blueprintIndex++;
        if (blueprintIndex >= all.size()) {
            blueprintIndex = -1;
        }
        if (blueprintIndex >= 0) {
            Blueprint blueprint = all.get(blueprintIndex);
            blueprint.applyTo(cfg);
            setStatus(I18n.format("ip.gui.blueprint_applied", blueprint.name), Theme.OK);
        } else {
            cfg.blueprintName = "";
        }
        refreshWidgets();
        syncFieldsToConfig();
    }

    /** Called back by {@link GuiBlockPicker} after a block was selected. */
    void onRolePicked(PlatformRole role, BlockRef ref) {
        cfg.set(role, ref);
        setStatus(I18n.format("ip.gui.role_set", I18n.format(role.langKey()), ref.displayName()), Theme.OK);
    }

    private void tryBuild() {
        syncFieldsToConfig();
        cfg.clamp();
        if (!hasBuilder()) {
            setStatus(I18n.format("ip.gui.no_builder"), Theme.ERR);
            return;
        }
        Map<BlockRef, Integer> bill = MaterialScanner.required(mc.world, pos, cfg);
        Map<BlockRef, Integer> missing = MaterialScanner.missing(mc.player, bill);
        if (!missing.isEmpty()) {
            setStatus(I18n.format("ip.msg.missing_list", MaterialScanner.formatMissing(missing)), Theme.ERR);
            return;
        }
        IndustrialPlatform.NETWORK.sendToServer(new PacketConfig(pos, cfg, true));
        setStatus(I18n.format("ip.msg.build_started"), Theme.OK);
    }

    private void close() {
        mc.displayGuiScreen(null);
    }

    private boolean hasBuilder() {
        return mc.world != null && mc.world.getTileEntity(pos) instanceof TilePlatformBuilder;
    }

    private void setStatus(String text, int color) {
        this.statusText = text == null ? "" : text;
        this.statusColor = color;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        PreviewRenderer.active = false;
        if (cfg != null && hasBuilder()) {
            syncFieldsToConfig();
            IndustrialPlatform.NETWORK.sendToServer(new PacketConfig(pos, cfg, false));
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int px = (width - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        Theme.drawPanel(px, py, PANEL_W, PANEL_H);

        // header: title + accent underline
        drawCenteredString(fontRenderer, I18n.format("ip.gui.title"), px + PANEL_W / 2, py + 6, Theme.TEXT);
        drawRect(px + PANEL_W / 2 - 24, py + 16, px + PANEL_W / 2 + 24, py + 17, Theme.ACCENT);

        // inset surface grouping the material picks
        Theme.drawInset(px + 5, py + 20, px + 142, py + 104);

        // inset surface grouping the offsets (bottom-left), label above the fields
        Theme.drawInset(px + 5, py + 104, px + 142, py + 190);
        fontRenderer.drawString(I18n.format("ip.gui.offset_group"), px + 12, py + 110, Theme.ACCENT_TEXT);

        // section labels
        fontRenderer.drawString(I18n.format("ip.gui.size"), px + COL_A, py + 24, Theme.TEXT_SUB);
        fontRenderer.drawString(I18n.format("ip.gui.count_x"), px + COL_B, py + 24, Theme.TEXT_SUB);
        fontRenderer.drawString(I18n.format("ip.gui.count_z"), px + COL_A, py + 56, Theme.TEXT_SUB);
        fontRenderer.drawString(I18n.format("ip.gui.link"), px + COL_B, py + 56, Theme.TEXT_SUB);
        // offset labels sit left of each field (X / Y / Z)
        fontRenderer.drawString("X", px + 28, py + 125, Theme.TEXT_SUB);
        fontRenderer.drawString("Y", px + 28, py + 169, Theme.TEXT_SUB);
        fontRenderer.drawString("Z", px + 28, py + 147, Theme.TEXT_SUB);

        super.drawScreen(mouseX, mouseY, partialTicks);

        for (GuiTextField field : fields) {
            field.drawTextBox();
            if (field.isFocused()) {
                frame(field.x - 1, field.y - 1, field.x + field.width + 1, field.y + field.height + 1, Theme.ACCENT);
            }
        }

        // footer separator + status line (never overlapped by buttons)
        drawRect(px + 1, py + PANEL_H - 16, px + PANEL_W - 1, py + PANEL_H - 15, Theme.BORDER);
        if (!statusText.isEmpty()) {
            fontRenderer.drawString(statusText, px + 8, py + PANEL_H - 12, statusColor);
        }

        boolean canBuild = hasBuilder() && MaterialScanner.missing(mc.player,
                MaterialScanner.required(mc.world, pos, cfg)).isEmpty();
        btnBuild.enabled = canBuild;
        boolean buildHovered = mouseX >= px + COL_A && mouseY >= py + 154
                && mouseX < px + COL_B + COL_W && mouseY < py + 154 + 18;
        if (buildHovered) {
            drawHoveringText(buildTooltip(), mouseX, mouseY);
        }
    }

    private static void frame(int x1, int y1, int x2, int y2, int color) {
        drawRect(x1, y1, x2, y1 + 1, color);
        drawRect(x1, y2 - 1, x2, y2, color);
        drawRect(x1, y1, x1 + 1, y2, color);
        drawRect(x2 - 1, y1, x2, y2, color);
    }

    /** Material bill for the current configuration against the real world. */
    private List<String> buildTooltip() {
        List<String> lines = new java.util.ArrayList<String>();
        if (!hasBuilder()) {
            lines.add(I18n.format("ip.gui.no_builder"));
            return lines;
        }
        Map<BlockRef, Integer> bill = MaterialScanner.required(mc.world, pos, cfg);
        lines.add(I18n.format("ip.gui.need"));
        int shown = 0;
        for (Map.Entry<BlockRef, Integer> entry : bill.entrySet()) {
            if (shown++ == 8) {
                lines.add("…");
                break;
            }
            lines.add(" " + entry.getKey().displayName() + " x" + entry.getValue());
        }
        if (bill.isEmpty()) {
            lines.add(" " + I18n.format("ip.gui.bill_empty"));
        }
        Map<BlockRef, Integer> missing = MaterialScanner.missing(mc.player, bill);
        if (!missing.isEmpty()) {
            lines.add(I18n.format("ip.gui.missing") + ": " + MaterialScanner.formatMissing(missing));
        }
        return lines;
    }

    void renderItemIcon(ItemStack stack, int x, int y) {
        RenderHelperHolder.enable();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelperHolder.disable();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------ widgets

    private class IconRowButton extends GuiButton {

        private final PlatformRole role;

        IconRowButton(int id, int x, int y, int w, int h, PlatformRole role) {
            super(id, x, y, w, h, I18n.format(role.langKey()));
            this.role = role;
        }

        void refreshLabel() {
            displayString = I18n.format(role.langKey());
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) {
                return;
            }
            hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            int border = hovered ? Theme.ACCENT : Theme.BORDER;
            int fill = hovered ? Theme.ROW_HOVER : Theme.ROW;
            drawRect(x, y, x + width, y + height, border);
            drawRect(x + 1, y + 1, x + width - 1, y + height - 1, fill);

            ItemStack icon = cfg.get(role).toStack(1);
            if (!icon.isEmpty()) {
                renderItemIcon(icon, x + 2, y + 1);
            }
            fontRenderer.drawString(displayString, x + 21, y + (height - 8) / 2,
                    hovered ? Theme.TEXT : Theme.TEXT_SUB);
        }
    }

    /** Small indirection so widget code stays readable. */
    private static final class RenderHelperHolder {
        static void enable() {
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableAlpha();
        }

        static void disable() {
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        }
    }
}
