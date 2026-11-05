package celia.adwadg.itemglintrelight.client;

/**
 * The input constants that changed in 26.3: mouse buttons moved from raw GLFW indices to
 * {@code SDLMouse} values, and key codes from {@code GLFW.GLFW_KEY_*} to {@code InputConstants.KEY_*}.
 */
public interface ConfigInput {
    int mouseLeft();

    int mouseRight();

    int mouseMiddle();

    int keyEscape();

    int keyReturn();

    int keyLeft();

    int keyRight();

    int keyBackspace();
}
