package dev.celestiacraft.industrialplatform.client.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * GitHub Dark style tokens for every builder GUI: canvas #0D1117, panel #151B24,
 * fields #0D141C, one accent #2563EB, semantic success/warning/danger.
 * Panels, insets and focus frames are drawn through these helpers.
 */
@SideOnly(Side.CLIENT)
public final class Theme {

    public static final int CANVAS = 0xFF0D1117;
    public static final int PANEL = 0xFF151B24;
    public static final int PANEL_BORDER = 0xFF263142;
    public static final int FIELD = 0xFF0D141C;
    public static final int FIELD_BORDER = 0xFF2D3644;
    public static final int FIELD_HOVER = 0xFF121A24;

    public static final int ACCENT = 0xFF2563EB;
    public static final int ACCENT_HOVER = 0xFF1D4ED8;
    public static final int ACCENT_ACTIVE = 0xFF1E40AF;
    public static final int FOCUS = 0xFF58A6FF;

    public static final int TEXT_1 = 0xFFE6EDF3;
    public static final int TEXT_2 = 0xFF9DA7B3;
    public static final int TEXT_3 = 0xFF6E7781;
    public static final int TITLE = 0xFF9BE7FF;

    public static final int SUCCESS = 0xFF3FB950;
    public static final int WARNING = 0xFFD29922;
    public static final int DANGER = 0xFFF85149;
    public static final int DIVIDER = 0xFF263142;

    public static final int ROW = 0xFF161D27;
    public static final int ROW_HOVER = 0xFF1A2230;
    public static final int ROW_SELECTED = 0xFF1D2836;
    public static final int SECONDARY = 0xFF1C2531;
    public static final int SECONDARY_BORDER = 0xFF334155;

    // --- backward-compatible aliases used by existing screens ---
    public static final int INSET = 0xFF0D141C;
    public static final int BORDER = PANEL_BORDER;
    public static final int BORDER_LIGHT = 0xFF334155;
    public static final int SHADE = 0xFF0D1117;
    public static final int PRIMARY = ACCENT;
    public static final int PRIMARY_HOVER = ACCENT_HOVER;
    public static final int PRIMARY_BORDER = 0xFF1E40AF;
    public static final int TEXT = TEXT_1;
    public static final int TEXT_SUB = TEXT_2;
    public static final int TEXT_DISABLED = 0xFF4A5560;
    public static final int ACCENT_TEXT = 0xFF7FB5FF;
    public static final int ACCENT_SOFT = 0xFF1D2A3A;
    public static final int OK = SUCCESS;
    public static final int WARN = WARNING;
    public static final int ERR = DANGER;

    private Theme() {
    }

    /** Panel: 1px outer border, fill, top hairline, bottom shade. */
    public static void drawPanel(int px, int py, int w, int h) {
        drawRect(px - 1, py - 1, px + w + 1, py + h + 1, 0xFF000000);
        drawRect(px, py, px + w, py + h, PANEL);
        drawRect(px, py, px + w, py + 1, BORDER_LIGHT);
        drawRect(px, py + h - 1, px + w, py + h, SHADE);
    }

    /** Inset surface for grouped content (lists, grids...). */
    public static void drawInset(int x1, int y1, int x2, int y2) {
        drawRect(x1, y1, x2, y2, FIELD_BORDER);
        drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, INSET);
    }

    private static void drawRect(int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.Gui.drawRect(x1, y1, x2, y2, color);
    }
}
