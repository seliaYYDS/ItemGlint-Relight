package celia.adwadg.itemglintrelight.mixin.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemStackLayerRenderStateAccessor {
    @Accessor("quads")
    List<BakedQuad> itemglintrelight$getQuads();

    @Accessor("tintLayers")
    int[] itemglintrelight$getTintLayers();

    @Accessor("particleIcon")
    TextureAtlasSprite itemglintrelight$getParticleIcon();
}
