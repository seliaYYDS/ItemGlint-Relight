package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the third-person outline's scene-depth snapshot where 1.21.11's main-batch flush used to be.
 *
 * <p>26.2 introduced {@code FeatureRenderDispatcher.PreparedFrame} and the snapshot sits on
 * {@code executeOutline}. 26.1.2 has no PreparedFrame: {@code LevelRenderer.addMainPass} drives the
 * phases directly and calls {@code renderSolidFeatures() -> renderTranslucentFeatures() ->
 * outlineBufferSource.endOutlineBatch() -> translucent terrain} (verified in the 26.1.2 bytecode,
 * {@code LevelRenderer.addMainPass} body). {@code endOutlineBatch} is therefore the 26.1.2 spelling of
 * {@code executeOutline}: it sits after every feature phase -- solid and translucent models, shadows,
 * name tags -- and still before translucent terrain, clouds and weather.
 *
 * <p>Being before translucent terrain is what lets the stroke stay visible over water and clouds while
 * still being occluded by walls, the player's own body and other entities.
 *
 * <p>{@code compositeThirdPersonAfterMainBatch} re-checks every gate (a pending capture, the
 * third-person pipeline being active, Iris' shadow pass, the debug switches), so this hook stays inert
 * on the many frames that take no capture at all.
 */
@Mixin(OutlineBufferSource.class)
public abstract class OutlineBatchFrameMixin {
    @Inject(method = "endOutlineBatch", at = @At("HEAD"))
    private void itemglintrelight$snapshotThirdPersonSceneDepth(CallbackInfo ci) {
        HeldItemOutlineRenderer.compositeThirdPersonAfterMainBatch(Minecraft.getInstance());
    }
}
