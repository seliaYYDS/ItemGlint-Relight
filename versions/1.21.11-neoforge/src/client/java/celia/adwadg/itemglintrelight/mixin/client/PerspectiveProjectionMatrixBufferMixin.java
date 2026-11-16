package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * GameRenderer installs the level projection through this buffer, and RenderSystem only exposes
 * the uploaded uniform slice. The third-person outline scissor needs the matrix itself to project
 * the item's model bounds into clip space, so capture it on the way in.
 */
@Mixin(PerspectiveProjectionMatrixBuffer.class)
public abstract class PerspectiveProjectionMatrixBufferMixin {
    @Inject(method = "getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;", at = @At("HEAD"))
    private void itemglintrelight$captureLevelProjection(Matrix4f projection, CallbackInfoReturnable<GpuBufferSlice> cir) {
        HeldItemOutlineRenderer.captureLevelProjection(projection);
    }
}
