package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.GuiItemOutlineManager;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Submits the GUI item outline at the one point where an item leaves the screen.
 *
 * <p>26.2 changed {@code submitBlitFromItemAtlas} from
 * {@code (GuiItemRenderState, float, float, int, int)} to
 * {@code (GuiItemRenderState, GuiItemAtlas.SlotView)}; the atlas slot replaces the loose u/v/size
 * arguments, and the manager only reads the render state, so the injection needs no other change.
 * Submitting into the atlas blit's existing layer before the item itself preserves vanilla GUI
 * ordering instead of moving intersecting elements into a new stratum.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererItemOutlineMixin {
    @Shadow @Final private GuiRenderState renderState;

    @Inject(
            method = "submitBlitFromItemAtlas(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;Lnet/minecraft/client/gui/render/GuiItemAtlas$SlotView;)V",
            at = @At("HEAD")
    )
    private void itemglintrelight$submitGuiItemOutline(GuiItemRenderState itemState, GuiItemAtlas.SlotView slotView, CallbackInfo ci) {
        GuiItemOutlineManager.submit(itemState, renderState);
    }
}
