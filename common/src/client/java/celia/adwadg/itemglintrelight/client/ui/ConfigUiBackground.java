package celia.adwadg.itemglintrelight.client.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class ConfigUiBackground {
    private ConfigUiBackground() {
    }

    public static void renderBackdrop(GuiGraphics graphics, int width, int height) {
    }

    public static void renderPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int sidebarRight) {
        renderAnimatedBorder(graphics, left - 1, top - 1, right + 1, bottom + 1);
        UiShapes.roundedRect(graphics, left, top, right, bottom, 3, UiPalette.DEEP_BLUE);
        graphics.fill(sidebarRight, top + 18, sidebarRight + 1, bottom - 18, UiPalette.DIVIDER);
        graphics.fill(sidebarRight + 12, top + 28, right - 12, top + 29, UiPalette.DIVIDER);
        graphics.fill(sidebarRight + 12, bottom - 50, right - 12, bottom - 49, UiPalette.DIVIDER);
    }

    public static void renderCompanionPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        renderCompanionPanel(graphics, left, top, right, bottom, 1.0F);
    }

    public static void renderCompanionPanel(GuiGraphics graphics, int left, int top, int right, int bottom, float opacity) {
        renderAnimatedBorder(graphics, left - 1, top - 1, right + 1, bottom + 1, opacity);
        UiShapes.roundedRect(graphics, left, top, right, bottom, 3, withOpacity(UiPalette.DEEP_BLUE, opacity));
    }

    private static void renderAnimatedBorder(GuiGraphics graphics, int left, int top, int right, int bottom, float opacity) {
        float phase = (System.nanoTime() % 2_400_000_000L) / 2_400_000_000.0F;
        int width = right - left;
        int height = bottom - top;
        int corner = 3;
        for (int offset = corner; offset < width - corner; offset += 3) {
            int length = Math.min(3, width - offset);
            graphics.fill(left + offset, top, left + offset + length, top + 1, withOpacity(gradient(phase + offset / (float) width), opacity));
            graphics.fill(right - offset - length, bottom - 1, right - offset, bottom, withOpacity(gradient(phase + 0.5F + offset / (float) width), opacity));
        }
        for (int offset = corner; offset < height - corner; offset += 3) {
            int length = Math.min(3, height - 1 - offset);
            graphics.fill(left, top + offset, left + 1, top + offset + length, withOpacity(gradient(phase + 0.25F + offset / (float) height), opacity));
            graphics.fill(right - 1, bottom - offset - length, right, bottom - offset, withOpacity(gradient(phase + 0.75F + offset / (float) height), opacity));
        }
        UiShapes.roundedOutline(graphics, left, top, right, bottom, corner,
                withOpacity(gradient(phase), opacity), 0x00000000);
    }

    private static int withOpacity(int color, float opacity) {
        return Math.round((color >>> 24) * Math.max(0.0F, Math.min(1.0F, opacity))) << 24 | color & 0x00FFFFFF;
    }

    private static void renderAnimatedBorder(GuiGraphics graphics, int left, int top, int right, int bottom) {
        renderAnimatedBorder(graphics, left, top, right, bottom, 1.0F);
    }

    private static int gradient(float position) {
        float wrapped = position - (float) Math.floor(position);
        if (wrapped < 0.5F) {
            return UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, wrapped * 2.0F);
        }
        return UiMath.mix(UiPalette.LIGHT_GREEN, UiPalette.BRIGHT_BLUE, (wrapped - 0.5F) * 2.0F);
    }
}
