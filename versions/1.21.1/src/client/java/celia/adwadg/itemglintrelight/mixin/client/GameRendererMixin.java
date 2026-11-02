package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import celia.adwadg.itemglintrelight.client.render.ThirdPersonOutlineManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void itemglintrelight$beginFrame(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginFrame();
        GuiItemOutlineManager.beginFrame();
        ThirdPersonOutlineManager.beginFrame();
    }

    /**
     * Composites after {@code renderLevel} has fully returned, so Iris' {@code finalizeGameRendering}
     * tail (its final pass) has already written the world into the main target.
     */
    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER))
    private void itemglintrelight$compositeThirdPerson(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        ThirdPersonOutlineManager.compositeAfterLevel();
    }

    /**
     * 1.21.1 draws the hands at the end of {@code renderItemInHand}, so the return of that method is
     * the first point where the captured geometry can be replayed into the mask and composited.
     */
    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void itemglintrelight$compositeHandOutline(Camera camera, float partialTick, Matrix4f projectionMatrix, CallbackInfo ci) {
        HeldItemOutlineRenderer.composite(Minecraft.getInstance());
    }
}
