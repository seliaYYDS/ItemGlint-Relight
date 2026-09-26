package celia.adwadg.itemglintrelight.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface UiComponent {
    void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY);

    boolean mouseClicked(double mouseX, double mouseY, int button);
}
