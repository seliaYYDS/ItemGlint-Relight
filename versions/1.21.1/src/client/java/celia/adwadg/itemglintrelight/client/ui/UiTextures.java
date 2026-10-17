package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Textured GUI quad for 1.21.1.
 *
 * <p>1.21.11 {@code GuiGraphics#blit} submits a {@code BlitRenderState} rendered with the
 * {@code GUI_TEXTURED} pipeline, which carries a translucent blend function. 1.21.1's
 * {@code GuiGraphics#blit} instead draws immediately through {@code getPositionTexShader} and never
 * touches blend state (verified: {@code GL_BLEND} was false at call time), so translucent textures
 * came out opaque. Drawing through {@code RenderType.text} restores the pipeline semantics: it has
 * {@code TRANSLUCENT_TRANSPARENCY}, shares the GUI BufferSource, and keeps draw order with fills.
 */
public final class UiTextures {
    private static final int FULL_BRIGHT = 0x00F000F0;

    private UiTextures() {
    }

    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                            float u, float v, int width, int height, int textureWidth, int textureHeight) {
        blit(graphics, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight);
    }

    /** Destination size and source region differ (atlas sub-rects). */
    public static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                            float u, float v, int width, int height, int uWidth, int vHeight,
                            int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || textureWidth <= 0 || textureHeight <= 0) {
            return;
        }
        float u0 = u / textureWidth;
        float u1 = (u + uWidth) / textureWidth;
        float v0 = v / textureHeight;
        float v1 = (v + vHeight) / textureHeight;
        PoseStack.Pose pose = graphics.pose().last();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.text(texture));
        consumer.addVertex(pose, x, y, 0.0F).setColor(-1).setUv(u0, v0)
                .setUv2(FULL_BRIGHT & 0xFFFF, FULL_BRIGHT >> 16);
        consumer.addVertex(pose, x, y + height, 0.0F).setColor(-1).setUv(u0, v1)
                .setUv2(FULL_BRIGHT & 0xFFFF, FULL_BRIGHT >> 16);
        consumer.addVertex(pose, x + width, y + height, 0.0F).setColor(-1).setUv(u1, v1)
                .setUv2(FULL_BRIGHT & 0xFFFF, FULL_BRIGHT >> 16);
        consumer.addVertex(pose, x + width, y, 0.0F).setColor(-1).setUv(u1, v0)
                .setUv2(FULL_BRIGHT & 0xFFFF, FULL_BRIGHT >> 16);
        graphics.flush();
    }
}
