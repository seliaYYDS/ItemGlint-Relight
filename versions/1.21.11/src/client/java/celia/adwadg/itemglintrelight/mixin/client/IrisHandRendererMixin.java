package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import celia.adwadg.itemglintrelight.client.render.IrisOutlineBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer")
public abstract class IrisHandRendererMixin {
    @ModifyArg(
            method = "renderSolid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_759;iris$renderHandsWithCustomRenderer(Lnet/irisshaders/iris/pathways/HandRenderer;FLnet/minecraft/class_4587;Lnet/minecraft/class_11661;Lnet/minecraft/class_746;I)V", remap = false),
            index = 3,
            remap = false)
    private SubmitNodeStorage itemglintrelight$mirrorSolid(SubmitNodeStorage storage) {
        return wrap(storage);
    }

    @Inject(method = "renderSolid", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_759;iris$renderHandsWithCustomRenderer(Lnet/irisshaders/iris/pathways/HandRenderer;FLnet/minecraft/class_4587;Lnet/minecraft/class_11661;Lnet/minecraft/class_746;I)V", shift = At.Shift.AFTER, remap = false), remap = false, require = 0)
    private void itemglintrelight$captureAfterIrisSolid(CallbackInfo ci) {
        if (IrisOutlineBridge.isShaderPackActive()) {
            HeldItemOutlineRenderer.captureIrisHandMasks(Minecraft.getInstance());
        }
    }

    @ModifyArg(
            method = "renderTranslucent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/class_759;iris$renderHandsWithCustomRenderer(Lnet/irisshaders/iris/pathways/HandRenderer;FLnet/minecraft/class_4587;Lnet/minecraft/class_11661;Lnet/minecraft/class_746;I)V", remap = false),
            index = 3,
            remap = false)
    private SubmitNodeStorage itemglintrelight$mirrorTranslucent(SubmitNodeStorage storage) {
        return wrap(storage);
    }

    @Inject(method = "renderTranslucent", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_759;iris$renderHandsWithCustomRenderer(Lnet/irisshaders/iris/pathways/HandRenderer;FLnet/minecraft/class_4587;Lnet/minecraft/class_11661;Lnet/minecraft/class_746;I)V", shift = At.Shift.AFTER, remap = false), remap = false, require = 0)
    private void itemglintrelight$captureAfterIrisTranslucent(CallbackInfo ci) {
        if (IrisOutlineBridge.isShaderPackActive()) {
            HeldItemOutlineRenderer.captureIrisHandMasks(Minecraft.getInstance());
        }
    }

    private static SubmitNodeStorage wrap(SubmitNodeStorage storage) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        return IrisOutlineBridge.isShaderPackActive() && player != null ? HeldItemOutlineRenderer.wrapStorage(minecraft, storage) : storage;
    }
}
