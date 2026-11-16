package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the mip chain so the texture-sample palette can read sprite pixels (same field name in 1.21.1). */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("byMipLevel")
    NativeImage[] itemglintrelight$getByMipLevel();
}
