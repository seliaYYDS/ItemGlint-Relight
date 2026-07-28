package celia.adwadg.itemglintrelight.client.ui;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class UiDropdown implements UiComponent {
    private int x;
    private int y;
    private final int width;
    private final String label;
    private final List<String> options;
    private final Consumer<Integer> changeListener;
    private int selected;
    private boolean expanded;
    private float expansion;
    private float fieldHover;
    private float renderOpacity = 1.0F;
    private final float[] optionHovers;
    private long lastFrame = System.nanoTime();

    public UiDropdown(int x, int y, int width, String label, List<String> options, int selected, Consumer<Integer> changeListener) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.label = label;
        this.options = List.copyOf(options);
        this.selected = selected;
        this.changeListener = changeListener;
        this.optionHovers = new float[this.options.size()];
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int expandedHeight() {
        return Math.round(options.size() * 22.0F * expansion);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setSelected(int selected) {
        this.selected = Math.max(0, Math.min(options.size() - 1, selected));
        expanded = false;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        float delta = deltaSeconds();
        expansion = UiMath.approach(expansion, expanded ? 1.0F : 0.0F, delta, 18.0F);
        if (!label.isEmpty()) {
            SmoothTextRenderer.draw(graphics, font, label, x, y, 0.76F, color(UiPalette.MUTED_TEXT), renderOpacity);
        }
        int fieldY = y + 13;
        boolean hovered = UiMath.contains(x, fieldY, width, 22, mouseX, mouseY);
        fieldHover = UiMath.approach(fieldHover, hovered ? 1.0F : 0.0F, delta, 10.0F);
        graphics.fill(x, fieldY, x + width, fieldY + 22, color(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, fieldHover)));
        graphics.fill(x + 1, fieldY + 1, x + width - 1, fieldY + 21, color(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, fieldHover)));
        String selectedText = options.get(selected);
        SmoothTextRenderer.draw(graphics, font, selectedText, x + 8,
                fieldY + (22 - SmoothTextRenderer.height(selectedText, 0.82F, UiPalette.TEXT)) / 2.0F, 0.82F, color(UiPalette.TEXT), renderOpacity);
        String expandSymbol = expanded ? "-" : "+";
        SmoothTextRenderer.draw(graphics, font, expandSymbol, x + width - 14,
                fieldY + (22 - SmoothTextRenderer.height(expandSymbol, 0.82F, UiPalette.BRIGHT_BLUE)) / 2.0F, 0.82F, color(UiPalette.BRIGHT_BLUE), renderOpacity);
        int visibleHeight = Math.round(options.size() * 22.0F * expansion);
        if (visibleHeight > 0) {
            graphics.fill(x, fieldY + 23, x + width, fieldY + 23 + visibleHeight, color(UiPalette.BRIGHT_BLUE));
            graphics.fill(x + 1, fieldY + 24, x + width - 1, fieldY + 22 + visibleHeight, color(UiPalette.DEEP_BLUE_FADE));
            if (!expanded) {
                return;
            }
            for (int index = 0; index < options.size(); index++) {
                int optionY = fieldY + 23 + index * 22;
                int visibleBottom = fieldY + 23 + visibleHeight;
                if (optionY >= visibleBottom) {
                    break;
                }
                boolean optionHovered = UiMath.contains(x, optionY, width, 22, mouseX, mouseY);
                optionHovers[index] = UiMath.approach(optionHovers[index], optionHovered ? 1.0F : 0.0F, delta, 10.0F);
                graphics.fill(x + 1, optionY, x + width - 1, optionY + 22, color(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, optionHovers[index])));
                graphics.fill(x + 2, optionY + 1, x + width - 2, optionY + 21, color(UiMath.mix(UiPalette.DEEP_BLUE_FADE, UiPalette.SURFACE_HOVER, optionHovers[index])));
                String option = options.get(index);
                float reveal = Math.min(1.0F, (visibleBottom - optionY) / 22.0F);
                if (reveal >= 0.2F) {
                    SmoothTextRenderer.draw(graphics, font, option, x + 8,
                            optionY + (22 - SmoothTextRenderer.height(option, 0.78F, index == selected ? UiPalette.LIGHT_GREEN : UiPalette.TEXT)) / 2.0F,
                            0.78F, color(index == selected ? UiPalette.LIGHT_GREEN : UiPalette.TEXT), renderOpacity);
                }
            }
        }
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float opacity) {
        float previousOpacity = renderOpacity;
        renderOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        render(graphics, font, mouseX, mouseY);
        renderOpacity = previousOpacity;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int fieldY = y + 13;
        if (UiMath.contains(x, fieldY, width, 22, mouseX, mouseY)) {
            expanded = !expanded;
            return true;
        }
        if (expanded) {
            for (int index = 0; index < options.size(); index++) {
                int optionY = fieldY + 23 + index * 22;
                if (UiMath.contains(x, optionY, width, 22, mouseX, mouseY)) {
                    selected = index;
                    expanded = false;
                    changeListener.accept(index);
                    return true;
                }
            }
            expanded = false;
        }
        return false;
    }

    private float deltaSeconds() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastFrame) / 1_000_000_000.0F);
        lastFrame = now;
        return delta;
    }

    private int color(int color) {
        int alpha = Math.round((color >>> 24) * renderOpacity);
        return alpha << 24 | (color & 0x00FFFFFF);
    }
}
