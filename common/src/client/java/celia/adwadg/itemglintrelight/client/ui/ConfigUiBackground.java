package celia.adwadg.itemglintrelight.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class ConfigUiBackground {
    private ConfigUiBackground() {
    }

    public static void renderBackdrop(GuiGraphics graphics, int width, int height) {
    }

    public static void renderPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int sidebarRight) {
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, UiPalette.PALE_BLUE);
        graphics.fill(left, top, right, bottom, UiPalette.DEEP_BLUE);
        graphics.fill(sidebarRight, top + 18, sidebarRight + 1, bottom - 18, UiPalette.DIVIDER);
        graphics.fill(right - 130, top + 63, right - 24, top + 64, UiPalette.DIVIDER);
        graphics.fill(right - 130, bottom - 58, right - 24, bottom - 57, UiPalette.DIVIDER);
    }
}
