package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererItemOutlineMixin {
    @Shadow @Final private GuiRenderState renderState;

    @Inject(
            method = "submitBlitFromItemAtlas(Lnet/minecraft/client/gui/render/state/GuiItemRenderState;FFII)V",
            // Submit into the atlas blit's existing layer before the item itself. This preserves
            // vanilla GUI ordering instead of moving intersecting elements into a new stratum.
            at = @At("HEAD")
    )
    private void itemglintrelight$submitGuiItemOutline(GuiItemRenderState itemState, float u, float v, int itemSize,
                                                        int atlasSize, CallbackInfo ci) {
        GuiItemOutlineManager.submit(itemState, renderState);
    }
}
