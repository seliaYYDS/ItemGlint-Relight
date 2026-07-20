package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class UiMouseGlow {
    private static final int SIZE = 240;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("itemglintrelight", "ui/mouse_glow");
    private DynamicTexture texture;
    private NativeImage image;
    private float x;
    private float y;
    private boolean positioned;
    private long lastFrame = System.nanoTime();

    public void render(GuiGraphics graphics, float targetX, float targetY) {
        ensureTexture();
        updateTexture();
        float delta = deltaSeconds();
        if (!positioned) {
            x = targetX;
            y = targetY;
            positioned = true;
        }
        x = UiMath.approach(x, targetX, delta, 8.0F);
        y = UiMath.approach(y, targetY, delta, 8.0F);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, Math.round(x - SIZE / 2.0F), Math.round(y - SIZE / 2.0F),
                0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);
    }

    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
            texture = null;
            image = null;
        }
    }

    private void ensureTexture() {
        if (texture != null) {
            return;
        }
        image = new NativeImage(SIZE, SIZE, false);
        texture = new DynamicTexture(() -> "itemglintrelight_mouse_glow", image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
        updateTexture();
    }

    private void updateTexture() {
        float center = (SIZE - 1) / 2.0F;
        float radius = SIZE / 2.0F;
        float time = System.nanoTime() / 1_000_000_000.0F;
        float breathing = 0.92F + 0.08F * (float) Math.sin(time * 1.3F);
        float waveCenter = 0.38F + 0.06F * (float) Math.sin(time * 0.85F);
        for (int pixelY = 0; pixelY < SIZE; pixelY++) {
            for (int pixelX = 0; pixelX < SIZE; pixelX++) {
                float dx = (pixelX - center) / radius;
                float dy = (pixelY - center) / radius;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float angle = (float) Math.atan2(dy, dx);
                float core = (float) Math.exp(-distance * distance * 4.8F) * 0.095F;
                float wave = (float) Math.exp(-(distance - waveCenter) * (distance - waveCenter) * 15.0F) * 0.018F;
                float edge = (float) Math.exp(-(distance - 0.72F) * (distance - 0.72F) * 24.0F) * 0.006F;
                float ripple = 0.90F + 0.10F * (float) Math.sin(angle * 3.0F - time * 1.8F);
                float intensity = Math.max(0.0F, Math.min(1.0F, (core + wave + edge) * ripple * breathing));
                float colorMix = Math.max(0.0F, Math.min(1.0F, (dx - dy + 2.0F) / 4.0F));
                int color = UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, colorMix);
                int alpha = Math.round(intensity * 255.0F);
                image.setPixel(pixelX, pixelY, alpha << 24 | color & 0x00FFFFFF);
            }
        }
        texture.upload();
    }

    private float deltaSeconds() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastFrame) / 1_000_000_000.0F);
        lastFrame = now;
        return delta;
    }
}
