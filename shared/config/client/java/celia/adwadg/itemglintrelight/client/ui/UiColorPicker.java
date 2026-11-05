package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class UiColorPicker implements UiComponent {
    private static final int MARKER_SIZE = 20;
    private static int textureIndex;
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
    private NativeImage paletteImage;
    private DynamicTexture paletteTexture;
    private Identifier paletteTextureId;
    private int renderedPaletteSize = -1;
    private float renderedPaletteHue = Float.NaN;
    private NativeImage markerImage;
    private DynamicTexture markerTexture;
    private Identifier markerTextureId;
    private int renderedMarkerColor = Integer.MIN_VALUE;
    private int markerSampling = -1;
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
        UiShapes.roundedRect(graphics, x - 1, paletteY - 1, x + paletteSize + 1, paletteY + paletteSize + 1, 5,
                UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, hoverAmount));

        float[] hsv = hsv(value.getAsInt());
        int hueX = x + paletteSize + 5;
        UiShapes.roundedRect(graphics, hueX - 1, paletteY - 1, hueX + 11, paletteY + paletteSize + 1, 5, UiPalette.PALE_BLUE);
        ensurePaletteTexture(paletteSize, hsv[0]);
        graphics.blit(RenderPipelines.GUI_TEXTURED, paletteTextureId, x, paletteY, 0.0F, 0.0F,
                paletteSize + 15, paletteSize, paletteSize + 15, paletteSize);
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
        UiShapes.roundedRect(graphics, hueX - 2, hueY - 1, hueX + 12, hueY + 2, 2, UiPalette.TEXT);
        UiShapes.roundedOutline(graphics, x - 1, paletteY + paletteSize + 9, x + width + 1, paletteY + paletteSize + 23, 5,
                UiPalette.PALE_BLUE, value.getAsInt());
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

    public void close() {
        if (paletteTexture != null) {
            Minecraft.getInstance().getTextureManager().release(paletteTextureId);
            paletteTexture = null;
            paletteImage = null;
        }
        if (markerTexture != null) {
            Minecraft.getInstance().getTextureManager().release(markerTextureId);
            markerTexture = null;
            markerImage = null;
        }
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

    private void ensurePaletteTexture(int paletteSize, float hue) {
        if (paletteTexture == null || renderedPaletteSize != paletteSize) {
            if (paletteTexture != null) Minecraft.getInstance().getTextureManager().release(paletteTextureId);
            paletteImage = new NativeImage(paletteSize + 15, paletteSize, false);
            paletteTexture = new DynamicTexture(() -> "itemglintrelight_color_palette", paletteImage);
            paletteTextureId = Identifier.fromNamespaceAndPath("itemglintrelight", "ui/color_palette/" + textureIndex++);
            Minecraft.getInstance().getTextureManager().register(paletteTextureId, paletteTexture);
            renderedPaletteSize = paletteSize;
            renderedPaletteHue = Float.NaN;
        }
        if (Math.abs(hue - renderedPaletteHue) < 0.0001F) return;
        for (int pixelY = 0; pixelY < paletteSize; pixelY++) {
            float brightness = 1.0F - pixelY / (float) Math.max(1, paletteSize - 1);
            int hueColor = 0xFF000000 | Color.HSBtoRGB(pixelY / (float) Math.max(1, paletteSize - 1), 1.0F, 1.0F);
            for (int pixelX = 0; pixelX < paletteSize; pixelX++) {
                float saturation = pixelX / (float) Math.max(1, paletteSize - 1);
                paletteImage.setPixel(pixelX, pixelY, 0xFF000000 | Color.HSBtoRGB(hue, saturation, brightness));
            }
            for (int huePixelX = paletteSize + 5; huePixelX < paletteSize + 15; huePixelX++) paletteImage.setPixel(huePixelX, pixelY, hueColor);
        }
        paletteTexture.upload();
        renderedPaletteHue = hue;
    }

    private void drawMarker(GuiGraphics graphics, int x, int y, int color) {
        ensureMarkerTexture(color);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x - MARKER_SIZE / 2.0F, y - MARKER_SIZE / 2.0F);
        graphics.pose().scale(1.0F / markerSampling, 1.0F / markerSampling);
        int sourceSize = MARKER_SIZE * markerSampling;
        graphics.blit(RenderPipelines.GUI_TEXTURED, markerTextureId, 0, 0, 0.0F, 0.0F, sourceSize, sourceSize, sourceSize, sourceSize);
        graphics.pose().popMatrix();
    }

    private void ensureMarkerTexture(int color) {
        int sampling = Math.max(2, Minecraft.getInstance().getWindow().getGuiScale() * 2);
        if (markerTexture == null || markerSampling != sampling) {
            if (markerTexture != null) Minecraft.getInstance().getTextureManager().release(markerTextureId);
            int sourceSize = MARKER_SIZE * sampling;
            markerImage = new NativeImage(sourceSize, sourceSize, false);
            markerTexture = new DynamicTexture(() -> "itemglintrelight_color_marker", markerImage);
            markerTextureId = Identifier.fromNamespaceAndPath("itemglintrelight", "ui/color_marker/" + textureIndex++);
            Minecraft.getInstance().getTextureManager().register(markerTextureId, markerTexture);
            markerSampling = sampling;
            renderedMarkerColor = Integer.MIN_VALUE;
        }
        if (renderedMarkerColor == color) return;
        markerImage.fillRect(0, 0, MARKER_SIZE * markerSampling, MARKER_SIZE * markerSampling, 0);
        float center = MARKER_SIZE * markerSampling / 2.0F;
        drawCircle(markerImage, center, center, 9.0F * markerSampling, 0xA0000000);
        drawCircle(markerImage, center, center, 8.0F * markerSampling, UiPalette.PALE_BLUE);
        drawCircle(markerImage, center, center, 6.0F * markerSampling, UiPalette.DEEP_BLUE);
        drawCircle(markerImage, center, center, 5.0F * markerSampling, color);
        drawCircle(markerImage, center - 2.0F * markerSampling, center - 2.0F * markerSampling, markerSampling, 0x88FFFFFF);
        markerTexture.upload();
        renderedMarkerColor = color;
    }

    private static void drawCircle(NativeImage image, float centerX, float centerY, float radius, int color) {
        int minX = Math.max(0, (int) Math.floor(centerX - radius - 1.0F));
        int maxX = Math.min(image.getWidth(), (int) Math.ceil(centerX + radius + 1.0F));
        int minY = Math.max(0, (int) Math.floor(centerY - radius - 1.0F));
        int maxY = Math.min(image.getHeight(), (int) Math.ceil(centerY + radius + 1.0F));
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                float distance = (float) Math.hypot(x + 0.5F - centerX, y + 0.5F - centerY);
                blend(image, x, y, color, Math.max(0.0F, Math.min(1.0F, radius + 0.75F - distance)));
            }
        }
    }

    private static void blend(NativeImage image, int x, int y, int color, float coverage) {
        float sourceAlpha = (color >>> 24) / 255.0F * coverage;
        if (sourceAlpha <= 0.0F) return;
        int existing = image.getPixel(x, y);
        float existingAlpha = (existing >>> 24) / 255.0F;
        float outputAlpha = sourceAlpha + existingAlpha * (1.0F - sourceAlpha);
        int red = Math.round((((color >>> 16) & 255) * sourceAlpha + ((existing >>> 16) & 255) * existingAlpha * (1.0F - sourceAlpha)) / outputAlpha);
        int green = Math.round((((color >>> 8) & 255) * sourceAlpha + ((existing >>> 8) & 255) * existingAlpha * (1.0F - sourceAlpha)) / outputAlpha);
        int blue = Math.round(((color & 255) * sourceAlpha + (existing & 255) * existingAlpha * (1.0F - sourceAlpha)) / outputAlpha);
        image.setPixel(x, y, Math.round(outputAlpha * 255.0F) << 24 | red << 16 | green << 8 | blue);
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
