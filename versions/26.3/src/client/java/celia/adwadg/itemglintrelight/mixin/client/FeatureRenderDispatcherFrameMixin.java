package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the third-person outline's opaque scene-depth snapshot at the level's phase boundary.
 *
 * <p>1.21.11 hooked {@code MultiBufferSource.BufferSource#endBatch} and filtered it down to the level's
 * own buffer source, which flushed everything the level had buffered -- solid <em>and</em> translucent
 * entity geometry. 26.2 converted that into an injection on
 * {@code FeatureRenderDispatcher.PreparedFrame#executeOutline}, whose parameterless phase method the
 * main pass called unconditionally once per frame.
 *
 * <p><b>26.3 broke that assumption twice over.</b> The phase method now takes a {@code RenderPass}, and
 * {@code LevelRenderer} only reaches it from its own {@code executeOutline(PreparedFrame)}, which is
 * guarded by {@code currentFrameRendersEntityOutline}: the call into
 * {@code PreparedFrame#executeOutline} happens only while the player is rendering entity outlines
 * (glowing/spectral entities). During ordinary play it never runs, so a hook there never fires and the
 * snapshot is never taken -- the composite then reads no scene depth and the stroke is drawn over
 * everything.
 *
 * <p>{@code LevelRenderer#executeOutline} itself is still called on every frame, and it is reached
 * after the main pass has been closed. That second property matters too: 26.3's command encoder guards
 * {@code copyTextureToTexture} (which {@code RenderTarget#copyDepthFrom} uses) behind its
 * "no open render pass" check, so the snapshot has to be taken outside a pass.
 *
 * <p>The trade-off of the later point is that the snapshot now also contains translucent terrain and
 * weather; the stroke is therefore occluded by water and clouds as well as by walls and bodies.
 */
@Mixin(LevelRenderer.class)
public abstract class FeatureRenderDispatcherFrameMixin {
    @Inject(
            method = "executeOutline(Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V",
            at = @At("HEAD"))
    private void itemglintrelight$snapshotThirdPersonSceneDepth(FeatureRenderDispatcher.PreparedFrame frame,
                                                               CallbackInfo ci) {
        HeldItemOutlineRenderer.compositeThirdPersonAfterMainBatch(Minecraft.getInstance());
    }
}
