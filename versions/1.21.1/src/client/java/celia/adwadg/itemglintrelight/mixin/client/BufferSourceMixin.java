package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.ThirdPersonOutlineManager;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snapshots the opaque scene depth at the level's main batch flush. Translucent terrain, clouds and
 * weather are drawn after this point, so the snapshot lets the third-person outline stay on top of
 * them without ever showing through a wall.
 */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin {
    @Inject(method = "endBatch", at = @At("TAIL"))
    private void itemglintrelight$snapshotThirdPersonDepth(CallbackInfo ci) {
        ThirdPersonOutlineManager.afterMainBatch((MultiBufferSource.BufferSource) (Object) this);
    }
}
