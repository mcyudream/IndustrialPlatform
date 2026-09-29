package dev.celestiacraft.industrialplatform.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * GitHub Dark flat button: secondary #1C2531 + #334155 border, primary #2563EB
 * (hover #1D4ED8, active #1E40AF), accent = "toggle ON" state. Disabled is dimmed.
 */
@SideOnly(Side.CLIENT)
public class DarkButton extends GuiButton {

    public boolean primary;
    public boolean accent;

    public DarkButton(int id, int x, int y, int w, int h, String text) {
        super(id, x, y, w, h, text);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

        int fill;
        int border;
        int textColor;
        if (!enabled) {
            fill = 0xFF1F2937;
            border = 0xFF2D3644;
            textColor = 0xFF4A5560;
        } else if (primary) {
            fill = hovered ? 0xFF1D4ED8 : 0xFF2563EB;
            border = hovered ? 0xFF58A6FF : 0xFF1E40AF;
            textColor = 0xFFFFFFFF;
        } else if (accent) {
            fill = hovered ? 0xFF1F3A5F : 0xFF1D2836;
            border = 0xFF2563EB;
            textColor = 0xFF7FB5FF;
        } else {
            fill = hovered ? 0xFF1A2230 : 0xFF1C2531;
            border = hovered ? 0xFF58A6FF : 0xFF334155;
            textColor = hovered ? 0xFFE6EDF3 : 0xFFC9D4E3;
        }

        drawRect(x, y, x + width, y + height, border);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, textColor);
    }
}
