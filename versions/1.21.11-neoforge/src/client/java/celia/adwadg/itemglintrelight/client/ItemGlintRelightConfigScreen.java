package celia.adwadg.itemglintrelight.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * 1.21.11+ entry point: adapts the event-object input API to the shared screen handlers.
 */
public final class ItemGlintRelightConfigScreen extends ItemGlintRelightConfigScreenBase {
    public ItemGlintRelightConfigScreen(Screen parent) {
        super(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return handleMouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return handleMouseDragged(event.x(), event.y(), event.button(), dragX, dragY)
                || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return handleMouseReleased(event.x(), event.y(), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return handleKeyPressed(event.key(), event.scancode(), event.modifiers()) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return handleCharTyped((char) event.codepoint(), event.modifiers()) || super.charTyped(event);
    }
}
