package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public abstract class GameRendererFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void itemglintrelight$beginFrame(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginFrame();
        GuiItemOutlineManager.beginFrame();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void itemglintrelight$beginHandPass(net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
                                                float partialTick, Matrix4fc projectionMatrix, CallbackInfo ci) {
        // 26.2 passes the projection as the read-only Matrix4fc view; the renderer keeps a Matrix4f.
        HeldItemOutlineRenderer.beginHandPass(new Matrix4f(projectionMatrix));
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void itemglintrelight$compositeHandOutline(net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState,
                                                       float partialTick, Matrix4fc projectionMatrix, CallbackInfo ci) {
        HeldItemOutlineRenderer.composite(net.minecraft.client.Minecraft.getInstance());
        HeldItemOutlineRenderer.endHandPass();
    }

    /**
     * Runs right after the level (so after translucent terrain, clouds and weather) and before the
     * GUI is extracted, which is exactly where the third-person outline must be composited: it is
     * no longer washed out by translucent geometry, yet it is not promoted above the GUI and keeps
     * depth testing against the opaque scene snapshot.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void itemglintrelight$compositeThirdPersonAfterLevel(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        HeldItemOutlineRenderer.compositeThirdPersonAfterLevel(net.minecraft.client.Minecraft.getInstance());
    }
}
