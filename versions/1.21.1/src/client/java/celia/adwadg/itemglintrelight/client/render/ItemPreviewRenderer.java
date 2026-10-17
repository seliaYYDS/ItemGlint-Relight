package celia.adwadg.itemglintrelight.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;

import java.nio.IntBuffer;

/**
 * Configuration-screen item preview.
 *
 * <p>1.21.11 renders the preview through {@code PictureInPictureRenderer}: the item is drawn into a
 * texture which is then submitted as a GUI blit, so it obeys normal GUI ordering and clipping.
 * 1.21.1 has no GUI render state; this class rebuilds the same model with a {@link TextureTarget}
 * and composites the result as a plain textured quad.
 *
 * <p>Three 1.21.1 details have to be reproduced exactly (all verified against the decompiled
 * sources):
 * <ul>
 *   <li>{@code GameRenderer#render} uses {@code setOrtho(0, w, h, 0, 1000, 21000)} with a model-view
 *       translation of {@code (0, 0, -11000)}; without it GUI z values sit outside the clip volume;</li>
 *   <li>{@code RenderTarget#clear(boolean)} binds the target, clears and then <em>unbinds</em> it, so
 *       the target must be rebound before drawing;</li>
 *   <li>GL scissor/cull state is global (1.21.11 {@code RenderPass} carries its own), so the active
 *       GUI scissor would clip the offscreen pass away; it is saved and disabled around the render.</li>
 * </ul>
 */
public final class ItemPreviewRenderer {
    /** Lightmap coordinate vanilla uses for GUI items; the preview is never shaded by the world. */
    private static final int FULL_BRIGHT = 15728880;
    private static final ByteBufferBuilder MODEL_BUFFER = new ByteBufferBuilder(786432);

    private static TextureTarget target;
    private static int targetWidth;
    private static int targetHeight;

    private ItemPreviewRenderer() {
    }

    public static void render(GuiGraphics graphics, ItemStack stack, int left, int top, int width, int height,
                              float zoom, float pitch, float yaw, float roll, float offsetX, float offsetY) {
        if (stack.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = Math.max(1.0D, minecraft.getWindow().getGuiScale());
        ensureTarget(Math.max(1, (int) Math.round(width * guiScale)), Math.max(1, (int) Math.round(height * guiScale)));

        renderModel(minecraft, stack, width, height, zoom, pitch, yaw, roll, offsetX, offsetY);
        blit(graphics, left, top, width, height);
    }

    private static void ensureTarget(int pixelWidth, int pixelHeight) {
        if (target != null && targetWidth == pixelWidth && targetHeight == pixelHeight) {
            return;
        }
        if (target != null) {
            target.destroyBuffers();
        }
        target = new TextureTarget(pixelWidth, pixelHeight, true, false);
        targetWidth = pixelWidth;
        targetHeight = pixelHeight;
    }

    private static void renderModel(Minecraft minecraft, ItemStack stack, int width, int height,
                                    float zoom, float pitch, float yaw, float roll, float offsetX, float offsetY) {
        // 1.21.1 keeps scissor/cull in global GL state; the caller is drawing inside the preview
        // canvas scissor, which would clip this offscreen pass away entirely.
        boolean scissorWasEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        IntBuffer scissorBox = BufferUtils.createIntBuffer(16);
        GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissorBox);
        boolean cullWasEnabled = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean depthTestWasEnabled = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean depthMaskWas = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        RenderSystem.disableScissor();
        RenderSystem.disableCull();

        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(true);
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, null, 0);
        float reference = Math.min(width, height) / 16.0F * 0.72F;
        float scale = Math.max(0.1F, reference * (zoom / 72.0F));

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, width, height, 0.0F, 1000.0F, 21000.0F),
                VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.translation(0.0F, 0.0F, -11000.0F);
        RenderSystem.applyModelViewMatrix();

        PoseStack pose = new PoseStack();
        pose.translate(width * 0.5F + offsetX, height * 0.5F + offsetY, 150.0F);
        pose.scale(16.0F * scale, -16.0F * scale, 16.0F * scale);
        pose.mulPose(new Quaternionf()
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .rotateZ((float) Math.toRadians(roll)));

        // Same lighting contract as GuiGraphics#renderItem: the GUI pass runs with 3D lighting and
        // flat models switch to flat lighting for the duration of their own draw only.
        Lighting.setupFor3DItems();
        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        }
        MultiBufferSource.BufferSource source = MultiBufferSource.immediate(MODEL_BUFFER);
        minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, source,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        source.endBatch();
        MODEL_BUFFER.clear();
        if (flat) {
            Lighting.setupFor3DItems();
        }

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        minecraft.getMainRenderTarget().bindWrite(true);

        if (!depthMaskWas) {
            RenderSystem.depthMask(false);
        }
        if (depthTestWasEnabled) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        if (cullWasEnabled) {
            RenderSystem.enableCull();
        }
        if (scissorWasEnabled) {
            RenderSystem.enableScissor(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3));
        }
    }

    /**
     * Composites the rendered texture as a normal GUI quad. The offscreen image uses the GUI y axis,
     * so v=1 is its top row.
     */
    private static void blit(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, left, top, 0.0F).setUv(0.0F, 1.0F);
        builder.addVertex(matrix, left, top + height, 0.0F).setUv(0.0F, 0.0F);
        builder.addVertex(matrix, left + width, top + height, 0.0F).setUv(1.0F, 0.0F);
        builder.addVertex(matrix, left + width, top, 0.0F).setUv(1.0F, 1.0F);
        MeshData mesh = builder.buildOrThrow();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableDepthTest();
        GlStateManager._bindTexture(0);
    }
}
