package celia.adwadg.itemglintrelight.mixin.client;

import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.21.1's {@code ItemRenderer} keeps its {@code ItemColors} private and exposes no getter. */
@Mixin(ItemRenderer.class)
public interface ItemRendererAccessor {
    @Accessor("itemColors")
    ItemColors itemglintrelight$getItemColors();
}
