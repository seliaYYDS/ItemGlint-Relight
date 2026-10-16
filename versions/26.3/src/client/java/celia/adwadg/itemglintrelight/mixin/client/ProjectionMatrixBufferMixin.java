package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the projection matrices the renderer actually installs.
 *
 * <p>1.21.11 hooked {@code PerspectiveProjectionMatrixBuffer#getBuffer(Matrix4f)} for the level and
 * {@code CachedPerspectiveProjectionMatrixBuffer#createProjectionMatrix(int,int,float)} for the hand.
 * 26.2 deleted both classes and consolidated them into {@link ProjectionMatrixBuffer} plus
 * {@link Projection}: {@code renderLevel} builds the level matrix and uploads it through
 * {@code getBuffer(Matrix4f)}, then sets up its {@code hudProjection} through
 * {@code getBuffer(Projection)} right before it calls {@code renderItemInHand}. The hand pass
 * therefore renders with that second projection.
 *
 * <p>Both matrices are needed to project the captured item's model bounds into clip space for the
 * composite scissor; without them the mod falls back to a scissor-less composite.
 */
@Mixin(ProjectionMatrixBuffer.class)
public abstract class ProjectionMatrixBufferMixin {
    @Inject(method = "getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;", at = @At("HEAD"))
    private void itemglintrelight$captureLevelProjection(Matrix4f projection, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (HeldItemOutlineRenderer.isWorldProjectionCaptureOpen()) {
            HeldItemOutlineRenderer.captureLevelProjection(projection);
        }
    }

    @Inject(method = "getBuffer(Lnet/minecraft/client/renderer/Projection;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;", at = @At("HEAD"))
    private void itemglintrelight$captureHandProjection(Projection projection, CallbackInfoReturnable<GpuBufferSlice> cir) {
        // Only the world passes' uploads count: the same entry point serves the HUD and every GUI
        // picture-in-picture renderer, whose projections must not be mistaken for the hand pass' one.
        if (projection != null && HeldItemOutlineRenderer.isWorldProjectionCaptureOpen()) {
            HeldItemOutlineRenderer.captureHandProjection(projection.getMatrix(new Matrix4f()));
        }
    }
}
