package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** High-resolution prefiltered corner masks for smooth configuration surfaces. */
public final class UiShapes {
    private static final int MAX_RADIUS = 3;
    private static final int EDGE_SAMPLES = 4;
    private static final Map<TextureKey, CornerTexture> CORNER_TEXTURES = new HashMap<>();
    private static int textureIndex;

    private UiShapes() { }

    public static void roundedRect(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color) {
        int width = right - left;
        int height = bottom - top;
        int corner = Math.max(0, Math.min(Math.min(radius, MAX_RADIUS), Math.min(width, height) / 2));
        if (corner < 2 || (color >>> 24) == 0) {
            graphics.fill(left, top, right, bottom, color);
            return;
        }
        graphics.fill(left + corner, top, right - corner, bottom, color);
        graphics.fill(left, top + corner, right, bottom - corner, color);

        CornerTexture texture = cornerTexture(corner, color);
        drawCorner(graphics, texture, left, top, 0, 0);
        drawCorner(graphics, texture, right - corner, top, texture.cornerPixels(), 0);
        drawCorner(graphics, texture, left, bottom - corner, 0, texture.cornerPixels());
        drawCorner(graphics, texture, right - corner, bottom - corner, texture.cornerPixels(), texture.cornerPixels());
    }

    public static void roundedOutline(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int border, int fill) {
        roundedRect(graphics, left, top, right, bottom, radius, border);
        roundedRect(graphics, left + 1, top + 1, right - 1, bottom - 1, Math.max(1, radius - 1), fill);
    }

    public static int withAlpha(int color, float opacity) {
        int alpha = Math.round((color >>> 24) * Math.max(0.0F, Math.min(1.0F, opacity)));
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    public static void clear() {
        for (CornerTexture texture : CORNER_TEXTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(texture.identifier());
        }
        CORNER_TEXTURES.clear();
    }

    private static CornerTexture cornerTexture(int radius, int color) {
        int sampling = Math.max(2, Minecraft.getInstance().getWindow().getGuiScale() * 2);
        TextureKey key = new TextureKey(radius, sampling, quantize(color));
        return CORNER_TEXTURES.computeIfAbsent(key, UiShapes::createCornerTexture);
    }

    private static CornerTexture createCornerTexture(TextureKey key) {
        int cornerPixels = key.radius() * key.sampling();
        int texturePixels = cornerPixels * 2;
        NativeImage image = new NativeImage(texturePixels, texturePixels, false);
        float center = cornerPixels;
        for (int y = 0; y < texturePixels; y++) {
            for (int x = 0; x < texturePixels; x++) {
                image.setPixel(x, y, withAlpha(key.color(), coverage(x, y, center)));
            }
        }
        Identifier identifier = Identifier.fromNamespaceAndPath("itemglintrelight", "ui/rounded_corner/" + textureIndex++);
        DynamicTexture texture = new DynamicTexture(() -> "itemglintrelight_rounded_corner", image);
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        texture.upload();
        return new CornerTexture(identifier, cornerPixels, texturePixels, key.sampling());
    }

    private static float coverage(int pixelX, int pixelY, float center) {
        float covered = 0.0F;
        float radiusSquared = center * center;
        for (int sampleY = 0; sampleY < EDGE_SAMPLES; sampleY++) {
            for (int sampleX = 0; sampleX < EDGE_SAMPLES; sampleX++) {
                float offsetX = pixelX + (sampleX + 0.5F) / EDGE_SAMPLES - center;
                float offsetY = pixelY + (sampleY + 0.5F) / EDGE_SAMPLES - center;
                if (offsetX * offsetX + offsetY * offsetY <= radiusSquared) covered++;
            }
        }
        return covered / (EDGE_SAMPLES * EDGE_SAMPLES);
    }

    private static void drawCorner(GuiGraphics graphics, CornerTexture texture, int x, int y, int sourceX, int sourceY) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(1.0F / texture.sampling(), 1.0F / texture.sampling());
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.identifier(), 0, 0, sourceX, sourceY,
                texture.cornerPixels(), texture.cornerPixels(), texture.texturePixels(), texture.texturePixels());
        graphics.pose().popMatrix();
    }

    private static int quantize(int color) {
        int alpha = Math.min(255, ((color >>> 24) + 8) / 17 * 17);
        int red = Math.min(255, ((color >>> 16 & 0xFF) + 8) / 17 * 17);
        int green = Math.min(255, ((color >>> 8 & 0xFF) + 8) / 17 * 17);
        int blue = Math.min(255, ((color & 0xFF) + 8) / 17 * 17);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private record TextureKey(int radius, int sampling, int color) { }
    private record CornerTexture(Identifier identifier, int cornerPixels, int texturePixels, int sampling) { }
}
