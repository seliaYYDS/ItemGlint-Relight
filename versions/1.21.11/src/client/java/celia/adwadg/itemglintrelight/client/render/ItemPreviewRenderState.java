package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;

public record ItemPreviewRenderState(ItemStack item, int x0, int y0, int x1, int y1, float scale, float pitch, float yaw, float roll,
                                     float offsetX, float offsetY, float outlineScale,
                                     ItemGlintRelightConfig config, ScreenRectangle scissorArea) implements PictureInPictureRenderState {
    @Override
    public ScreenRectangle bounds() {
        return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
    }
}
