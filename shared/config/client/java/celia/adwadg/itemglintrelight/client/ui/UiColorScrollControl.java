package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class UiColorScrollControl implements UiComponent {
    private static final int TEXTURE_SIZE = 180;
    private static final int CENTER = TEXTURE_SIZE / 2;
    private static final float OUTER_RADIUS = 66.0F;
    private static final float MIN_INTERVAL = 0.25F;
    private static final float MAX_INTERVAL = 1.5F;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("itemglintrelight", "ui/colour_scroll_control");

    private int x;
    private int y;
    private final int width;
    private final String label;
    private final DoubleSupplier direction;
    private final DoubleSupplier interval;
    private final BiConsumer<Float, Float> changeListener;
    private DynamicTexture texture;
    private NativeImage image;
    private float renderedDirection = Float.NaN;
    private float renderedInterval = Float.NaN;
    private boolean renderedHover;
    private boolean dragging;

    public UiColorScrollControl(int x, int y, int width, String label, DoubleSupplier direction,
                                DoubleSupplier interval, BiConsumer<Float, Float> changeListener) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.direction = direction;
        this.interval = interval;
        this.changeListener = changeListener;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int height() {
        return 190;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        SmoothTextRenderer.draw(graphics, font, label, x, y, 0.76F, UiPalette.TEXT);
        int centerX = x + width / 2;
        int centerY = y + 102;
        boolean hovered = distanceSquared(mouseX - centerX, mouseY - centerY) <= 74 * 74;
        updateTexture((float) direction.getAsDouble(), (float) interval.getAsDouble(), hovered);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, centerX - CENTER, centerY - CENTER,
                0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        drawMarkers(graphics, font, centerX, centerY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) return false;
        dragging = true;
        update(mouseX, mouseY);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!dragging || button != 0) return false;
        update(mouseX, mouseY);
        return true;
    }

    public void stopDragging() {
        dragging = false;
    }

    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
            texture = null;
            image = null;
        }
    }

    private boolean contains(double mouseX, double mouseY) {
        float centerX = x + width / 2.0F;
        float centerY = y + 102.0F;
        return distanceSquared((float) mouseX - centerX, (float) mouseY - centerY) <= 74 * 74;
    }

    private void update(double mouseX, double mouseY) {
        float centerX = x + width / 2.0F;
        float centerY = y + 102.0F;
        float deltaX = (float) mouseX - centerX;
        float deltaY = (float) mouseY - centerY;
        float distance = (float) Math.sqrt(distanceSquared(deltaX, deltaY));
        if (distance < 0.001F) return;
        float directionDegrees = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
        float normalizedLength = clamp(distance / OUTER_RADIUS);
        changeListener.accept(directionDegrees, MIN_INTERVAL + normalizedLength * (MAX_INTERVAL - MIN_INTERVAL));
    }

    private void updateTexture(float currentDirection, float currentInterval, boolean hovered) {
        ensureTexture();
        if (Math.abs(currentDirection - renderedDirection) < 0.01F && Math.abs(currentInterval - renderedInterval) < 0.001F && hovered == renderedHover) return;
        renderedDirection = currentDirection;
        renderedInterval = currentInterval;
        renderedHover = hovered;
        image.fillRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE, 0);
        int ringColor = hovered ? UiPalette.PALE_BLUE : UiPalette.DIVIDER;
        drawRing(OUTER_RADIUS, 1.15F, ringColor);
        for (int degree = 0; degree < 360; degree += 45) {
            float radians = (float) Math.toRadians(degree);
            float cosine = (float) Math.cos(radians);
            float sine = (float) Math.sin(radians);
            drawSegment(CENTER + cosine * 70.0F, CENTER + sine * 70.0F, CENTER + cosine * 77.0F, CENTER + sine * 77.0F, 1.35F, UiPalette.PALE_BLUE);
        }
        float normalizedLength = clamp((currentInterval - MIN_INTERVAL) / (MAX_INTERVAL - MIN_INTERVAL));
        float arrowLength = normalizedLength * OUTER_RADIUS;
        float radians = (float) Math.toRadians(currentDirection);
        float arrowX = (float) Math.cos(radians);
        float arrowY = (float) Math.sin(radians);
        float endX = CENTER + arrowX * arrowLength;
        float endY = CENTER + arrowY * arrowLength;
        drawRing(8.0F, 1.0F, UiPalette.DIVIDER);
        drawSegment(CENTER, CENTER, endX, endY, 0.85F, UiPalette.BRIGHT_BLUE);
        drawSegment(endX, endY, endX - arrowX * 10.0F - arrowY * 4.0F, endY - arrowY * 10.0F + arrowX * 4.0F, 0.85F, UiPalette.LIGHT_GREEN);
        drawSegment(endX, endY, endX - arrowX * 10.0F + arrowY * 4.0F, endY - arrowY * 10.0F - arrowX * 4.0F, 0.85F, UiPalette.LIGHT_GREEN);
        texture.upload();
    }

    private void ensureTexture() {
        if (texture != null) return;
        image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false);
        texture = new DynamicTexture(() -> "itemglintrelight_colour_scroll_control", image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
    }

    private void drawMarkers(GuiGraphics graphics, Font font, int centerX, int centerY) {
        for (int degree = 0; degree < 360; degree += 45) {
            float radians = (float) Math.toRadians(degree);
            String marker = Integer.toString(degree);
            int markerX = Math.round(centerX + (float) Math.cos(radians) * 91.0F - SmoothTextRenderer.width(marker, 0.54F, UiPalette.MUTED_TEXT) / 2.0F);
            int markerY = Math.round(centerY + (float) Math.sin(radians) * 91.0F - SmoothTextRenderer.height(marker, 0.54F, UiPalette.MUTED_TEXT) / 2.0F);
            SmoothTextRenderer.draw(graphics, font, marker, markerX, markerY, 0.54F, UiPalette.MUTED_TEXT);
        }
    }

    private void drawRing(float radius, float halfWidth, int color) {
        for (int pixelY = 0; pixelY < TEXTURE_SIZE; pixelY++) {
            for (int pixelX = 0; pixelX < TEXTURE_SIZE; pixelX++) {
                float distance = (float) Math.hypot(pixelX + 0.5F - CENTER, pixelY + 0.5F - CENTER);
                blend(pixelX, pixelY, color, coverage(Math.abs(distance - radius) - halfWidth));
            }
        }
    }

    private void drawSegment(float fromX, float fromY, float toX, float toY, float halfWidth, int color) {
        float directionX = toX - fromX;
        float directionY = toY - fromY;
        float lengthSquared = directionX * directionX + directionY * directionY;
        for (int pixelY = 0; pixelY < TEXTURE_SIZE; pixelY++) {
            for (int pixelX = 0; pixelX < TEXTURE_SIZE; pixelX++) {
                float offsetX = pixelX + 0.5F - fromX;
                float offsetY = pixelY + 0.5F - fromY;
                float projection = lengthSquared <= 0.001F ? 0.0F : clamp((offsetX * directionX + offsetY * directionY) / lengthSquared);
                float nearX = fromX + directionX * projection;
                float nearY = fromY + directionY * projection;
                blend(pixelX, pixelY, color, coverage((float) Math.hypot(pixelX + 0.5F - nearX, pixelY + 0.5F - nearY) - halfWidth));
            }
        }
    }

    private void blend(int pixelX, int pixelY, int color, float alpha) {
        if (alpha <= 0.0F) return;
        int existing = image.getPixel(pixelX, pixelY);
        float existingAlpha = (existing >>> 24) / 255.0F;
        float outputAlpha = alpha + existingAlpha * (1.0F - alpha);
        int red = Math.round((((color >>> 16) & 255) * alpha + ((existing >>> 16) & 255) * existingAlpha * (1.0F - alpha)) / outputAlpha);
        int green = Math.round((((color >>> 8) & 255) * alpha + ((existing >>> 8) & 255) * existingAlpha * (1.0F - alpha)) / outputAlpha);
        int blue = Math.round(((color & 255) * alpha + (existing & 255) * existingAlpha * (1.0F - alpha)) / outputAlpha);
        image.setPixel(pixelX, pixelY, Math.round(outputAlpha * 255.0F) << 24 | red << 16 | green << 8 | blue);
    }

    private static float coverage(float signedDistance) {
        return clamp(0.75F - signedDistance);
    }

    private static float distanceSquared(float x, float y) {
        return x * x + y * y;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
