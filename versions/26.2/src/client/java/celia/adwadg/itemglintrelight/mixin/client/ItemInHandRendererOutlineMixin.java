package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.FirstPersonOutlineState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the first-person hand window. The held item is submitted inside {@code submitArmWithItem},
 * so a flag raised here scopes the outline colour override to the first-person hand only.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererOutlineMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void itemglintrelight$beginFirstPersonOutline(AbstractClientPlayer player, float partialTick, float pitch,
                                                           InteractionHand hand, float swing, ItemStack stack,
                                                           float equip, PoseStack poseStack,
                                                           SubmitNodeCollector collector, int light, CallbackInfo ci) {
        FirstPersonOutlineState.enter(hand, stack);
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void itemglintrelight$endFirstPersonOutline(AbstractClientPlayer player, float partialTick, float pitch,
                                                         InteractionHand hand, float swing, ItemStack stack,
                                                         float equip, PoseStack poseStack,
                                                         SubmitNodeCollector collector, int light, CallbackInfo ci) {
        FirstPersonOutlineState.exit();
    }
}
