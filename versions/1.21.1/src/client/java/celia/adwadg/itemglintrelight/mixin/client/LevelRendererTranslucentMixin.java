package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.ThirdPersonOutlineManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snapshots the scene depth at the translucent layer boundary, before the level depth is cleared. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererTranslucentMixin {
    @Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void itemglintrelight$snapshotBeforeTranslucents(RenderType renderType, double cameraX, double cameraY,
            double cameraZ, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (renderType == RenderType.translucent()) {
            ThirdPersonOutlineManager.beforeTranslucentLayer();
        }
    }
}
