package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person entry point.
 *
 * <p>26.3 renamed the renderer to {@code FirstPersonHandsAndItemsRenderer} and folded the separate
 * {@code renderItem} helper into {@code submitArmWithItem}: the item submission window is therefore
 * bracketed by the same two injections that already bracket the hand.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {
    @ModifyVariable(method = "submitHandsWithItems", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private SubmitNodeCollector itemglintrelight$captureHandNodes(SubmitNodeCollector collector) {
        if (collector instanceof SubmitNodeStorage storage) {
            return HeldItemOutlineRenderer.wrapStorage(Minecraft.getInstance(), storage);
        }
        return HeldItemOutlineRenderer.wrapCollector(Minecraft.getInstance(), collector);
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void itemglintrelight$beginHand(PlayerRenderState playerRenderState, FirstPersonHandsAndItemsRenderState handsState,
                                            float x, float y, InteractionHand hand, float swing, ItemStack stack, float equip,
                                            PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHand(hand, stack);
        HeldItemOutlineRenderer.beginItemSubmission(hand, poseStack);
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void itemglintrelight$endHand(PlayerRenderState playerRenderState, FirstPersonHandsAndItemsRenderState handsState,
                                          float x, float y, InteractionHand hand, float swing, ItemStack stack, float equip,
                                          PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.endItemSubmission(hand);
        HeldItemOutlineRenderer.endHand();
    }

    /**
     * The arm the vanilla renderer draws from {@code submitArmWithItem}. Its nodes are diverted into
     * the arm-occluder mask so the outline is suppressed where the arm covers the item.
     */
    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void itemglintrelight$beginArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                   float equipProgress, float swingProgress, HumanoidArm arm,
                                                   PlayerRenderState playerRenderState, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginArmOccluderCapture(arm);
    }

    @Inject(method = "renderPlayerArm", at = @At("RETURN"))
    private void itemglintrelight$endArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                 float equipProgress, float swingProgress, HumanoidArm arm,
                                                 PlayerRenderState playerRenderState, CallbackInfo ci) {
        HeldItemOutlineRenderer.endArmOccluderCapture();
    }

    @Inject(method = "renderMapHand", at = @At("HEAD"))
    private void itemglintrelight$beginMapArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                      HumanoidArm arm, PlayerRenderState playerRenderState, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginArmOccluderCapture(arm);
    }

    @Inject(method = "renderMapHand", at = @At("RETURN"))
    private void itemglintrelight$endMapArmOccluder(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                    HumanoidArm arm, PlayerRenderState playerRenderState, CallbackInfo ci) {
        HeldItemOutlineRenderer.endArmOccluderCapture();
    }
}
