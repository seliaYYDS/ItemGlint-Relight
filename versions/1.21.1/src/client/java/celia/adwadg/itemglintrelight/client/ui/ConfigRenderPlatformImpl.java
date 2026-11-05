package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** 1.21.1: PoseStack posing, RenderType.text quads, ResourceLocation ids, RGBA pixel storage. */
public final class ConfigRenderPlatformImpl implements ConfigRenderPlatform {
    @Override
    public void pushPose(GuiGraphics graphics) {
        graphics.pose().pushPose();
    }

    @Override
    public void popPose(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    @Override
    public void translate(GuiGraphics graphics, float x, float y) {
        graphics.pose().translate(x, y, 0.0F);
    }

    @Override
    public void scale(GuiGraphics graphics, float factor) {
        graphics.pose().scale(factor, factor, 1.0F);
    }

    @Override
    public void blit(GuiGraphics graphics, Object texture, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        UiTextures.blit(graphics, (ResourceLocation) texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    @Override
    public void blit(GuiGraphics graphics, Object texture, int x, int y, float u, float v,
                     int width, int height, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        UiTextures.blit(graphics, (ResourceLocation) texture, x, y, u, v, width, height, uWidth, vHeight,
                textureWidth, textureHeight);
    }

    @Override
    public Object textureId(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    @Override
    public Object createTexture(String name, NativeImage image) {
        return new net.minecraft.client.renderer.texture.DynamicTexture(image);
    }

    @Override
    public void registerTexture(Object id, Object texture) {
        Minecraft.getInstance().getTextureManager().register((ResourceLocation) id,
                (net.minecraft.client.renderer.texture.DynamicTexture) texture);
    }

    @Override
    public int pixel(NativeImage image, int x, int y) {
        return UiPainting.getPixel(image, x, y);
    }

    @Override
    public void setPixel(NativeImage image, int x, int y, int argb) {
        UiPainting.setPixel(image, x, y, argb);
    }

    @Override
    public void setPixelAbgr(NativeImage image, int x, int y, int abgr) {
        UiPainting.setPixel(image, x, y, UiPainting.fromAbgr(abgr));
    }


    @Override
    public void uploadTexture(Object texture) {
        ((net.minecraft.client.renderer.texture.DynamicTexture) texture).upload();
    }

    @Override
    public void releaseTexture(Object id) {
        Minecraft.getInstance().getTextureManager().release((ResourceLocation) id);
    }

    @Override
    public int guiScale() {
        return (int) Minecraft.getInstance().getWindow().getGuiScale();
    }
}
