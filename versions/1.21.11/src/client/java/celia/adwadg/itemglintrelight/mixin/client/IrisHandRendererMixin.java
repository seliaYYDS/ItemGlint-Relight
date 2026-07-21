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
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;iris$renderHandsWithCustomRenderer(Lnet/irisshaders/iris/pathways/HandRenderer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeStorage;Lnet/minecraft/client/player/LocalPlayer;I)V", remap = false),
            index = 3,
            remap = false)
    private SubmitNodeStorage itemglintrelight$mirrorSolid(SubmitNodeStorage storage) {
        return wrap(storage);
    }

    @ModifyArg(
            method = "renderTranslucent",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;iris$renderHandsWithCustomRenderer(Lnet/irisshaders/iris/pathways/HandRenderer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeStorage;Lnet/minecraft/client/player/LocalPlayer;I)V", remap = false),
            index = 3,
            remap = false)
    private SubmitNodeStorage itemglintrelight$mirrorTranslucent(SubmitNodeStorage storage) {
        return wrap(storage);
    }

    @Inject(method = "renderSolid", at = @At("RETURN"), remap = false)
    private void itemglintrelight$compositeSolid(CallbackInfo ci) {
        composite();
    }

    @Inject(method = "renderTranslucent", at = @At("RETURN"), remap = false)
    private void itemglintrelight$compositeTranslucent(CallbackInfo ci) {
        composite();
    }

    private static SubmitNodeStorage wrap(SubmitNodeStorage storage) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        return IrisOutlineBridge.isActive() && player != null ? HeldItemOutlineRenderer.wrapStorage(minecraft, storage) : storage;
    }

    private static void composite() {
        if (IrisOutlineBridge.isActive()) {
            HeldItemOutlineRenderer.composite(Minecraft.getInstance());
        }
    }
}
