package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TridentItem;
import org.joml.Quaternionf;

/**
 * 3D item preview for the config screen, including the outline.
 *
 * <p>The item is rendered twice, exactly like 1.21.11: once into the picture-in-picture colour target
 * and once into a private mask target whose colour holds the silhouette and whose depth holds the
 * item depth the composite shader tests against. {@link HeldItemOutlineRenderer#compositePreviewToTexture}
 * then turns that mask into the stroke (plus its bloom halo) on top of the item.
 *
 * <p>26.2 note on ordering: {@code PictureInPictureRenderer.prepare} hands this method a
 * {@link SubmitNodeCollector} but only replays it through {@code renderAllFeatures} <em>after</em>
 * this method returns, and it is that replay which draws the item. Drawing the item through the
 * collector would therefore put it on top of an outline composited here, so the item is submitted to
 * the mod's own {@link SubmitNodeStorage} and flushed directly; the supplied collector stays empty.
 */
public final class ItemPreviewRenderer extends PictureInPictureRenderer<ItemPreviewRenderState> {
    private RenderBuffers buffers;
    private FeatureRenderDispatcher dispatcher;
    private TextureTarget outlineMask;
    private long nextOutlineDiagnosticMillis;

    @Override
    public Class<ItemPreviewRenderState> getRenderStateClass() {
        return ItemPreviewRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "itemglintrelight_preview";
    }

    @Override
    protected void renderToTexture(ItemPreviewRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureDispatcher(minecraft);
        ItemStackRenderState itemState = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(itemState, state.item(), previewDisplayContext(state),
                minecraft.level, null, 0);

        float height = state.y1() - state.y0();
        pose.translate(state.offsetX() / state.scale(), (state.offsetY() - height * 0.5F) / state.scale(), 0.0F);
        pose.scale(1.0F, -1.0F, -1.0F);
        pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(state.pitch())));
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(state.yaw())));
        pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(state.roll())));
        minecraft.gameRenderer.lighting().setupFor(
                itemState.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);

        SubmitNodeStorage colorStorage = new SubmitNodeStorage();
        itemState.submit(pose, colorStorage, 15728880, OverlayTexture.NO_OVERLAY, 0);
        dispatcher.renderAllFeatures(colorStorage);
        buffers.endFrame();

        renderOutline(state, itemState, pose);
    }

    /**
     * 26.2's {@code FeatureRenderDispatcher} no longer owns a target storage and takes its buffers from
     * the constructor, so the preview keeps a private pair instead of reaching into the game's.
     */
    private void ensureDispatcher(Minecraft minecraft) {
        if (dispatcher != null) {
            return;
        }
        buffers = new RenderBuffers(1);
        dispatcher = new FeatureRenderDispatcher(buffers, minecraft.getModelManager(), minecraft.getAtlasManager(),
                minecraft.font, minecraft.gameRenderer.gameRenderState());
    }

    private void renderOutline(ItemPreviewRenderState state, ItemStackRenderState itemState, PoseStack pose) {
        GpuTextureView colorTarget = RenderSystem.outputColorTextureOverride;
        GpuTextureView depthTarget = RenderSystem.outputDepthTextureOverride;
        if (colorTarget == null || depthTarget == null || !state.config().outlineEnabled()) {
            return;
        }
        int width = colorTarget.getWidth(0);
        int height = colorTarget.getHeight(0);
        if (outlineMask == null) {
            outlineMask = new TextureTarget("itemglintrelight_preview_mask", width, height, true, GpuFormat.RGBA8_UNORM);
        } else if (outlineMask.width != width || outlineMask.height != height) {
            outlineMask.resize(width, height);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                outlineMask.getColorTexture(), HeldItemOutlineRenderer.clearColor(), outlineMask.getDepthTexture(),
                HeldItemOutlineRenderer.farDepth());
        RenderSystem.outputColorTextureOverride = outlineMask.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = outlineMask.getDepthTextureView();
        SubmitNodeStorage maskStorage = new SubmitNodeStorage();
        try {
            itemState.submit(pose, maskStorage, 15728880, OverlayTexture.NO_OVERLAY, 0);
            outlineDiagnostic("mask orders=" + maskStorage.getSubmitsPerOrder().size() + " target=" + width + "x" + height);
            dispatcher.renderAllFeatures(maskStorage);
            buffers.endFrame();
        } finally {
            RenderSystem.outputColorTextureOverride = colorTarget;
            RenderSystem.outputDepthTextureOverride = depthTarget;
        }
        HeldItemOutlineRenderer.compositePreviewToTexture(Minecraft.getInstance(), state.config(), outlineMask,
                colorTarget, depthTarget, HeldItemOutlineRenderer.resolvePreviewMaterialPalette(itemState, state.config()),
                state.outlineScale());
    }

    @Override
    public void close() {
        super.close();
        if (dispatcher != null) {
            dispatcher.close();
            dispatcher = null;
        }
        if (buffers != null) {
            buffers.close();
            buffers = null;
        }
        if (outlineMask != null) {
            outlineMask.destroyBuffers();
            outlineMask = null;
        }
    }

    private void outlineDiagnostic(String message) {
        long now = System.currentTimeMillis();
        if (now >= nextOutlineDiagnosticMillis) {
            nextOutlineDiagnosticMillis = now + 1000L;
            ItemGlintRelight.LOGGER.info("[PreviewOutline] {}", message);
        }
    }

    private static ItemDisplayContext previewDisplayContext(ItemPreviewRenderState state) {
        return state.item().getItem() instanceof ShieldItem
                || state.item().getItem() instanceof TridentItem
                || state.item().getItem() instanceof BannerItem
                || state.item().getItem() instanceof SpyglassItem
                ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI;
    }
}
