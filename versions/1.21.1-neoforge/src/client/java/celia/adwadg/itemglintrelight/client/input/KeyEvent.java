package celia.adwadg.itemglintrelight.client.input;

/**
 * 1.21.1 has no {@code net.minecraft.client.input.KeyEvent}: {@code Screen#keyPressed} still takes
 * raw {@code (int keyCode, int scanCode, int modifiers)}. This record reproduces the 1.21.11
 * accessor shape so the ported screen logic can stay identical to the 1.21.11 source.
 */
public record KeyEvent(int key, int scancode, int modifiers) {
}
