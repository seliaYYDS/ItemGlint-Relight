package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.mixin.client.FeatureRenderDispatcherAccessor;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TridentItem;

public final class ItemPreviewRenderer extends PictureInPictureRenderer<ItemPreviewRenderState> {
    private SubmitNodeStorage storage;
    private RenderBuffers buffers;
    private FeatureRenderDispatcher dispatcher;
    private TextureTarget outlineMask;
    private long nextOutlineDiagnosticMillis;

    public ItemPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<ItemPreviewRenderState> getRenderStateClass() {
        return ItemPreviewRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "itemglintrelight_preview";
    }

    @Override
    protected void renderToTexture(ItemPreviewRenderState state, PoseStack pose) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureDispatcher(minecraft);
        ItemStackRenderState itemState = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(itemState, state.item(), previewDisplayContext(state), minecraft.level, null, 0);
        float height = state.y1() - state.y0();
        pose.translate(state.offsetX() / state.scale(), (state.offsetY() - height * 0.5F) / state.scale(), 0.0F);
        pose.scale(1.0F, -1.0F, -1.0F);
        pose.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(state.pitch())));
        pose.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(state.yaw())));
        pose.mulPose(new org.joml.Quaternionf().rotateZ((float) Math.toRadians(state.roll())));
        minecraft.gameRenderer.getLighting().setupFor(itemState.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        itemState.submit(pose, storage, 15728880, OverlayTexture.NO_OVERLAY, 0);
        dispatcher.renderAllFeatures();
        buffers.bufferSource().endBatch();
        buffers.outlineBufferSource().endOutlineBatch();
        buffers.crumblingBufferSource().endBatch();
        renderOutline(state, itemState, pose);
    }

    private void ensureDispatcher(Minecraft minecraft) {
        if (dispatcher != null) return;
        storage = new SubmitNodeStorage();
        buffers = new RenderBuffers(1);
        FeatureRenderDispatcher source = minecraft.gameRenderer.getFeatureRenderDispatcher();
        dispatcher = new FeatureRenderDispatcher(storage, minecraft.getBlockRenderer(), buffers.bufferSource(),
                ((FeatureRenderDispatcherAccessor) source).itemglintrelight$getAtlasManager(), buffers.outlineBufferSource(),
                buffers.crumblingBufferSource(), minecraft.font);
    }

    private static ItemDisplayContext previewDisplayContext(ItemPreviewRenderState state) {
        return state.item().getItem() instanceof ShieldItem
                || state.item().getItem() instanceof TridentItem
                || state.item().getItem() instanceof BannerItem
                || state.item().getItem() instanceof SpyglassItem
                ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI;
    }

    private void renderOutline(ItemPreviewRenderState state, ItemStackRenderState itemState, PoseStack pose) {
        GpuTextureView colorTarget = RenderSystem.outputColorTextureOverride;
        GpuTextureView depthTarget = RenderSystem.outputDepthTextureOverride;
        if (colorTarget == null || depthTarget == null || !state.config().outlineEnabled()) return;
        int width = colorTarget.getWidth(0);
        int height = colorTarget.getHeight(0);
        if (outlineMask == null) {
            outlineMask = new TextureTarget("itemglintrelight_preview_mask", width, height, true);
        } else if (outlineMask.width != width || outlineMask.height != height) {
            outlineMask.resize(width, height);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                outlineMask.getColorTexture(), 0, outlineMask.getDepthTexture(), 1.0D);
        RenderSystem.outputColorTextureOverride = outlineMask.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = outlineMask.getDepthTextureView();
        try {
            itemState.submit(pose, storage, 15728880, OverlayTexture.NO_OVERLAY, 0);
            outlineDiagnostic("mask nodes=" + storage.getSubmitsPerOrder().size() + " target=" + width + "x" + height);
            dispatcher.renderAllFeatures();
            buffers.bufferSource().endBatch();
            buffers.outlineBufferSource().endOutlineBatch();
            buffers.crumblingBufferSource().endBatch();
        } finally {
            RenderSystem.outputColorTextureOverride = colorTarget;
            RenderSystem.outputDepthTextureOverride = depthTarget;
        }
        HeldItemOutlineRenderer.compositePreviewToTexture(Minecraft.getInstance(), state.config(), outlineMask, colorTarget, depthTarget,
                HeldItemOutlineRenderer.resolvePreviewMaterialPalette(itemState, state.config()), state.outlineScale());
    }

    private void outlineDiagnostic(String message) {
        long now = System.currentTimeMillis();
        if (now >= nextOutlineDiagnosticMillis) {
            nextOutlineDiagnosticMillis = now + 1000L;
            ItemGlintRelight.LOGGER.info("[PreviewOutline] {}", message);
        }
    }
}
