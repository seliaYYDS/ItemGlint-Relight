package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the third-person outline's opaque depth snapshot on 26.2.
 *
 * <p>1.21.11 hooked {@code MultiBufferSource.BufferSource#endBatch} and filtered it down to the
 * level's own buffer source. 26.2 deleted that class and drives the same drawing through
 * {@link FeatureRenderDispatcher.PreparedFrame}: {@code executeSolid()} draws the solid feature
 * batch -- the third-person player, their held items and their armor included -- after opaque
 * terrain but before translucent terrain, clouds and weather.
 *
 * <p>That is exactly the window the outline needs. The depth captured here holds opaque geometry
 * only, so the stroke is occluded by walls while still drawing over water and clouds; snapshotting
 * any later, at the end of the level, would let translucent geometry reject it. The snapshot also
 * becomes the item-free scene depth the Iris route prefers over Iris' own depthtex2.
 *
 * <p>{@code compositeThirdPersonAfterMainBatch} re-checks every gate (a pending capture, the
 * third-person pipeline being active, Iris' shadow pass, the debug switches), so this hook stays
 * inert on the many frames that take no capture at all.
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class FeatureRenderDispatcherFrameMixin {
    @Inject(method = "executeSolid", at = @At("RETURN"))
    private void itemglintrelight$snapshotThirdPersonSceneDepth(CallbackInfo ci) {
        HeldItemOutlineRenderer.compositeThirdPersonAfterMainBatch(Minecraft.getInstance());
    }
}
