package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
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
    private void itemglintrelight$beginHandPass(float partialTick, boolean renderLevel, Matrix4f projectionMatrix, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHandPass(projectionMatrix);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void itemglintrelight$compositeHandOutline(float partialTick, boolean renderLevel, Matrix4f projectionMatrix, CallbackInfo ci) {
        HeldItemOutlineRenderer.composite(net.minecraft.client.Minecraft.getInstance());
        HeldItemOutlineRenderer.endHandPass();
    }

    /**
     * Iris finalizes its level pipeline, including its color-space pass, from a tail callback on
     * {@code renderLevel}. Hook the caller instead of competing for that same tail injection.
     * At this point the main target's depth is the depth Iris used for its finished frame.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void itemglintrelight$compositeThirdPersonAfterIrisLevel(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        HeldItemOutlineRenderer.compositeThirdPersonAfterIrisFrame(net.minecraft.client.Minecraft.getInstance());
    }

}
