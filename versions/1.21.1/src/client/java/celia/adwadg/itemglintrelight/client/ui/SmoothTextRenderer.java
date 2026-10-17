package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class SmoothTextRenderer {
    private static final int OPACITY_STEPS = 32;
    /** Fixed font bundled with the mod so the config UI never follows the operating system font. */
    private static final String BUNDLED_FONT_PATH = "/assets/itemglintrelight/font/noto_sans_light.ttf";
    /** Only reached for code points the bundled font does not cover (CJK, full-width punctuation, math symbols). */
    private static final java.awt.Font FALLBACK_FONT = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 18);
    private static java.awt.Font bundledFont;
    private static boolean bundledFontMissing;
    private static final Map<TextKey, TextureEntry> TEXTURES = new HashMap<>();
    private static final Map<GradientKey, GradientTexture> GRADIENT_TEXTURES = new HashMap<>();
    private static int textureIndex;

    private SmoothTextRenderer() {
    }

    public static void draw(GuiGraphics graphics, Font ignored, String text, float x, float y, float scale, int color) {
        if (text == null || text.isEmpty()) return;
        TextureEntry texture = texture(text, scale, color);
        drawTexture(graphics, texture, Math.round(x) - texture.inset(), Math.round(y) - texture.inset());
    }

    public static void draw(GuiGraphics graphics, Font ignored, String text, float x, float y, float scale, int color, float opacity) {
        int translucentColor = withOpacity(color, opacity);
        if ((translucentColor >>> 24) == 0) return;
        draw(graphics, ignored, text, x, y, scale, translucentColor);
    }

    public static void drawCentered(GuiGraphics graphics, Font ignored, String text, float centerX, float y, float scale, int color) {
        if (text == null || text.isEmpty()) return;
        TextureEntry texture = texture(text, scale, color);
        drawTexture(graphics, texture, Math.round(centerX - texture.width() / 2.0F), Math.round(y) - texture.inset());
    }

    public static int height(String text, float scale, int color) {
        if (text == null || text.isEmpty()) return 0;
        TextureEntry texture = texture(text, scale, color);
        return Math.max(1, texture.height() - texture.inset() * 2);
    }

    public static void drawCentered(GuiGraphics graphics, Font ignored, String text, float centerX, float y, float scale, int color, float opacity) {
        int translucentColor = withOpacity(color, opacity);
        if ((translucentColor >>> 24) == 0) return;
        drawCentered(graphics, ignored, text, centerX, y, scale, translucentColor);
    }

    public static int width(String text, float scale, int color) {
        if (text == null || text.isEmpty()) return 0;
        TextureEntry texture = texture(text, scale, color);
        return Math.max(1, texture.width() - texture.inset() * 2);
    }

    public static void drawGradient(GuiGraphics graphics, Font ignored, String text, float x, float y, float scale) {
        if (text == null || text.isEmpty()) return;
        int sampling = Math.max(2, (int) Minecraft.getInstance().getWindow().getGuiScale());
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
        int sampling = Math.max(2, (int) Minecraft.getInstance().getWindow().getGuiScale());
        TextKey key = new TextKey(text, Math.round(scale * 100.0F), color, sampling);
        return TEXTURES.computeIfAbsent(key, unused -> createTexture(text, scale, color, sampling));
    }

    private static int withOpacity(int color, float opacity) {
        int step = Math.max(0, Math.min(OPACITY_STEPS, Math.round(opacity * OPACITY_STEPS)));
        int alpha = Math.round((color >>> 24) * step / (float) OPACITY_STEPS);
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    private static void drawTexture(GuiGraphics graphics, TextureEntry texture, int x, int y) {
        // 1.21.1 has no GUI_TEXTURED pipeline: GuiGraphics#blit draws immediately through
        // getPositionTexShader without touching blend state (verified at runtime: GL_BLEND was off).
        // Draw through RenderType.text instead: it carries TRANSLUCENT_TRANSPARENCY and shares the
        // BufferSource with every fill, so ordering and blending match the 1.21.11 GUI pipeline.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(1.0F / texture.sampling(), 1.0F / texture.sampling(), 1.0F);
        PoseStack.Pose last = pose.last();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.text(texture.identifier()));
        int width = texture.sourceWidth();
        int height = texture.sourceHeight();
        int light = 0x00F000F0;
        consumer.addVertex(last, 0.0F, 0.0F, 0.0F).setColor(-1).setUv(0.0F, 0.0F)
                .setUv2(light & 0xFFFF, light >> 16);
        consumer.addVertex(last, 0.0F, height, 0.0F).setColor(-1).setUv(0.0F, 1.0F)
                .setUv2(light & 0xFFFF, light >> 16);
        consumer.addVertex(last, width, height, 0.0F).setColor(-1).setUv(1.0F, 1.0F)
                .setUv2(light & 0xFFFF, light >> 16);
        consumer.addVertex(last, width, 0.0F, 0.0F).setColor(-1).setUv(1.0F, 0.0F)
                .setUv2(light & 0xFFFF, light >> 16);
        pose.popPose();
        graphics.flush();
    }

    private static TextRaster rasterize(String text, float fontSize) {
        BufferedImage measuringImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measuringGraphics = measuringImage.createGraphics();
        measuringGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        measuringGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        FontRenderContext context = measuringGraphics.getFontRenderContext();
        measuringGraphics.dispose();
        TextLayout layout = createLayout(text, fontSize, context);
        return new TextRaster(layout, layout.getPixelBounds(context, 0.0F, 0.0F));
    }

    /**
     * Lays the text out with the bundled font and only falls back to the platform font for code
     * points the bundled font cannot draw, so CJK text and symbols such as {@code ⊂} stay readable.
     */
    private static TextLayout createLayout(String text, float fontSize, FontRenderContext context) {
        java.awt.Font primary = baseFont().deriveFont(fontSize);
        java.awt.Font fallback = FALLBACK_FONT.deriveFont(fontSize);
        AttributedString attributed = new AttributedString(text);
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            boolean primaryCovers = primary.canDisplay(codePoint);
            int runEnd = index + Character.charCount(codePoint);
            while (runEnd < text.length()) {
                int nextCodePoint = text.codePointAt(runEnd);
                if (primary.canDisplay(nextCodePoint) != primaryCovers) break;
                runEnd += Character.charCount(nextCodePoint);
            }
            attributed.addAttribute(TextAttribute.FONT, primaryCovers ? primary : fallback, index, runEnd);
            index = runEnd;
        }
        return new TextLayout(attributed.getIterator(), context);
    }

    private static java.awt.Font baseFont() {
        if (bundledFont != null) return bundledFont;
        if (bundledFontMissing) return FALLBACK_FONT;
        return loadBundledFont();
    }

    private static synchronized java.awt.Font loadBundledFont() {
        if (bundledFont != null) return bundledFont;
        if (bundledFontMissing) return FALLBACK_FONT;
        try (InputStream stream = SmoothTextRenderer.class.getResourceAsStream(BUNDLED_FONT_PATH)) {
            if (stream == null) {
                bundledFontMissing = true;
                return FALLBACK_FONT;
            }
            bundledFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, stream);
        } catch (Exception exception) {
            bundledFontMissing = true;
            return FALLBACK_FONT;
        }
        return bundledFont;
    }

    private static TextureEntry createTexture(String text, float scale, int color, int sampling) {
        int padding = sampling * 4;
        float fontSize = Math.max(9.0F, 18.0F * scale) * sampling;
        TextRaster raster = rasterize(text, fontSize);
        Rectangle bounds = raster.bounds();
        int sourceWidth = Math.max(1, bounds.width + padding * 2);
        int sourceHeight = Math.max(1, bounds.height + padding * 2);

        BufferedImage image = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setColor(new Color(color, true));
        raster.layout().draw(graphics, padding - bounds.x, padding - bounds.y);
        graphics.dispose();

        NativeImage nativeImage = new NativeImage(sourceWidth, sourceHeight, false);
        for (int pixelY = 0; pixelY < sourceHeight; pixelY++) {
            for (int pixelX = 0; pixelX < sourceWidth; pixelX++) {
                UiPainting.setPixel(nativeImage, pixelX, pixelY, image.getRGB(pixelX, pixelY));
            }
        }
        ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath("itemglintrelight", "ui_text/" + textureIndex++);
        DynamicTexture texture = new DynamicTexture(nativeImage);
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        texture.upload();
        int inset = Math.round(padding / (float) sampling);
        return new TextureEntry(identifier, Math.max(1, Math.round(sourceWidth / (float) sampling)),
                Math.max(1, Math.round(sourceHeight / (float) sampling)), sourceWidth, sourceHeight, inset, sampling);
    }

    private static GradientTexture createGradientTexture(String text, float scale, int sampling) {
        int padding = sampling * 4;
        float fontSize = Math.max(9.0F, 18.0F * scale) * sampling;
        TextRaster raster = rasterize(text, fontSize);
        Rectangle bounds = raster.bounds();
        int sourceWidth = Math.max(1, bounds.width + padding * 2);
        int sourceHeight = Math.max(1, bounds.height + padding * 2);

        BufferedImage mask = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskGraphics = mask.createGraphics();
        maskGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        maskGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        maskGraphics.setColor(Color.WHITE);
        raster.layout().draw(maskGraphics, padding - bounds.x, padding - bounds.y);
        maskGraphics.dispose();

        NativeImage nativeImage = new NativeImage(sourceWidth, sourceHeight, false);
        ResourceLocation identifier = ResourceLocation.fromNamespaceAndPath("itemglintrelight", "gradient_text/" + textureIndex++);
        DynamicTexture texture = new DynamicTexture(nativeImage);
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

    private record TextRaster(TextLayout layout, Rectangle bounds) {
    }

    private record TextureEntry(ResourceLocation identifier, int width, int height, int sourceWidth, int sourceHeight, int inset, int sampling) {
    }

    private record GradientTexture(TextureEntry entry, BufferedImage mask, NativeImage image, DynamicTexture texture) {
        private void update() {
            float phase = (System.nanoTime() % 1_800_000_000L) / 1_800_000_000.0F;
            for (int y = 0; y < entry.sourceHeight(); y++) {
                for (int x = 0; x < entry.sourceWidth(); x++) {
                    int alpha = mask.getRGB(x, y) >>> 24;
                    int color = gradientColor(x / (float) entry.sourceWidth() + phase);
                    UiPainting.setPixel(image, x, y, alpha << 24 | color & 0x00FFFFFF);
                }
            }
            texture.upload();
        }
    }
}
