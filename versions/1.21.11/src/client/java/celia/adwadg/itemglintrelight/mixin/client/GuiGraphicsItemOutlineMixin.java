package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import java.util.List;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsItemOutlineMixin {
    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V", at = @At("HEAD"))
    private void itemglintrelight$tooltipScheduled(Component component, int x, int y, CallbackInfo ci) {
        GuiItemOutlineManager.tooltipScheduled(x, y);
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD")
    )
    private void itemglintrelight$beginTooltip(Font font, List<ClientTooltipComponent> components, int x, int y,
                                               ClientTooltipPositioner positioner, Identifier background, CallbackInfo ci) {
        GuiItemOutlineManager.beginTooltipRender();
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("RETURN")
    )
    private void itemglintrelight$endTooltip(Font font, List<ClientTooltipComponent> components, int x, int y,
                                             ClientTooltipPositioner positioner, Identifier background, CallbackInfo ci) {
        GuiItemOutlineManager.endTooltipRender();
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("TAIL"),
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void itemglintrelight$captureGuiItem(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed,
                                                   CallbackInfo ci, TrackingItemStackRenderState trackingItemStackRenderState) {
        GuiItemOutlineManager.capture(stack, trackingItemStackRenderState);
    }
}
