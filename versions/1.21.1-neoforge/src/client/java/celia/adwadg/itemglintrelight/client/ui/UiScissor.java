package celia.adwadg.itemglintrelight.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 1.21.1 {@code GuiGraphics#enableScissor} takes raw GUI coordinates; 1.21.11 transforms the
 * rectangle by the current pose ({@code ScreenRectangle#transformAxisAligned(Matrix3x2f)}) before
 * pushing it. Our layout draws inside {@code translate(origin) + scale(uiScale)}, so passing layout
 * coordinates straight to 1.21.1 clips at the wrong place. This helper reproduces the 1.21.11
 * behaviour.
 */
public final class UiScissor {
    private UiScissor() {
    }

    public static void enable(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        Matrix4f matrix = graphics.pose().last().pose();
        Vector4f first = matrix.transform(new Vector4f(x1, y1, 0.0F, 1.0F));
        Vector4f second = matrix.transform(new Vector4f(x2, y2, 0.0F, 1.0F));
        int left = Math.round(Math.min(first.x, second.x));
        int top = Math.round(Math.min(first.y, second.y));
        int right = Math.round(Math.max(first.x, second.x));
        int bottom = Math.round(Math.max(first.y, second.y));
        graphics.enableScissor(left, top, right, bottom);
    }

    public static void disable(GuiGraphics graphics) {
        graphics.disableScissor();
    }
}
