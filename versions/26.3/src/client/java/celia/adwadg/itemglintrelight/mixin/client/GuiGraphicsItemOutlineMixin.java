package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Captures GUI items so {@link GuiItemOutlineManager} can outline them.
 *
 * <p>26.2 renamed {@code renderItem} to {@code item} and moved the real work into the private
 * six-argument overload that takes a {@code LivingEntity} and a {@code Level}; every public
 * {@code item(...)} overload funnels through it, so one injection covers inventory slots, tooltips and
 * every other GUI item. Its local {@code itemStackRenderState} is the {@code TrackingItemStackRenderState}
 * the manager keys captures on.
 *
 * <p>The 1.21.11 build also bracketed the tooltip render ({@code beginTooltipRender} /
 * {@code endTooltipRender}) because 26.2 replaced {@code GuiGraphicsExtractor#renderTooltip} with the
 * {@code setTooltipForNextFrame} family plus a deferred runnable; the tooltip's item is drawn through
 * the same private {@code item(...)} method, so it is captured like any other GUI item now. That only
 * means tooltip item previews get an outline too.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsItemOutlineMixin {
    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V", at = @At("HEAD"))
    private void itemglintrelight$tooltipScheduled(Component component, int x, int y, CallbackInfo ci) {
        GuiItemOutlineManager.tooltipScheduled(x, y);
    }

    @Inject(
            method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("TAIL"),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void itemglintrelight$captureGuiItem(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed,
                                                 CallbackInfo ci, TrackingItemStackRenderState trackingItemStackRenderState) {
        GuiItemOutlineManager.capture(stack, trackingItemStackRenderState);
    }
}
