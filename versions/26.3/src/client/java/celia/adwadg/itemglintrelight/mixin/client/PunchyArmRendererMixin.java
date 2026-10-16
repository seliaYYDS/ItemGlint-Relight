package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlinePunchyCompat;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Punchy bridge.
 * <p>Punchy re-implements the first-person pass: it draws the arm and the held item through its own
 * {@code renderFirstPerson} / {@code renderArm} / {@code renderItemInHand} methods instead of the
 * vanilla ones this mod hooks into, and it cancels every vanilla {@code renderArmWithItem}. Without
 * this bridge the item nodes would never reach a capture target, so no outline would appear at all.
 * <p>{@code renderItemInHand} is also the window in which Punchy draws the special in-hand items
 * (boats, minecarts, chests, block entities, bedrock geometry, maps) through their own renderers
 * instead of {@code ItemInHandRenderer.renderItem}. That is why the window is opened as an
 * <em>external</em> submission rather than a plain hand window: the capture has to accept every node
 * submitted while Punchy owns the hand, not only the nodes that pass through {@code renderItem}.
 * <p>The class only exists while Punchy is installed, which is what the mixin plugin checks, so the
 * targets are resolved by name and left unremapped.
 *
 * <p>Verified against the real Punchy artifacts for 26.2 (`punchy-fpa` 2.5.6 and 2.8b, Modrinth),
 * whose jar is built with unobfuscated Mojang names, so the descriptors compare directly with
 * `javap -p -s punchy.client.render.PunchyArmRenderer`:
 *
 * <ul>
 *   <li>{@code renderFirstPerson(ItemInHandRenderer, LocalPlayer, float, PoseStack,
 *       SubmitNodeCollector, int)} -- the collector is the only argument of its type, so the
 *       {@code argsOnly} {@code @ModifyVariable} at ordinal 0 still selects it, whatever the other
 *       parameters are;</li>
 *   <li>{@code renderArm(PlayerModel, AbstractClientPlayer, HumanoidArm, PoseStack,
 *       SubmitNodeCollector, int, Identifier, boolean, float)} -- matches the handler exactly;</li>
 *   <li>{@code renderItemInHand(ItemInHandRenderer, PlayerModel, AvatarRenderState, LocalPlayer,
 *       HumanoidArm, PoseStack, SubmitNodeCollector, int, Matrix4f, ModelPart, ModelPart, ModelPart,
 *       float)} -- matches the handler exactly.</li>
 * </ul>
 *
 * <p>Each name is declared exactly once in the class, so the name-only selectors resolve
 * unambiguously, and `renderFirstPerson` really does call both entry points. Both verified 26.2
 * builds agree, so no Punchy version gate is needed.
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer", remap = false)
public abstract class PunchyArmRendererMixin {
    /**
     * Punchy may build its own submit storage for the pass. Wrapping the collector it is handed is
     * the only place that sees every node it submits, and the wrap is idempotent: a collector that is
     * already mirrored is returned untouched, so the vanilla
     * {@code ItemInHandRenderer.renderHandsWithItems} wrap and this one never nest.
     */
    @ModifyVariable(method = "renderFirstPerson", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static SubmitNodeCollector itemglintrelight$wrapPunchyCollector(SubmitNodeCollector submitNodeCollector) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || submitNodeCollector == null) {
            return submitNodeCollector;
        }
        if (submitNodeCollector instanceof SubmitNodeStorage submitNodeStorage) {
            return HeldItemOutlineRenderer.wrapStorage(minecraft, submitNodeStorage);
        }
        return HeldItemOutlineRenderer.wrapCollector(minecraft, submitNodeCollector);
    }

    /**
     * The vanilla arm entry points never run under Punchy, so the arm-occluder window opens here.
     * Without it the arm would never reach the occluder mask and the outline would stay visible on
     * top of it.
     */
    @Inject(method = "renderArm", at = @At("HEAD"))
    private static void itemglintrelight$beginPunchyArmOccluder(PlayerModel playerModel, AbstractClientPlayer player,
                                                                 HumanoidArm arm, PoseStack poseStack,
                                                                 SubmitNodeCollector submitNodeCollector, int combinedLight,
                                                                 Identifier texture, boolean slim, float partialTicks,
                                                                 CallbackInfo ci) {
        HeldItemOutlineRenderer.beginArmOccluderCapture(arm);
    }

    @Inject(method = "renderArm", at = @At("RETURN"))
    private static void itemglintrelight$endPunchyArmOccluder(PlayerModel playerModel, AbstractClientPlayer player,
                                                              HumanoidArm arm, PoseStack poseStack,
                                                              SubmitNodeCollector submitNodeCollector, int combinedLight,
                                                              Identifier texture, boolean slim, float partialTicks,
                                                              CallbackInfo ci) {
        HeldItemOutlineRenderer.endArmOccluderCapture();
    }

    /**
     * Punchy's own item entry point replaces {@code renderArmWithItem}. The window is opened with the
     * stack Punchy is actually about to draw, which is not necessarily the live hand item.
     */
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private static void itemglintrelight$beginPunchyHand(FirstPersonHandsAndItemsRenderer handRenderer, PlayerModel playerModel,
                                                         AvatarRenderState renderState, LocalPlayer player, HumanoidArm arm,
                                                         PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                                         int combinedLight, Matrix4f rootWorld, ModelPart geoArm,
                                                         ModelPart geoItem, ModelPart geoGrip, float partialTicks,
                                                         CallbackInfo ci) {
        InteractionHand hand = resolveHand(player, arm);
        if (hand == null) {
            return;
        }
        HeldItemOutlineRenderer.beginExternalHandSubmission(
                hand, HeldItemOutlinePunchyCompat.resolveRenderedHandStack(player, hand), poseStack);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private static void itemglintrelight$endPunchyHand(FirstPersonHandsAndItemsRenderer handRenderer, PlayerModel playerModel,
                                                       AvatarRenderState renderState, LocalPlayer player, HumanoidArm arm,
                                                       PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                                       int combinedLight, Matrix4f rootWorld, ModelPart geoArm,
                                                       ModelPart geoItem, ModelPart geoGrip, float partialTicks,
                                                       CallbackInfo ci) {
        HeldItemOutlineRenderer.endExternalHandSubmission();
    }

    private static InteractionHand resolveHand(LocalPlayer player, HumanoidArm arm) {
        if (player == null || arm == null) {
            return null;
        }
        return player.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
