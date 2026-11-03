package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlinePunchyCompat;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Punchy bridge for the first-person outline.
 *
 * <p>Punchy replaces the first-person pass with {@code PunchyArmRenderer}: it draws the arm and the
 * held item itself and never calls {@code ItemInHandRenderer#renderArmWithItem}, which is where this
 * mod opens its hand capture window. Without this bridge the window never opens, so no first-person
 * outline is captured at all.
 *
 * <p>Punchy still routes normal items through {@code ItemInHandRenderer#renderItem} and held maps
 * through {@code renderMap}, so the existing item/arm capture hooks keep working once the window is
 * open. The window is opened with the stack Punchy is actually about to draw, which is not
 * necessarily the live hand stack while an equip or use animation runs.
 *
 * <p>The target class only exists while Punchy is installed; {@code @Pseudo} plus {@code require = 0}
 * keeps this mixin inert otherwise, and the parameters are vanilla types only.
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {
    @ModifyVariable(method = "renderArm", at = @At("HEAD"), argsOnly = true, index = 5, require = 0)
    private static MultiBufferSource itemglintrelight$mirrorPunchyArmGeometry(MultiBufferSource source,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) HumanoidArm arm) {
        return HeldItemOutlineRenderer.wrapArmGeometry(source, arm);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), require = 0)
    private static void itemglintrelight$beginPunchyHand(ItemInHandRenderer handRenderer, PlayerModel playerModel,
            LocalPlayer player, HumanoidArm arm, PoseStack poseStack, MultiBufferSource source, int light,
            Matrix4f rootWorld, ModelPart armPart, ModelPart itemPart, ModelPart gripPart, float partialTicks,
            CallbackInfo ci) {
        InteractionHand hand = resolveHand(player, arm);
        if (hand == null) {
            return;
        }
        HeldItemOutlineRenderer.beginHand(hand, HeldItemOutlinePunchyCompat.resolveRenderedHandStack(player, hand));
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"), require = 0)
    private static void itemglintrelight$endPunchyHand(ItemInHandRenderer handRenderer, PlayerModel playerModel,
            LocalPlayer player, HumanoidArm arm, PoseStack poseStack, MultiBufferSource source, int light,
            Matrix4f rootWorld, ModelPart armPart, ModelPart itemPart, ModelPart gripPart, float partialTicks,
            CallbackInfo ci) {
        HeldItemOutlineRenderer.endHand();
    }

    private static InteractionHand resolveHand(LocalPlayer player, HumanoidArm arm) {
        if (player == null || arm == null) {
            return null;
        }
        return player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
