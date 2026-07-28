package celia.adwadg.itemglintrelight.client.ui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiCyclingSelector {
    private int x;
    private int y;
    private final int width;
    private final List<String> options;
    private final Consumer<Integer> changeListener;
    private final float[] optionHovers;
    private int selected;
    private int previous;
    private int direction = 1;
    private boolean expanded;
    private float selectionTransition = 1.0F;
    private float expansion;
    private float fieldHover;
    private float previousArrowHover;
    private float nextArrowHover;
    private float centerHover;
    private long lastFrame = System.nanoTime();

    public UiCyclingSelector(int x, int y, int width, List<String> options, int selected, Consumer<Integer> changeListener) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.options = List.copyOf(options);
        this.changeListener = changeListener;
        this.optionHovers = new float[options.size()];
        this.selected = clamp(selected);
        this.previous = this.selected;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSelected(int selected) {
        this.selected = clamp(selected);
        this.previous = this.selected;
        this.selectionTransition = 1.0F;
    }

    public int expandedHeight() {
        return Math.round(options.size() * 22.0F * expansion);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int fieldY = y + 12;
        if (expanded) {
            for (int index = 0; index < options.size(); index++) {
                if (UiMath.contains(x, fieldY + 23 + index * 22, width, 22, mouseX, mouseY)) {
                    select(index, index >= selected ? 1 : -1);
                    expanded = false;
                    return true;
                }
            }
        }
        if (UiMath.contains(x, fieldY, 24, 22, mouseX, mouseY)) {
            select(selected - 1, -1);
            return true;
        }
        if (UiMath.contains(x + width - 24, fieldY, 24, 22, mouseX, mouseY)) {
            select(selected + 1, 1);
            return true;
        }
        if (UiMath.contains(x + 24, fieldY, width - 48, 22, mouseX, mouseY)) {
            expanded = !expanded;
            return true;
        }
        return false;
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float opacity) {
        float delta = deltaSeconds();
        selectionTransition = UiMath.approach(selectionTransition, 1.0F, delta, 16.0F);
        expansion = UiMath.approach(expansion, expanded ? 1.0F : 0.0F, delta, 18.0F);
        int fieldY = y + 12;
        fieldHover = UiMath.approach(fieldHover, UiMath.contains(x, fieldY, width, 22, mouseX, mouseY) ? 1.0F : 0.0F, delta, 12.0F);
        previousArrowHover = UiMath.approach(previousArrowHover, UiMath.contains(x, fieldY, 24, 22, mouseX, mouseY) ? 1.0F : 0.0F, delta, 14.0F);
        nextArrowHover = UiMath.approach(nextArrowHover, UiMath.contains(x + width - 24, fieldY, 24, 22, mouseX, mouseY) ? 1.0F : 0.0F, delta, 14.0F);
        centerHover = UiMath.approach(centerHover, UiMath.contains(x + 24, fieldY, width - 48, 22, mouseX, mouseY) ? 1.0F : 0.0F, delta, 14.0F);
        fill(graphics, x, fieldY, x + width, fieldY + 22, UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, fieldHover), opacity);
        fill(graphics, x + 1, fieldY + 1, x + width - 1, fieldY + 21, UiPalette.SURFACE, opacity);
        fill(graphics, x + 1, fieldY + 1, x + 23, fieldY + 21, UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, previousArrowHover), opacity);
        fill(graphics, x + width - 23, fieldY + 1, x + width - 1, fieldY + 21, UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, nextArrowHover), opacity);
        fill(graphics, x + 24, fieldY + 1, x + width - 24, fieldY + 21, UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, centerHover), opacity);
        drawCentered(graphics, font, "<", x + 12.0F, fieldY, UiMath.mix(UiPalette.PALE_BLUE, UiPalette.LIGHT_GREEN, previousArrowHover), opacity);
        drawCentered(graphics, font, ">", x + width - 12.0F, fieldY, UiMath.mix(UiPalette.PALE_BLUE, UiPalette.LIGHT_GREEN, nextArrowHover), opacity);
        if (selectionTransition < 0.995F) drawCentered(graphics, font, options.get(previous), x + width * 0.5F - direction * selectionTransition * 22.0F, fieldY, UiPalette.TEXT, opacity * (1.0F - selectionTransition));
        drawCentered(graphics, font, options.get(selected), x + width * 0.5F + direction * (1.0F - selectionTransition) * 22.0F, fieldY, UiPalette.TEXT, opacity);
        int optionHeight = expandedHeight();
        if (optionHeight <= 0) return;
        int optionsY = fieldY + 23;
        fill(graphics, x, optionsY, x + width, optionsY + optionHeight, UiPalette.BRIGHT_BLUE, opacity);
        fill(graphics, x + 1, optionsY + 1, x + width - 1, optionsY + optionHeight - 1, UiPalette.DEEP_BLUE_FADE, opacity);
        fill(graphics, x, optionsY + optionHeight - 1, x + width, optionsY + optionHeight, UiPalette.BRIGHT_BLUE, opacity);
        for (int index = 0; index < options.size(); index++) {
            int optionY = optionsY + index * 22;
            if (optionY >= optionsY + optionHeight) break;
            optionHovers[index] = UiMath.approach(optionHovers[index], UiMath.contains(x, optionY, width, 22, mouseX, mouseY) ? 1.0F : 0.0F, delta, 12.0F);
            fill(graphics, x + 1, optionY, x + width - 1, optionY + 22, UiMath.mix(UiPalette.DEEP_BLUE_FADE, UiPalette.SURFACE_HOVER, optionHovers[index]), opacity);
            drawCentered(graphics, font, options.get(index), x + width * 0.5F, optionY, index == selected ? UiPalette.LIGHT_GREEN : UiPalette.TEXT, opacity);
        }
    }

    private void select(int next, int direction) {
        int clamped = clamp(next);
        if (clamped == selected) return;
        previous = selected;
        selected = clamped;
        this.direction = direction;
        selectionTransition = 0.0F;
        changeListener.accept(selected);
    }

    private int clamp(int value) { return Math.max(0, Math.min(options.size() - 1, value)); }
    private float deltaSeconds() { long now = System.nanoTime(); float delta = Math.min(0.05F, (now - lastFrame) / 1_000_000_000.0F); lastFrame = now; return delta; }
    private void drawCentered(GuiGraphics graphics, Font font, String text, float centerX, int y, int color, float opacity) { SmoothTextRenderer.drawCentered(graphics, font, text, centerX, y + (22 - SmoothTextRenderer.height(text, 0.68F, color)) * 0.5F, 0.68F, color, opacity); }
    private void fill(GuiGraphics graphics, int left, int top, int right, int bottom, int color, float opacity) { int alpha = Math.round((color >>> 24) * opacity); graphics.fill(left, top, right, bottom, alpha << 24 | color & 0x00FFFFFF); }
}
