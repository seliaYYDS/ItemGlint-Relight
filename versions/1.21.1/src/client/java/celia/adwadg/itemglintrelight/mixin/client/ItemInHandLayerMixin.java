package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.ThirdPersonOutlineManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Third-person held items: the layer's buffer source is routed through the mirroring source, and the
 * capture is opened around {@code renderArmWithItem}. The same mixin covers armor stands, whose
 * renderer installs a plain {@link ItemInHandLayer}; the manager narrows the capture to
 * {@code ArmorStand} entities and to the local player's own layer call.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @ModifyVariable(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
                    + "ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private MultiBufferSource itemglintrelight$routeBufferSource(MultiBufferSource source) {
        return ThirdPersonOutlineManager.route(source);
    }

    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginThirdPersonItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                       HumanoidArm arm, PoseStack pose, MultiBufferSource source, int light,
                                                       CallbackInfo ci) {
        ThirdPersonOutlineManager.beginItem(entity, stack, pose);
    }

    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/HumanoidArm;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void itemglintrelight$endThirdPersonItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                     HumanoidArm arm, PoseStack pose, MultiBufferSource source, int light,
                                                     CallbackInfo ci) {
        ThirdPersonOutlineManager.endItem();
    }
}
