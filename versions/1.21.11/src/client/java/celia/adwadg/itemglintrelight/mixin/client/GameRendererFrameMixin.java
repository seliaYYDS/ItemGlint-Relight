package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import celia.adwadg.itemglintrelight.client.render.IrisOutlineBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void itemglintrelight$beginFrame(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginFrame();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void itemglintrelight$beginHandPass(float partialTick, boolean renderLevel, Matrix4f projectionMatrix, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHandPass(projectionMatrix);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void itemglintrelight$compositeHandOutline(float partialTick, boolean renderLevel, Matrix4f projectionMatrix, CallbackInfo ci) {
        if (!IrisOutlineBridge.isActive()) {
            HeldItemOutlineRenderer.composite(net.minecraft.client.Minecraft.getInstance());
        }
        HeldItemOutlineRenderer.endHandPass();
    }
}
