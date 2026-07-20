package celia.adwadg.itemglintrelight.client.ui;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiSlider implements UiComponent {
    private int x;
    private int y;
    private final int width;
    private final String label;
    private final DoubleSupplier value;
    private final Consumer<Double> changeListener;
    private final double minimum;
    private final double maximum;
    private final double step;
    private float displayedValue;
    private float hoverAmount;
    private boolean dragging;
    private long lastFrame = System.nanoTime();

    public UiSlider(int x, int y, int width, String label, double minimum, double maximum, double step,
                    DoubleSupplier value, Consumer<Double> changeListener) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.value = value;
        this.changeListener = changeListener;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        displayedValue = normalized(value.getAsDouble());
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        float delta = deltaSeconds();
        displayedValue = UiMath.approach(displayedValue, normalized(value.getAsDouble()), delta, 18.0F);
        hoverAmount = UiMath.approach(hoverAmount, UiMath.contains(x, y, width, 28, mouseX, mouseY) ? 1.0F : 0.0F, delta, 11.0F);
        SmoothTextRenderer.draw(graphics, font, label, x,
                y + (12 - SmoothTextRenderer.height(label, 0.76F, UiPalette.TEXT)) / 2.0F, 0.76F, UiPalette.TEXT);
        String displayValue = formatValue(value.getAsDouble());
        SmoothTextRenderer.draw(graphics, font, displayValue, x + width - SmoothTextRenderer.width(displayValue, 0.66F, UiPalette.MUTED_TEXT),
                y + (12 - SmoothTextRenderer.height(displayValue, 0.66F, UiPalette.MUTED_TEXT)) / 2.0F, 0.66F, UiPalette.MUTED_TEXT);
        int trackY = y + 17;
        int trackHeight = 10;
        graphics.fill(x, trackY, x + width, trackY + trackHeight, UiMath.mix(UiPalette.DIVIDER, UiPalette.PALE_BLUE, hoverAmount));
        graphics.fill(x + 1, trackY + 1, x + width - 1, trackY + trackHeight - 1, UiPalette.SURFACE);
        int filled = Math.round((width - 2) * displayedValue);
        float phase = (System.nanoTime() % 1_600_000_000L) / 1_600_000_000.0F;
        for (int offset = 0; offset < filled; offset += 2) {
            int segmentWidth = Math.min(2, filled - offset);
            graphics.fill(x + 1 + offset, trackY + 1, x + 1 + offset + segmentWidth, trackY + trackHeight - 1,
                    gradientColor(offset / (float) Math.max(1, width - 2) + phase));
        }
        int markerX = x + 1 + filled;
        graphics.fill(markerX, trackY - 2, markerX + 1, trackY + trackHeight + 2, UiMath.mix(UiPalette.PALE_BLUE, UiPalette.LIGHT_GREEN, hoverAmount));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !UiMath.contains(x, y, width, 28, mouseX, mouseY)) {
            return false;
        }
        dragging = true;
        updateValue(mouseX);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!dragging || button != 0) {
            return false;
        }
        updateValue(mouseX);
        return true;
    }

    public void stopDragging() {
        dragging = false;
    }

    private void updateValue(double mouseX) {
        double normalized = Math.max(0.0D, Math.min(1.0D, (mouseX - x) / width));
        double rawValue = minimum + normalized * (maximum - minimum);
        double snapped = Math.round((rawValue - minimum) / step) * step + minimum;
        changeListener.accept(Math.max(minimum, Math.min(maximum, snapped)));
    }

    private float normalized(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D, (value - minimum) / (maximum - minimum)));
    }

    private String formatValue(double value) {
        if (step >= 1.0D) {
            return Integer.toString((int) Math.round(value));
        }
        if (step >= 0.1D) {
            return String.format(java.util.Locale.ROOT, "%.1f", value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static int gradientColor(float position) {
        float wrapped = position - (float) Math.floor(position);
        if (wrapped < 0.5F) {
            return UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, wrapped * 2.0F);
        }
        return UiMath.mix(UiPalette.LIGHT_GREEN, UiPalette.BRIGHT_BLUE, (wrapped - 0.5F) * 2.0F);
    }

    private float deltaSeconds() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastFrame) / 1_000_000_000.0F);
        lastFrame = now;
        return delta;
    }
}
