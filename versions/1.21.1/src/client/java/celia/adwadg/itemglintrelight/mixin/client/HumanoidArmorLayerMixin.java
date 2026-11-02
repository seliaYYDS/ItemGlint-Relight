package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.ThirdPersonOutlineManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Worn armor is drawn as an entity model, not as item quads, so it is captured by routing the
 * layer's buffer source and opening one capture per armor group. Pieces that resolve to the same
 * config share the capture, mirroring the reference's merged silhouette.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private MultiBufferSource itemglintrelight$routeArmorBufferSource(MultiBufferSource source) {
        return ThirdPersonOutlineManager.route(source);
    }

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;I"
                    + "Lnet/minecraft/client/model/HumanoidModel;)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginArmorPiece(PoseStack pose, MultiBufferSource source, LivingEntity entity,
                                                  EquipmentSlot slot, int light, HumanoidModel<?> model, CallbackInfo ci) {
        ThirdPersonOutlineManager.beginArmorPiece(entity, entity.getItemBySlot(slot), pose);
    }

    @Inject(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN"))
    private void itemglintrelight$endArmorGroup(PoseStack pose, MultiBufferSource source, int light,
                                                LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                                CallbackInfo ci) {
        ThirdPersonOutlineManager.endArmorGroup();
    }
}
