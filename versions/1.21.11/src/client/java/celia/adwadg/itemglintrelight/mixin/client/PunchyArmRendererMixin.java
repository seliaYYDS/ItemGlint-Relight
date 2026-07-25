package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {
    private static final Method RESOLVE_RENDER_STACK = resolveRenderStackMethod();
    @ModifyVariable(method = "renderFirstPerson", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static SubmitNodeCollector itemglintrelight$wrapCollector(SubmitNodeCollector collector) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || collector == null) return collector;
        if (collector instanceof SubmitNodeStorage storage) return HeldItemOutlineRenderer.wrapStorage(minecraft, storage);
        return HeldItemOutlineRenderer.wrapCollector(minecraft, collector);
    }

    @Inject(method = "renderFirstPerson", at = @At("HEAD"))
    private static void itemglintrelight$beginPass(ItemInHandRenderer renderer, LocalPlayer player, float partialTick,
                                                   PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginCompatibilityHandPass();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private static void itemglintrelight$beginItem(ItemInHandRenderer renderer, PlayerModel playerModel, AvatarRenderState state,
                                                   LocalPlayer player, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector,
                                                   int light, Matrix4f root, ModelPart geoArm, ModelPart geoItem, ModelPart geoGrip,
                                                   float partialTick, CallbackInfo ci) {
        if (player == null || arm == null) return;
        InteractionHand hand = player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = resolveRenderedStack(player, arm, hand == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem());
        HeldItemOutlineRenderer.beginExternalHandSubmission(hand, stack, poseStack);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private static void itemglintrelight$endItem(ItemInHandRenderer renderer, PlayerModel playerModel, AvatarRenderState state,
                                                 LocalPlayer player, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector,
                                                 int light, Matrix4f root, ModelPart geoArm, ModelPart geoItem, ModelPart geoGrip,
                                                 float partialTick, CallbackInfo ci) {
        HeldItemOutlineRenderer.endExternalHandSubmission();
    }

    @Inject(method = "renderArm", at = @At("HEAD"))
    private static void itemglintrelight$beginArmOccluder(PlayerModel playerModel, AbstractClientPlayer player, HumanoidArm arm,
                                                          PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                          net.minecraft.resources.Identifier texture, boolean slim, float partialTick,
                                                          CallbackInfo ci) {
        HeldItemOutlineRenderer.beginArmOccluderCapture(poseStack);
    }

    @Inject(method = "renderArm", at = @At("RETURN"))
    private static void itemglintrelight$endArmOccluder(PlayerModel playerModel, AbstractClientPlayer player, HumanoidArm arm,
                                                        PoseStack poseStack, SubmitNodeCollector collector, int light,
                                                        net.minecraft.resources.Identifier texture, boolean slim, float partialTick,
                                                        CallbackInfo ci) {
        HeldItemOutlineRenderer.endArmOccluderCapture();
    }

    private static ItemStack resolveRenderedStack(LocalPlayer player, HumanoidArm arm, ItemStack stack) {
        if (RESOLVE_RENDER_STACK == null) return stack;
        try {
            Object resolved = RESOLVE_RENDER_STACK.invoke(null, player, arm, stack);
            return resolved instanceof ItemStack itemStack ? itemStack : stack;
        } catch (ReflectiveOperationException exception) {
            return stack;
        }
    }

    private static Method resolveRenderStackMethod() {
        try {
            return Class.forName("punchy.client.animation.PunchyAnimationManager").getMethod("resolveRenderStack",
                    net.minecraft.world.entity.player.Player.class, HumanoidArm.class, ItemStack.class);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
