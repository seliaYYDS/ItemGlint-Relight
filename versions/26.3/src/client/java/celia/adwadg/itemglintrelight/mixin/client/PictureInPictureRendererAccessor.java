package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the picture-in-picture target the preview draws into.
 *
 * <p>26.2 published that target by setting {@code RenderSystem.outputColorTextureOverride} before it
 * called {@code renderToTexture}. 26.3 keeps both views private to the base class and opens the render
 * pass itself once {@code renderToTexture} returns, so the preview has to name the views explicitly.
 */
@Mixin(PictureInPictureRenderer.class)
public interface PictureInPictureRendererAccessor {
    @Accessor("textureView")
    GpuTextureView itemglintrelight$getTextureView();

    @Accessor("depthTextureView")
    GpuTextureView itemglintrelight$getDepthTextureView();
}
