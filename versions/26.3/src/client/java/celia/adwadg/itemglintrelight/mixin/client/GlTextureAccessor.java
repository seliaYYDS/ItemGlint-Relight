package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlTexture.class)
public interface GlTextureAccessor {
    @Invoker("glId")
    int itemglintrelight$getGlId();
}
