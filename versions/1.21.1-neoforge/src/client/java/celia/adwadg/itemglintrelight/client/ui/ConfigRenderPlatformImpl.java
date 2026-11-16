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
    public void scale(GuiGraphics graphics, float x, float y) {
        graphics.pose().scale(x, y, 1.0F);
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
    public void renderPreview(GuiGraphics graphics, net.minecraft.world.item.ItemStack stack,
                              celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig config,
                              int left, int top, int right, int bottom,
                              int layoutLeft, int layoutTop, int layoutWidth, int layoutHeight,
                              float zoom, float pitch, float yaw, float roll, float offsetX, float offsetY,
                              float uiScale) {
        celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderer.render(graphics, stack, config,
                layoutLeft, layoutTop, layoutWidth, layoutHeight, zoom, pitch, yaw, roll, offsetX, offsetY);
    }

    @Override
    public void clearPreview() {
    }

    @Override
    public void enableScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        UiScissor.enable(graphics, x1, y1, x2, y2);
    }

    @Override
    public void disableScissor(GuiGraphics graphics) {
        UiScissor.disable(graphics);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.screens.Screen screen, GuiGraphics graphics,
                                 int mouseX, int mouseY, float partialTick) {
        screen.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isWindowIconified() {
        return org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(Minecraft.getInstance().getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_ICONIFIED) == org.lwjgl.glfw.GLFW.GLFW_TRUE;
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
