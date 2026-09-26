package celia.adwadg.itemglintrelight.mixin.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemStackLayerRenderStateAccessor {
    @Accessor("quads")
    List<BakedQuad> itemglintrelight$getQuads();

    /** 26.2 stores the per-layer tints in an {@code IntList} instead of a raw {@code int[]}. */
    @Accessor("tintLayers")
    IntList itemglintrelight$getTintLayers();

    /** 26.2 replaced the bare {@code particleIcon} sprite with a baked material. */
    @Accessor("particleMaterial")
    Material.Baked itemglintrelight$getParticleMaterial();
}
