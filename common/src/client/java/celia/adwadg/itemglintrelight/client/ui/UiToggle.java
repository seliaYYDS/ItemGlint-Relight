package celia.adwadg.itemglintrelight.client.ui;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiToggle implements UiComponent {
    private int x;
    private int y;
    private final int width;
    private final String label;
    private final BooleanSupplier value;
    private final Consumer<Boolean> changeListener;
    private float position;
    private float hoverAmount;
    private long lastFrame = System.nanoTime();

    public UiToggle(int x, int y, int width, String label, BooleanSupplier value, Consumer<Boolean> changeListener) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.value = value;
        this.changeListener = changeListener;
        position = value.getAsBoolean() ? 1.0F : 0.0F;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        float delta = deltaSeconds();
        position = UiMath.approach(position, value.getAsBoolean() ? 1.0F : 0.0F, delta, 18.0F);
        hoverAmount = UiMath.approach(hoverAmount, UiMath.contains(x, y, width, 20, mouseX, mouseY) ? 1.0F : 0.0F, delta, 10.0F);
        SmoothTextRenderer.draw(graphics, font, label, x,
                y + (18 - SmoothTextRenderer.height(label, 0.88F, UiPalette.TEXT)) / 2.0F, 0.88F, UiPalette.TEXT);
        int trackX = x + width - 42;
        int trackY = y;
        int trackColor = UiMath.mix(UiPalette.INK_GREEN, UiPalette.BRIGHT_BLUE, position);
        UiShapes.roundedOutline(graphics, trackX, trackY, trackX + 42, trackY + 18, 9,
                UiMath.mix(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, hoverAmount), UiPalette.LIGHT_GREEN, position),
                UiMath.mix(trackColor, UiPalette.SURFACE_HOVER, hoverAmount * 0.35F));
        int knobX = Math.round(trackX + 3 + position * 20.0F);
        UiShapes.roundedRect(graphics, knobX, trackY + 3, knobX + 16, trackY + 15, 6, UiPalette.TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && UiMath.contains(x, y, width, 20, mouseX, mouseY)) {
            changeListener.accept(!value.getAsBoolean());
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
