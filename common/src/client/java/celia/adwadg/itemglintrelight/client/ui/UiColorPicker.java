package celia.adwadg.itemglintrelight.client.ui;

import java.awt.Color;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiColorPicker implements UiComponent {
    private int x;
    private int y;
    private final int width;
    private final String label;
    private final IntSupplier value;
    private final IntConsumer changeListener;
    private float hoverAmount;
    private boolean dragging;
    private float markerX;
    private float markerY;
    private boolean markerInitialized;
    private long lastFrame = System.nanoTime();

    public UiColorPicker(int x, int y, int width, String label, IntSupplier value, IntConsumer changeListener) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.value = value;
        this.changeListener = changeListener;
    }

    public void setPosition(int x, int y) {
        if (markerInitialized) {
            markerX += x - this.x;
            markerY += y - this.y;
        }
        this.x = x;
        this.y = y;
    }

    public int height() {
        return width + 18;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        float delta = deltaSeconds();
        int paletteY = y + 16;
        int paletteSize = width - 20;
        boolean hovered = UiMath.contains(x, paletteY, paletteSize, paletteSize, mouseX, mouseY)
                || UiMath.contains(x + paletteSize + 5, paletteY, 10, paletteSize, mouseX, mouseY);
        hoverAmount = UiMath.approach(hoverAmount, hovered ? 1.0F : 0.0F, delta, 10.0F);
        SmoothTextRenderer.draw(graphics, font, label, x, y, 0.72F, UiPalette.MUTED_TEXT);
        graphics.fill(x - 1, paletteY - 1, x + paletteSize + 1, paletteY + paletteSize + 1,
                UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, hoverAmount));

        float[] hsv = hsv(value.getAsInt());
        int step = 3;
        for (int pixelY = 0; pixelY < paletteSize; pixelY += step) {
            float brightness = 1.0F - pixelY / (float) Math.max(1, paletteSize - 1);
            for (int pixelX = 0; pixelX < paletteSize; pixelX += step) {
                float saturation = pixelX / (float) Math.max(1, paletteSize - 1);
                int color = 0xFF000000 | Color.HSBtoRGB(hsv[0], saturation, brightness);
                graphics.fill(x + pixelX, paletteY + pixelY, x + Math.min(paletteSize, pixelX + step), paletteY + Math.min(paletteSize, pixelY + step), color);
            }
        }

        int hueX = x + paletteSize + 5;
        graphics.fill(hueX - 1, paletteY - 1, hueX + 11, paletteY + paletteSize + 1, UiPalette.PALE_BLUE);
        for (int pixelY = 0; pixelY < paletteSize; pixelY += step) {
            float hue = pixelY / (float) Math.max(1, paletteSize - 1);
            graphics.fill(hueX, paletteY + pixelY, hueX + 10, paletteY + Math.min(paletteSize, pixelY + step), 0xFF000000 | Color.HSBtoRGB(hue, 1.0F, 1.0F));
        }
        int markerX = x + Math.round(hsv[1] * (paletteSize - 1));
        int markerY = paletteY + Math.round((1.0F - hsv[2]) * (paletteSize - 1));
        if (!markerInitialized) {
            this.markerX = markerX;
            this.markerY = markerY;
            markerInitialized = true;
        }
        this.markerX = UiMath.approach(this.markerX, markerX, delta, 18.0F);
        this.markerY = UiMath.approach(this.markerY, markerY, delta, 18.0F);
        drawMarker(graphics, Math.round(this.markerX), Math.round(this.markerY), value.getAsInt());
        int hueY = paletteY + Math.round(hsv[0] * (paletteSize - 1));
        graphics.fill(hueX - 2, hueY - 1, hueX + 12, hueY + 2, UiPalette.TEXT);
        graphics.fill(x, paletteY + paletteSize + 10, x + width, paletteY + paletteSize + 22, value.getAsInt());
        graphics.fill(x - 1, paletteY + paletteSize + 9, x + width + 1, paletteY + paletteSize + 23, UiPalette.PALE_BLUE);
        graphics.fill(x, paletteY + paletteSize + 10, x + width, paletteY + paletteSize + 22, value.getAsInt());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int paletteY = y + 16;
        int paletteSize = width - 20;
        float[] hsv = hsv(value.getAsInt());
        if (UiMath.contains(x, paletteY, paletteSize, paletteSize, mouseX, mouseY)) {
            selectPalette(mouseX, mouseY, hsv, paletteY, paletteSize);
            dragging = true;
            return true;
        }
        int hueX = x + paletteSize + 5;
        if (UiMath.contains(hueX, paletteY, 10, paletteSize, mouseX, mouseY)) {
            selectHue(mouseY, hsv, paletteY, paletteSize);
            dragging = true;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!dragging || button != 0) {
            return false;
        }
        int paletteY = y + 16;
        int paletteSize = width - 20;
        float[] hsv = hsv(value.getAsInt());
        int hueX = x + paletteSize + 5;
        if (mouseX >= x && mouseX < x + paletteSize) {
            selectPalette(mouseX, mouseY, hsv, paletteY, paletteSize);
            return true;
        }
        if (mouseX >= hueX && mouseX < hueX + 10) {
            selectHue(mouseY, hsv, paletteY, paletteSize);
            return true;
        }
        return false;
    }

    public void stopDragging() {
        dragging = false;
    }

    private void selectPalette(double mouseX, double mouseY, float[] hsv, int paletteY, int paletteSize) {
        float saturation = Math.max(0.0F, Math.min(1.0F, ((float) mouseX - x) / Math.max(1, paletteSize - 1)));
        float brightness = 1.0F - Math.max(0.0F, Math.min(1.0F, ((float) mouseY - paletteY) / Math.max(1, paletteSize - 1)));
        changeListener.accept(argb(hsv[0], saturation, brightness, value.getAsInt() >>> 24));
    }

    private void selectHue(double mouseY, float[] hsv, int paletteY, int paletteSize) {
        float hue = Math.max(0.0F, Math.min(1.0F, ((float) mouseY - paletteY) / Math.max(1, paletteSize - 1)));
        changeListener.accept(argb(hue, hsv[1], hsv[2], value.getAsInt() >>> 24));
    }

    private static void drawMarker(GuiGraphics graphics, int x, int y, int color) {
        drawCircle(graphics, x, y, 9, 0xA0000000);
        drawCircle(graphics, x, y, 8, UiPalette.PALE_BLUE);
        drawCircle(graphics, x, y, 6, UiPalette.DEEP_BLUE);
        drawCircle(graphics, x, y, 5, color);
        drawCircle(graphics, x - 2, y - 2, 1, 0x88FFFFFF);
    }

    private static void drawCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int baseAlpha = color >>> 24;
        for (int offsetY = -radius - 1; offsetY <= radius; offsetY++) {
            for (int offsetX = -radius - 1; offsetX <= radius; offsetX++) {
                float distance = (float) Math.sqrt((offsetX + 0.5F) * (offsetX + 0.5F) + (offsetY + 0.5F) * (offsetY + 0.5F));
                float coverage = Math.max(0.0F, Math.min(1.0F, radius + 0.5F - distance));
                if (coverage > 0.0F) {
                    int alpha = Math.round(baseAlpha * coverage);
                    graphics.fill(centerX + offsetX, centerY + offsetY, centerX + offsetX + 1, centerY + offsetY + 1,
                            alpha << 24 | color & 0x00FFFFFF);
                }
            }
        }
    }

    private static float[] hsv(int color) {
        return Color.RGBtoHSB(color >>> 16 & 0xFF, color >>> 8 & 0xFF, color & 0xFF, null);
    }

    private static int argb(float hue, float saturation, float brightness, int alpha) {
        return alpha << 24 | Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
    }

    private float deltaSeconds() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastFrame) / 1_000_000_000.0F);
        lastFrame = now;
        return delta;
    }
}
