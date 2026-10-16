package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 900)
public abstract class GameRendererFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void itemglintrelight$beginFrame(CallbackInfo ci) {
        HeldItemOutlineRenderer.beginFrame();
    }

    /**
     * 26.2 splits a frame into {@code extract} (build the render states) and {@code render} (consume
     * them). GUI items are captured while {@code GuiGraphicsExtractor} runs during {@code extract}, and
     * the GUI renderer submits them later in {@code render}, so the GUI manager's frame boundary has to
     * sit in front of {@code extract}: resetting it at {@code render} would drop every capture made
     * during that same frame's extraction, which is exactly what left the GUI outline with no masks.
     */
    @Inject(method = "extract", at = @At("HEAD"))
    private void itemglintrelight$beginGuiFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        GuiItemOutlineManager.beginFrame();
    }

    /**
     * 26.3 dropped the hand projection from this call (the third argument is the lightmap now), so the
     * pass reuses the matrix the projection-buffer hook recorded for the very same upload.
     */
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void itemglintrelight$beginHandPass(CameraRenderState cameraRenderState, PlayerRenderState playerRenderState,
                                                GpuTextureView lightmap, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHandPass();
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void itemglintrelight$compositeHandOutline(CameraRenderState cameraRenderState, PlayerRenderState playerRenderState,
                                                       GpuTextureView lightmap, CallbackInfo ci) {
        HeldItemOutlineRenderer.composite(Minecraft.getInstance());
        HeldItemOutlineRenderer.endHandPass();
    }

    /**
     * Runs right after the level (so after translucent terrain, clouds and weather) and before the
     * GUI is extracted, which is exactly where the third-person outline must be composited: it is
     * no longer washed out by translucent geometry, yet it is not promoted above the GUI and keeps
     * depth testing against the opaque scene snapshot.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V",
            shift = At.Shift.AFTER))
    private void itemglintrelight$compositeThirdPersonAfterLevel(CallbackInfo ci) {
        HeldItemOutlineRenderer.compositeThirdPersonAfterLevel(Minecraft.getInstance());
    }
}
