package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlTextureView.class)
public interface GlTextureViewAccessor {
    @Invoker("getFbo")
    int itemglintrelight$getFbo(DirectStateAccess directStateAccess, GpuTexture depthTexture);
}
