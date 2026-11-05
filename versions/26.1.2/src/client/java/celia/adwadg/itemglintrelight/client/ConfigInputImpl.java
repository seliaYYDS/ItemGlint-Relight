package celia.adwadg.itemglintrelight.client;

import org.lwjgl.glfw.GLFW;

/** Mouse buttons are raw GLFW indices / keys are GLFW key codes in this version. */
public final class ConfigInputImpl implements ConfigInput {
    @Override
    public int mouseLeft() {
        return 0;
    }

    @Override
    public int mouseRight() {
        return 1;
    }

    @Override
    public int mouseMiddle() {
        return 2;
    }

    @Override
    public int keyEscape() {
        return GLFW.GLFW_KEY_ESCAPE;
    }

    @Override
    public int keyReturn() {
        return GLFW.GLFW_KEY_ENTER;
    }

    @Override
    public int keyLeft() {
        return GLFW.GLFW_KEY_LEFT;
    }

    @Override
    public int keyRight() {
        return GLFW.GLFW_KEY_RIGHT;
    }

    @Override
    public int keyBackspace() {
        return GLFW.GLFW_KEY_BACKSPACE;
    }
}
