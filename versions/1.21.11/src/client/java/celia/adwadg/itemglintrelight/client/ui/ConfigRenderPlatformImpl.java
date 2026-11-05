package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Newer 1.21.x: matrix posing, GUI_TEXTURED blits, Identifier ids, ARGB pixel storage. */
public final class ConfigRenderPlatformImpl implements ConfigRenderPlatform {
    @Override
    public void pushPose(GuiGraphics graphics) {
        graphics.pose().pushMatrix();
    }

    @Override
    public void popPose(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }

    @Override
    public void translate(GuiGraphics graphics, float x, float y) {
        graphics.pose().translate(x, y);
    }

    @Override
    public void scale(GuiGraphics graphics, float factor) {
        graphics.pose().scale(factor, factor);
    }

    @Override
    public void blit(GuiGraphics graphics, Object texture, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, (Identifier) texture, x, y, u, v, width, height,
                textureWidth, textureHeight);
    }

    @Override
    public void blit(GuiGraphics graphics, Object texture, int x, int y, float u, float v,
                     int width, int height, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, (Identifier) texture, x, y, u, v, width, height,
                uWidth, vHeight, textureWidth, textureHeight);
    }

    @Override
    public Object textureId(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    @Override
    public Object createTexture(String name, NativeImage image) {
        return new DynamicTexture(() -> name, image);
    }

    @Override
    public void registerTexture(Object id, Object texture) {
        Minecraft.getInstance().getTextureManager().register((Identifier) id, (DynamicTexture) texture);
    }

    @Override
    public int pixel(NativeImage image, int x, int y) {
        return image.getPixel(x, y);
    }

    @Override
    public void setPixel(NativeImage image, int x, int y, int argb) {
        image.setPixel(x, y, argb);
    }

    @Override
    public void setPixelAbgr(NativeImage image, int x, int y, int abgr) {
        image.setPixelABGR(x, y, abgr);
    }


    @Override
    public void uploadTexture(Object texture) {
        ((DynamicTexture) texture).upload();
    }

    @Override
    public void releaseTexture(Object id) {
        Minecraft.getInstance().getTextureManager().release((Identifier) id);
    }

    @Override
    public int guiScale() {
        return (int) Minecraft.getInstance().getWindow().getGuiScale();
    }
}
