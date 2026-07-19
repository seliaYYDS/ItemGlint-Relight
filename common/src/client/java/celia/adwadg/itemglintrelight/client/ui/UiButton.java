package celia.adwadg.itemglintrelight.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiButton implements UiComponent {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String label;
    private final Runnable action;
    private float hoverAmount;
    private long lastFrame = System.nanoTime();

    public UiButton(int x, int y, int width, int height, String label, Runnable action) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.action = action;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        float delta = deltaSeconds();
        hoverAmount = UiMath.approach(hoverAmount, UiMath.contains(x, y, width, height, mouseX, mouseY) ? 1.0F : 0.0F, delta, 10.0F);
        graphics.fill(x, y, x + width, y + height, UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, hoverAmount));
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, UiMath.mix(UiPalette.INK_GREEN, UiPalette.SURFACE_HOVER, hoverAmount));
        SmoothTextRenderer.drawCentered(graphics, font, label, x + width / 2.0F,
                y + (height - SmoothTextRenderer.height(label, 0.86F, UiPalette.TEXT)) / 2.0F, 0.86F, UiPalette.TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && UiMath.contains(x, y, width, height, mouseX, mouseY)) {
            action.run();
            return true;
        }
        return false;
    }

    private float deltaSeconds() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastFrame) / 1_000_000_000.0F);
        lastFrame = now;
        return delta;
    }
}
