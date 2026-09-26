package celia.adwadg.itemglintrelight.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TridentItem;
import org.joml.Quaternionf;

/**
 * Simple 3D item preview for the config screen.
 *
 * <p>26.2 note: this is the reduced port. The outline compositing pass is intentionally not
 * implemented yet -- it needs the feature / submit-node rewrite tracked as P1 in
 * {@code docs/26.2-port-todo.md}. Only the item itself is rendered here.
 */
public final class ItemPreviewRenderer extends PictureInPictureRenderer<ItemPreviewRenderState> {
    private long nextLogMillis;

    @Override
    public Class<ItemPreviewRenderState> getRenderStateClass() {
        return ItemPreviewRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "itemglintrelight_preview";
    }

    @Override
    protected void renderToTexture(ItemPreviewRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStackRenderState itemState = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(itemState, state.item(), previewDisplayContext(state),
                minecraft.level, null, 0);

        long now = System.currentTimeMillis();
        if (now >= nextLogMillis) {
            nextLogMillis = now + 2000L;
            celia.adwadg.itemglintrelight.ItemGlintRelight.LOGGER.info(
                    "[DIAG] preview renderToTexture item={} empty={} inWorld={}",
                    state.item().getItem(), state.item().isEmpty(), minecraft.level != null);
        }

        float height = state.y1() - state.y0();
        pose.translate(state.offsetX() / state.scale(), (state.offsetY() - height * 0.5F) / state.scale(), 0.0F);
        pose.scale(1.0F, -1.0F, -1.0F);
        pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(state.pitch())));
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(state.yaw())));
        pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(state.roll())));
        minecraft.gameRenderer.lighting().setupFor(
                itemState.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        itemState.submit(pose, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
    }

    private static ItemDisplayContext previewDisplayContext(ItemPreviewRenderState state) {
        return state.item().getItem() instanceof ShieldItem
                || state.item().getItem() instanceof TridentItem
                || state.item().getItem() instanceof BannerItem
                || state.item().getItem() instanceof SpyglassItem
                ? ItemDisplayContext.FIXED : ItemDisplayContext.GUI;
    }
}
