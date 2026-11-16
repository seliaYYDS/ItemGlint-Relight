package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsItemOutlineMixin {
    /**
     * 1.21.1 has one funnel for every GUI item: the private seven-argument overload. Hooking it covers
     * container slots, the carried stack, {@code renderFakeItem} and creative-tab grids at once.
     */
    @Inject(
            method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At("HEAD")
    )
    private void itemglintrelight$guiItemOutline(LivingEntity entity, Level level, ItemStack stack, int x, int y,
                                                 int seed, int zOffset, CallbackInfo ci) {
        GuiItemOutlineManager.renderBeforeItem((GuiGraphics) (Object) this, entity, level, stack, x, y, seed);
    }

    /**
     * Bundle and shulker tooltips draw their contents through {@code renderItem}; the reference skips
     * GUI outlines for the whole tooltip pass, and so does this.
     */
    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void itemglintrelight$beginTooltip(Font font, List<ClientTooltipComponent> components, int x, int y,
                                               ClientTooltipPositioner positioner, CallbackInfo ci) {
        GuiItemOutlineManager.beginTooltipRender();
    }

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void itemglintrelight$endTooltip(Font font, List<ClientTooltipComponent> components, int x, int y,
                                             ClientTooltipPositioner positioner, CallbackInfo ci) {
        GuiItemOutlineManager.endTooltipRender();
    }
}
