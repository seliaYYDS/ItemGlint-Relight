package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Rendering seam between the two 1.21.x generations.
 *
 * <p>The newer generation poses with {@code pushMatrix}/{@code translate(x, y)} and blits through
 * {@code RenderPipelines.GUI_TEXTURED} with {@code Identifier}; 1.21.1 poses with
 * {@code pushPose}/{@code translate(x, y, z)}, blits through the {@code RenderType.text} quad that
 * {@code UiTextures} provides, and names the resource location {@code ResourceLocation}. Texture
 * creation and pixel access also changed shape.
 */
public interface ConfigRenderPlatform {
    void pushPose(GuiGraphics graphics);

    void popPose(GuiGraphics graphics);

    void translate(GuiGraphics graphics, float x, float y);

    void scale(GuiGraphics graphics, float factor);

    void scale(GuiGraphics graphics, float x, float y);

    void blit(GuiGraphics graphics, Object texture, int x, int y, float u, float v,
              int width, int height, int textureWidth, int textureHeight);

    void blit(GuiGraphics graphics, Object texture, int x, int y, float u, float v,
              int width, int height, int uWidth, int vHeight, int textureWidth, int textureHeight);

    Object textureId(String namespace, String path);

    Object createTexture(String name, NativeImage image);

    void registerTexture(Object id, Object texture);

    int pixel(NativeImage image, int x, int y);

    void setPixel(NativeImage image, int x, int y, int argb);

    void setPixelAbgr(NativeImage image, int x, int y, int abgr);

    void enableScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2);

    void disableScissor(GuiGraphics graphics);

    /** Draws the screen backdrop; newer versions do it in the default screen render, 1.21.1 does not. */
    void renderBackground(net.minecraft.client.gui.screens.Screen screen, GuiGraphics graphics,
                          int mouseX, int mouseY, float partialTick);

    boolean isWindowIconified();

    void uploadTexture(Object texture);

    void releaseTexture(Object id);

    int guiScale();
}
