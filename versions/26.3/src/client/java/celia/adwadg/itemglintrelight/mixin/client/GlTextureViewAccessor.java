package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
import com.mojang.renderpearl.backend.opengl.GlTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlTextureView.class)
public interface GlTextureViewAccessor {
    /**
     * 26.2 removed {@code GlTextureView#getFbo}; framebuffer lookup now lives in a shared
     * {@link FrameBufferCache} held by every view of the same texture. Vanilla reaches it exactly
     * this way (see the framebuffer bind in {@code GlCommandEncoder}), so the accessor replaces the
     * old invoker with the same resulting FBO id.
     */
    @Accessor("frameBufferCache")
    FrameBufferCache itemglintrelight$getFrameBufferCache();
}
