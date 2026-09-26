package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderer;
import celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderState;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers the config screen's item preview with the GUI picture-in-picture renderer set.
 *
 * <p>26.2 builds {@code pictureInPictureRenderers} as an immutable map inside the GuiRenderer
 * constructor, so it is copied into a mutable map before the extra renderer is added.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererPreviewMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void itemglintrelight$registerPreviewRenderer(CallbackInfo ci) {
        pictureInPictureRenderers = new HashMap<>(pictureInPictureRenderers);
        pictureInPictureRenderers.put(ItemPreviewRenderState.class, new ItemPreviewRenderer());
    }
}
