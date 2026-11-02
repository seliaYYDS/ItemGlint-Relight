package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.ThirdPersonOutlineManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player layer overrides {@code renderArmWithItem} and calls super only for the ordinary case,
 * so the base-class injection cannot see the spyglass branch. Opening the capture here covers both
 * paths: the geometry is mirrored by the routed buffer source either way.
 */
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandLayerMixin {
    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginPlayerItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                  HumanoidArm arm, PoseStack pose, MultiBufferSource source, int light,
                                                  CallbackInfo ci) {
        ThirdPersonOutlineManager.beginItem(entity, stack, pose);
    }

    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void itemglintrelight$endPlayerItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                HumanoidArm arm, PoseStack pose, MultiBufferSource source, int light,
                                                CallbackInfo ci) {
        ThirdPersonOutlineManager.endItem();
    }
}
