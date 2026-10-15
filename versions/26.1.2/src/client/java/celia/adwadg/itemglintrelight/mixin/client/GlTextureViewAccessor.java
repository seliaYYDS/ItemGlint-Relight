package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 26.1.2 still exposes framebuffer lookup on the view: {@code GlTextureView#getFbo(DirectStateAccess,
 * GpuTexture)}. (26.2 removed it and moved the lookup into a per-texture {@code FrameBufferCache}.)
 * The invoker is used rather than reflection so the method survives production obfuscation.
 */
@Mixin(GlTextureView.class)
public interface GlTextureViewAccessor {
    @Invoker("getFbo")
    int itemglintrelight$getFbo(DirectStateAccess directStateAccess, GpuTexture depthTexture);
}
