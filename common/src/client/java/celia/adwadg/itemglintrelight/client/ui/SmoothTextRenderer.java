package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class SmoothTextRenderer {
    private static final java.awt.Font SYSTEM_FONT = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 18);
    private static final Map<TextKey, TextureEntry> TEXTURES = new HashMap<>();
    private static final Map<GradientKey, GradientTexture> GRADIENT_TEXTURES = new HashMap<>();
    private static int textureIndex;

    private SmoothTextRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font ignored, String text, float x, float y, float scale, int color) {
        TextureEntry texture = texture(text, scale, color);
        drawTexture(graphics, texture, Math.round(x) - texture.inset(), Math.round(y) - texture.inset());
    }

    public static void drawCentered(GuiGraphics graphics, Font ignored, String text, float centerX, float y, float scale, int color) {
        TextureEntry texture = texture(text, scale, color);
        drawTexture(graphics, texture, Math.round(centerX - texture.width() / 2.0F), Math.round(y) - texture.inset());
    }

    public static int height(String text, float scale, int color) {
        TextureEntry texture = texture(text, scale, color);
        return Math.max(1, texture.height() - texture.inset() * 2);
    }

    public static void drawGradient(GuiGraphics graphics, Font ignored, String text, float x, float y, float scale) {
        int sampling = Math.max(2, Minecraft.getInstance().getWindow().getGuiScale());
        GradientKey key = new GradientKey(text, Math.round(scale * 100.0F), sampling);
        GradientTexture texture = GRADIENT_TEXTURES.computeIfAbsent(key, unused -> createGradientTexture(text, scale, sampling));
        texture.update();
        drawTexture(graphics, texture.entry(), Math.round(x) - texture.entry().inset(), Math.round(y) - texture.entry().inset());
    }

    public static void clear() {
        for (TextureEntry texture : TEXTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(texture.identifier());
        }
        TEXTURES.clear();
        for (GradientTexture texture : GRADIENT_TEXTURES.values()) {
            Minecraft.getInstance().getTextureManager().release(texture.entry().identifier());
        }
        GRADIENT_TEXTURES.clear();
    }

    private static TextureEntry texture(String text, float scale, int color) {
        int sampling = Math.max(2, Minecraft.getInstance().getWindow().getGuiScale());
        TextKey key = new TextKey(text, Math.round(scale * 100.0F), color, sampling);
        return TEXTURES.computeIfAbsent(key, unused -> createTexture(text, scale, color, sampling));
    }

    private static void drawTexture(GuiGraphics graphics, TextureEntry texture, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(1.0F / texture.sampling(), 1.0F / texture.sampling());
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture.identifier(), 0, 0, 0.0F, 0.0F,
                texture.sourceWidth(), texture.sourceHeight(), texture.sourceWidth(), texture.sourceHeight());
        graphics.pose().popMatrix();
    }

    private static TextureEntry createTexture(String text, float scale, int color, int sampling) {
        int padding = sampling * 4;
        float fontSize = Math.max(9.0F, 18.0F * scale) * sampling;
        java.awt.Font font = SYSTEM_FONT.deriveFont(fontSize);
        BufferedImage measuringImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measuringGraphics = measuringImage.createGraphics();
        measuringGraphics.setFont(font);
        measuringGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        measuringGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontRenderContext context = measuringGraphics.getFontRenderContext();
        TextLayout layout = new TextLayout(text, font, context);
        Rectangle bounds = layout.getPixelBounds(context, 0.0F, 0.0F);
        int sourceWidth = Math.max(1, bounds.width + padding * 2);
        int sourceHeight = Math.max(1, bounds.height + padding * 2);
        measuringGraphics.dispose();

        BufferedImage image = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setFont(font);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setColor(new Color(color, true));
        graphics.drawString(text, padding - bounds.x, padding - bounds.y);
        graphics.dispose();

        NativeImage nativeImage = new NativeImage(sourceWidth, sourceHeight, false);
        for (int pixelY = 0; pixelY < sourceHeight; pixelY++) {
            for (int pixelX = 0; pixelX < sourceWidth; pixelX++) {
                nativeImage.setPixel(pixelX, pixelY, image.getRGB(pixelX, pixelY));
            }
        }
        Identifier identifier = Identifier.fromNamespaceAndPath("itemglintrelight", "system_text/" + textureIndex++);
        DynamicTexture texture = new DynamicTexture(() -> "itemglintrelight_system_text", nativeImage);
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        texture.upload();
        int inset = Math.round(padding / (float) sampling);
        return new TextureEntry(identifier, Math.max(1, Math.round(sourceWidth / (float) sampling)),
                Math.max(1, Math.round(sourceHeight / (float) sampling)), sourceWidth, sourceHeight, inset, sampling);
    }

    private static GradientTexture createGradientTexture(String text, float scale, int sampling) {
        int padding = sampling * 4;
        float fontSize = Math.max(9.0F, 18.0F * scale) * sampling;
        java.awt.Font font = SYSTEM_FONT.deriveFont(fontSize);
        BufferedImage measuringImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measuringGraphics = measuringImage.createGraphics();
        measuringGraphics.setFont(font);
        measuringGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        measuringGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontRenderContext context = measuringGraphics.getFontRenderContext();
        TextLayout layout = new TextLayout(text, font, context);
        Rectangle bounds = layout.getPixelBounds(context, 0.0F, 0.0F);
        int sourceWidth = Math.max(1, bounds.width + padding * 2);
        int sourceHeight = Math.max(1, bounds.height + padding * 2);
        measuringGraphics.dispose();

        BufferedImage mask = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskGraphics = mask.createGraphics();
        maskGraphics.setFont(font);
        maskGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        maskGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        maskGraphics.setColor(Color.WHITE);
        maskGraphics.drawString(text, padding - bounds.x, padding - bounds.y);
        maskGraphics.dispose();

        NativeImage nativeImage = new NativeImage(sourceWidth, sourceHeight, false);
        Identifier identifier = Identifier.fromNamespaceAndPath("itemglintrelight", "gradient_text/" + textureIndex++);
        DynamicTexture texture = new DynamicTexture(() -> "itemglintrelight_gradient_text", nativeImage);
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        int inset = Math.round(padding / (float) sampling);
        TextureEntry entry = new TextureEntry(identifier, Math.max(1, Math.round(sourceWidth / (float) sampling)),
                Math.max(1, Math.round(sourceHeight / (float) sampling)), sourceWidth, sourceHeight, inset, sampling);
        return new GradientTexture(entry, mask, nativeImage, texture);
    }

    private static int gradientColor(float position) {
        int[] colors = {UiPalette.LIGHT_GREEN, UiPalette.BRIGHT_BLUE, 0xFF167A5B, 0xFF174F8C};
        float wrapped = position - (float) Math.floor(position);
        float scaled = wrapped * colors.length;
        int index = (int) scaled;
        return UiMath.mix(colors[index], colors[(index + 1) % colors.length], scaled - index);
    }

    private record TextKey(String text, int scale, int color, int sampling) {
    }

    private record GradientKey(String text, int scale, int sampling) {
    }

    private record TextureEntry(Identifier identifier, int width, int height, int sourceWidth, int sourceHeight, int inset, int sampling) {
    }

    private record GradientTexture(TextureEntry entry, BufferedImage mask, NativeImage image, DynamicTexture texture) {
        private void update() {
            float phase = (System.nanoTime() % 1_800_000_000L) / 1_800_000_000.0F;
            for (int y = 0; y < entry.sourceHeight(); y++) {
                for (int x = 0; x < entry.sourceWidth(); x++) {
                    int alpha = mask.getRGB(x, y) >>> 24;
                    int color = gradientColor(x / (float) entry.sourceWidth() + phase);
                    image.setPixel(x, y, alpha << 24 | color & 0x00FFFFFF);
                }
            }
            texture.upload();
        }
    }
}
