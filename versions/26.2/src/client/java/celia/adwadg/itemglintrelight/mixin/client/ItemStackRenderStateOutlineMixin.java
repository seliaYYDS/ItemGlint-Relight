package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.FirstPersonOutlineState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Replaces the outline colour of the held item while the first-person hand window is open.
 *
 * <p>{@code ItemStackRenderState#submit(PoseStack, SubmitNodeCollector, int light, int overlay,
 * int outlineColor)} -- local slot 5 is the outline colour. A non-zero value makes vanilla's item
 * feature renderer emit an outline submit.
 */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateOutlineMixin {
    @ModifyVariable(method = "submit", at = @At("HEAD"), argsOnly = true, index = 5)
    private int itemglintrelight$applyFirstPersonOutline(int outlineColor) {
        int custom = FirstPersonOutlineState.outlineColor();
        return custom != 0 ? custom : outlineColor;
    }
}
