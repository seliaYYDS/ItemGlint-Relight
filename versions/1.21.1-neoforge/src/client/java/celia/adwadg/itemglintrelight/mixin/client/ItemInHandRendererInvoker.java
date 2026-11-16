package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private hand draw calls so the mask pass can replay them verbatim. */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererInvoker {
    @Invoker("renderPlayerArm")
    void itemglintrelight$renderPlayerArm(PoseStack poseStack, MultiBufferSource source, int light,
                                          float equipProgress, float swingProgress, HumanoidArm arm);

    @Invoker("renderMap")
    void itemglintrelight$renderMap(PoseStack poseStack, MultiBufferSource source, int light, ItemStack stack);

    @Invoker("renderMapHand")
    void itemglintrelight$renderMapHand(PoseStack poseStack, MultiBufferSource source, int light, HumanoidArm arm);
}
