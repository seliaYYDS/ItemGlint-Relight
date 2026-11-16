package celia.adwadg.itemglintrelight.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;

/**
 * The GL state the GUI item outline pass has to undo.
 *
 * <p>1.21.1 keeps every piece of render state in global GL state, so the offscreen mask / composite /
 * blit path has to restore what it changes. Only the scissor box is actually queried: it differs per
 * slot (container grids, scrolled lists) and cannot be derived. Everything else is restored to the
 * state the GUI pass is in at {@code GuiGraphics#renderItem} time, which {@code GuiGraphics#flush()}
 * (called right before the capture) has already normalised -- querying those values per slot cost ten
 * driver round-trips per item and made large inventories stutter.
 */
final class GuiGlState {
    private final boolean scissorEnabled;
    private final int[] scissorBox = new int[4];

    private GuiGlState() {
        this.scissorEnabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, this.scissorBox);
    }

    static GuiGlState capture() {
        return new GuiGlState();
    }

    /** Restores the GUI scissor exactly; used right before the outline is composited back. */
    void restoreScissor() {
        if (this.scissorEnabled) {
            RenderSystem.enableScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
        } else {
            RenderSystem.disableScissor();
        }
    }

    void restore() {
        restoreScissor();
        RenderSystem.enableCull();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
