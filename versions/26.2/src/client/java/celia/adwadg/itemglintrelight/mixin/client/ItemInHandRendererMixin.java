package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.InteractionHand;
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
 * First-person entry point.
 *
 * <p>26.2 renamed {@code renderHandsWithItems} to {@code submitHandsWithItems} and
 * {@code renderArmWithItem} to {@code submitArmWithItem}; the parameter lists are unchanged, so the
 * 1.21.11 injections carry over with the new method names only.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @ModifyVariable(method = "submitHandsWithItems", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private SubmitNodeCollector itemglintrelight$captureHandNodes(SubmitNodeCollector collector) {
        if (collector instanceof SubmitNodeStorage storage) {
            return HeldItemOutlineRenderer.wrapStorage(Minecraft.getInstance(), storage);
        }
        return HeldItemOutlineRenderer.wrapCollector(Minecraft.getInstance(), collector);
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void itemglintrelight$beginHand(AbstractClientPlayer player, float partialTick, float pitch,
                                            InteractionHand hand, float swing, ItemStack stack, float equip,
                                            PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHand(hand, stack);
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void itemglintrelight$endHand(AbstractClientPlayer player, float partialTick, float pitch,
                                          InteractionHand hand, float swing, ItemStack stack, float equip,
                                          PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.endHand();
    }

    /**
     * The arm the vanilla renderer draws from {@code submitArmWithItem}. Its nodes are diverted into
     * the arm-occluder mask so the outline is suppressed where the arm covers the item.
     */
    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void itemglintrelight$beginArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                   float equipProgress, float swingProgress, HumanoidArm arm,
                                                   CallbackInfo ci) {
        HeldItemOutlineRenderer.beginArmOccluderCapture(arm);
    }

    @Inject(method = "renderPlayerArm", at = @At("RETURN"))
    private void itemglintrelight$endArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                 float equipProgress, float swingProgress, HumanoidArm arm,
                                                 CallbackInfo ci) {
        HeldItemOutlineRenderer.endArmOccluderCapture();
    }

    @Inject(method = "renderMapHand", at = @At("HEAD"))
    private void itemglintrelight$beginMapArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                      HumanoidArm arm, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginArmOccluderCapture(arm);
    }

    @Inject(method = "renderMapHand", at = @At("RETURN"))
    private void itemglintrelight$endMapArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                    HumanoidArm arm, CallbackInfo ci) {
        HeldItemOutlineRenderer.endArmOccluderCapture();
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void itemglintrelight$beginItemSubmission(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                                      PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginItemSubmission(displayContext, poseStack);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void itemglintrelight$endItemSubmission(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                                    PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.endItemSubmission(displayContext);
    }
}
