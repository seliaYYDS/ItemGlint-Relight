package celia.adwadg.itemglintrelight.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLMouse;

/** 26.3 reports SDL mouse buttons and InputConstants key codes. */
public final class ConfigInputImpl implements ConfigInput {
    @Override
    public int mouseLeft() {
        return SDLMouse.SDL_BUTTON_LEFT;
    }

    @Override
    public int mouseRight() {
        return SDLMouse.SDL_BUTTON_RIGHT;
    }

    @Override
    public int mouseMiddle() {
        return SDLMouse.SDL_BUTTON_MIDDLE;
    }

    @Override
    public int keyEscape() {
        return InputConstants.KEY_ESCAPE;
    }

    @Override
    public int keyReturn() {
        return InputConstants.KEY_RETURN;
    }

    @Override
    public int keyLeft() {
        return InputConstants.KEY_LEFT;
    }

    @Override
    public int keyRight() {
        return InputConstants.KEY_RIGHT;
    }

    @Override
    public int keyBackspace() {
        return InputConstants.KEY_BACKSPACE;
    }
}
