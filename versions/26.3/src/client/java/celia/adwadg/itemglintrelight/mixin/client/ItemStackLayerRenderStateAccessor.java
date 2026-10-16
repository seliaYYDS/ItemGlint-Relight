package celia.adwadg.itemglintrelight.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.sprite.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemStackLayerRenderStateAccessor {
    @Accessor("quads")
    ItemQuads itemglintrelight$getQuads();

    /** 26.2 replaced the bare {@code particleIcon} sprite with a baked material. */
    @Accessor("particleMaterial")
    Material.Baked itemglintrelight$getParticleMaterial();
}
