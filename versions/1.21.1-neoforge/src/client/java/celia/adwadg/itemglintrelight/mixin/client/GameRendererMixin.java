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
     * The hand capture window spans the whole level render, not just {@code renderItemInHand}.
     *
     * <p>With a shader pack Iris cancels {@code renderArmWithItem} for translucent hand items during
     * the normal pass and redraws them from {@code renderLevel}'s tail ({@code HandRenderer
     * .renderTranslucent}), after {@code renderItemInHand} has returned. A window that closes there
     * would record nothing and an early composite would be overwritten by Iris' final pass.
     */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void itemglintrelight$beginHandPass(DeltaTracker deltaTracker, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHandPass();
    }

    /**
     * Composites right after {@code renderLevel} returns: Iris' final pass and its translucent hand
     * pass have both written to the main target by then, and the GUI has not started yet.
     */
    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER))
    private void itemglintrelight$compositeHandOutline(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        HeldItemOutlineRenderer.composite(Minecraft.getInstance());
        HeldItemOutlineRenderer.endHandPass();
    }
}
