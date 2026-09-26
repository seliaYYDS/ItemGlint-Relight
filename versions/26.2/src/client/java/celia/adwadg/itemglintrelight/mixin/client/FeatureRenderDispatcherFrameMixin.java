package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes the third-person outline's scene-depth snapshot where 1.21.11's main-batch flush used to be.
 *
 * <p>1.21.11 hooked {@code MultiBufferSource.BufferSource#endBatch} and filtered it down to the
 * level's own buffer source, which flushed everything the level had buffered -- solid <em>and</em>
 * translucent entity geometry. 26.2 has no such class: the same drawing is driven by
 * {@link FeatureRenderDispatcher.PreparedFrame}, and the level's main pass calls its phases in this
 * order (verified in {@code LevelRenderer.lambda$addMainPass$0}):
 *
 * <pre>
 * renderGroup(OPAQUE) -&gt; executeSolid() -&gt; executeTranslucent() -&gt; executeOutline()
 *     -&gt; renderGroup(TRANSLUCENT) -&gt; executeTranslucentAfterTerrain()
 * </pre>
 *
 * <p><b>The snapshot belongs at {@code executeOutline}.</b> {@code executeSolid} only runs each
 * collection's {@code solid} phase, and the player's skin is not in it: the body is submitted with
 * the translucent model render type, so it lands in {@code SubmitNodeCollection.translucentModels},
 * which {@code executeTranslucent} draws. Hooked at {@code executeSolid} the snapshot therefore held
 * the terrain but no player, and the stroke was composited straight through the body. This call site
 * is after every feature phase (solid and translucent models, shadows, name tags) and still before
 * translucent terrain, clouds and weather -- identical to where 1.21.11 took it, and unconditional,
 * unlike {@code executeAlwaysOnTop}.
 *
 * <p>Being before translucent terrain is what lets the stroke stay visible over water and clouds
 * while still being occluded by walls, the player's own body and other entities.
 *
 * <p>{@code compositeThirdPersonAfterMainBatch} re-checks every gate (a pending capture, the
 * third-person pipeline being active, Iris' shadow pass, the debug switches), so this hook stays
 * inert on the many frames that take no capture at all.
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class FeatureRenderDispatcherFrameMixin {
    @Inject(method = "executeOutline", at = @At("HEAD"))
    private void itemglintrelight$snapshotThirdPersonSceneDepth(CallbackInfo ci) {
        HeldItemOutlineRenderer.compositeThirdPersonAfterMainBatch(Minecraft.getInstance());
    }
}
