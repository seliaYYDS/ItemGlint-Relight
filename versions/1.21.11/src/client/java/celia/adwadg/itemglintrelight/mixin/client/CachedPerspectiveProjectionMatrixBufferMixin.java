package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import net.minecraft.client.renderer.CachedPerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The first-person hand pass renders with GameRenderer's cached perspective buffer, which is
 * unrelated to the matrix handed to renderItemInHand (that one is the camera rotation). Capture
 * the projection it builds so the hand outline scissor can project the item bounds with the
 * projection that is actually installed.
 */
@Mixin(CachedPerspectiveProjectionMatrixBuffer.class)
public abstract class CachedPerspectiveProjectionMatrixBufferMixin {
    @Inject(method = "createProjectionMatrix(IIF)Lorg/joml/Matrix4f;", at = @At("RETURN"))
    private void itemglintrelight$captureHandProjection(int width, int height, float fov, CallbackInfoReturnable<Matrix4f> cir) {
        HeldItemOutlineRenderer.captureHandProjection(cir.getReturnValue());
    }
}