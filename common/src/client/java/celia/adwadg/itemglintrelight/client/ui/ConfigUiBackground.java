package celia.adwadg.itemglintrelight.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class ConfigUiBackground {
    private ConfigUiBackground() {
    }

    public static void renderBackdrop(GuiGraphics graphics, int width, int height) {
    }

    public static void renderPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int sidebarRight) {
        renderAnimatedBorder(graphics, left - 1, top - 1, right + 1, bottom + 1);
        graphics.fill(left, top, right, bottom, UiPalette.DEEP_BLUE);
        graphics.fill(sidebarRight, top + 18, sidebarRight + 1, bottom - 18, UiPalette.DIVIDER);
        graphics.fill(sidebarRight + 12, top + 28, right - 12, top + 29, UiPalette.DIVIDER);
        graphics.fill(sidebarRight + 12, bottom - 50, right - 12, bottom - 49, UiPalette.DIVIDER);
    }

    private static void renderAnimatedBorder(GuiGraphics graphics, int left, int top, int right, int bottom) {
        float phase = (System.nanoTime() % 2_400_000_000L) / 2_400_000_000.0F;
        int width = right - left;
        int height = bottom - top;
        for (int offset = 0; offset < width; offset += 3) {
            int length = Math.min(3, width - offset);
            graphics.fill(left + offset, top, left + offset + length, top + 1, gradient(phase + offset / (float) width));
            graphics.fill(right - offset - length, bottom - 1, right - offset, bottom, gradient(phase + 0.5F + offset / (float) width));
        }
        for (int offset = 1; offset < height - 1; offset += 3) {
            int length = Math.min(3, height - 1 - offset);
            graphics.fill(left, top + offset, left + 1, top + offset + length, gradient(phase + 0.25F + offset / (float) height));
            graphics.fill(right - 1, bottom - offset - length, right, bottom - offset, gradient(phase + 0.75F + offset / (float) height));
        }
    }

    private static int gradient(float position) {
        float wrapped = position - (float) Math.floor(position);
        if (wrapped < 0.5F) {
            return UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, wrapped * 2.0F);
        }
        return UiMath.mix(UiPalette.LIGHT_GREEN, UiPalette.BRIGHT_BLUE, (wrapped - 0.5F) * 2.0F);
    }
}
