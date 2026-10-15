package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderState;
import celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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
 * <p>26.1.2 builds {@code pictureInPictureRenderers} as an immutable map inside the GuiRenderer
 * constructor and still keeps the GUI buffer source as a field, so the mod's renderer is handed that
 * buffer source the way 1.21.11 did.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererPreviewMixin {
    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Shadow
    @Final
    @Mutable
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void itemglintrelight$registerPreviewRenderer(CallbackInfo ci) {
        pictureInPictureRenderers = new HashMap<>(pictureInPictureRenderers);
        pictureInPictureRenderers.put(ItemPreviewRenderState.class, new ItemPreviewRenderer(bufferSource));
    }
}
