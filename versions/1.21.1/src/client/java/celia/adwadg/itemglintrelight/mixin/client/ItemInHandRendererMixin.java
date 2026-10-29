package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void itemglintrelight$beginHand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                            float swing, ItemStack stack, float equip, PoseStack poseStack,
                                            MultiBufferSource source, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHand(hand, stack);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void itemglintrelight$endHand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                          float swing, ItemStack stack, float equip, PoseStack poseStack,
                                          MultiBufferSource source, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.endHand();
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void itemglintrelight$captureItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                              PoseStack poseStack, MultiBufferSource source, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.captureItem(stack, context, poseStack, light);
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void itemglintrelight$captureArm(PoseStack poseStack, MultiBufferSource source, int light,
                                             float equipProgress, float swingProgress, HumanoidArm arm, CallbackInfo ci) {
        HeldItemOutlineRenderer.captureArm(poseStack, light, equipProgress, swingProgress, arm);
    }

    @Inject(method = "renderMap", at = @At("HEAD"))
    private void itemglintrelight$captureMap(PoseStack poseStack, MultiBufferSource source, int light, ItemStack stack, CallbackInfo ci) {
        HeldItemOutlineRenderer.captureMap(poseStack, light, stack);
    }

    @Inject(method = "renderMapHand", at = @At("HEAD"))
    private void itemglintrelight$captureMapHand(PoseStack poseStack, MultiBufferSource source, int light, HumanoidArm arm, CallbackInfo ci) {
        HeldItemOutlineRenderer.captureMapHand(poseStack, light, arm);
    }
}
