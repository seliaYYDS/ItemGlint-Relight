package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the local player's armor for the third-person armor outline.
 *
 * <p>Armor is submitted as an entity model rather than as item quads, so it never passes through
 * submitItem and the item-in-hand hook cannot see it. The collector handed to the armor layer is
 * wrapped for the duration of the call, which diverts its submitModel / submitModelPart nodes into
 * whichever capture is open.
 *
 * <p>The capture is opened per armor piece, because each piece is an item of its own and resolves its
 * own display rule -- a rule aimed at {@code minecraft:diamond_chestplate} has to reach the worn
 * diamond chestplate. Pieces that resolve to the same config share one capture, so an ordinary
 * four-piece set still produces a single merged silhouette in a single composite pass; the capture is
 * closed at the end of the layer.
 *
 * <p>{@link HeldItemOutlineRenderer#beginThirdPersonArmorPiece} narrows the capture to the local
 * player by checking the render state id, so every other humanoid simply wraps and passes through
 * without recording anything.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @ModifyVariable(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private SubmitNodeCollector itemglintrelight$wrapArmorCollector(SubmitNodeCollector collector) {
        return HeldItemOutlineRenderer.wrapArmorCollector(Minecraft.getInstance(), collector);
    }

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginArmorCapture(PoseStack pose, SubmitNodeCollector collector, ItemStack stack,
                                                   EquipmentSlot slot, int light, HumanoidRenderState renderState,
                                                   CallbackInfo ci) {
        HeldItemOutlineRenderer.beginThirdPersonArmorPiece(renderState, stack, slot, pose);
    }

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("RETURN"))
    private void itemglintrelight$endArmorCapture(PoseStack pose, SubmitNodeCollector collector, int light,
                                                  HumanoidRenderState renderState, float limbSwing, float limbSwingAmount,
                                                  CallbackInfo ci) {
        HeldItemOutlineRenderer.endThirdPersonArmorGroup();
    }
}
