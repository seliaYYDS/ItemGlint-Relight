package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @ModifyVariable(method = "renderHandsWithItems", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private SubmitNodeCollector itemglintrelight$captureHandNodes(SubmitNodeCollector collector) {
        if (collector instanceof SubmitNodeStorage storage) {
            return HeldItemOutlineRenderer.wrapStorage(Minecraft.getInstance(), storage);
        }
        return collector;
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void itemglintrelight$beginHand(AbstractClientPlayer player, float partialTick, float pitch,
                                             InteractionHand hand, float swing, ItemStack stack, float equip,
                                             PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginHand(hand, stack);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void itemglintrelight$endHand(AbstractClientPlayer player, float partialTick, float pitch,
                                           InteractionHand hand, float swing, ItemStack stack, float equip,
                                           PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.endHand();
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void itemglintrelight$beginItemSubmission(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                                       PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginItemSubmission(displayContext);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void itemglintrelight$endItemSubmission(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext,
                                                     PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.endItemSubmission(displayContext);
    }
}
