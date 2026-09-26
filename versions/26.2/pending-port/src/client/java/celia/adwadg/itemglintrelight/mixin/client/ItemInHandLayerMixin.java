package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the held items of armor stands for the armor-stand outline.
 *
 * <p>An armor stand draws what it holds through vanilla's shared {@link ItemInHandLayer}, not through
 * the player-specific layer {@link PlayerItemInHandLayerMixin} hooks, so without this bridge a stand's
 * sword or torch never reaches the third-person capture. The layer submits each hand with a
 * third-person display context, which is exactly what the mirroring collector records.
 *
 * <p>The collector is wrapped for every armed entity and the capture is opened only for an
 * {@code ArmorStandRenderState}, the same narrowing {@link HumanoidArmorLayerMixin} uses: mobs keep
 * their vanilla path, and a stand that holds nothing opens no capture at all.
 *
 * <p>{@code PlayerItemInHandLayer} overrides {@code submitArmWithItem} without calling super, so the
 * local player's own capture in {@link PlayerItemInHandLayerMixin} is untouched by this bridge.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @ModifyVariable(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private SubmitNodeCollector itemglintrelight$wrapArmorStandCollector(SubmitNodeCollector collector) {
        return HeldItemOutlineRenderer.wrapArmorStandCollector(Minecraft.getInstance(), collector);
    }

    @Inject(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginArmorStandCapture(ArmedEntityRenderState renderState, ItemStackRenderState itemState,
                                                         ItemStack stack, HumanoidArm arm, PoseStack pose,
                                                         SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginArmorStandItem(renderState, stack, arm, pose);
    }

    @Inject(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"))
    private void itemglintrelight$endArmorStandCapture(ArmedEntityRenderState renderState, ItemStackRenderState itemState,
                                                       ItemStack stack, HumanoidArm arm, PoseStack pose,
                                                       SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.endArmorStandItem();
    }
}
