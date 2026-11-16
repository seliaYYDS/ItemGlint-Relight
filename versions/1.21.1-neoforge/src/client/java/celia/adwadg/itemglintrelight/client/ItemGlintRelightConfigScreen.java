package celia.adwadg.itemglintrelight.client;

import net.minecraft.client.gui.screens.Screen;

/**
 * 1.21.1 entry point: adapts the primitive input API to the shared screen handlers.
 */
public final class ItemGlintRelightConfigScreen extends ItemGlintRelightConfigScreenBase {
    public ItemGlintRelightConfigScreen(Screen parent) {
        super(parent);
    }

    @Override
    public boolean mouseClicked(double screenMouseX, double screenMouseY, int screenButton) {
        return handleMouseClicked(screenMouseX, screenMouseY, screenButton)
                || super.mouseClicked(screenMouseX, screenMouseY, screenButton);
    }

    @Override
    public boolean mouseDragged(double screenMouseX, double screenMouseY, int screenButton, double dragX, double dragY) {
        return handleMouseDragged(screenMouseX, screenMouseY, screenButton, dragX, dragY)
                || super.mouseDragged(screenMouseX, screenMouseY, screenButton, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double screenMouseX, double screenMouseY, int screenButton) {
        return handleMouseReleased(screenMouseX, screenMouseY, screenButton)
                || super.mouseReleased(screenMouseX, screenMouseY, screenButton);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return handleKeyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return handleCharTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }
}
