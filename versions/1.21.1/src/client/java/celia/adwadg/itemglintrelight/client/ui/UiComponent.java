package celia.adwadg.itemglintrelight.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public interface UiComponent {
    void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    boolean mouseClicked(double mouseX, double mouseY, int button);
}
