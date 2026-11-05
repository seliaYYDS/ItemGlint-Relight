package celia.adwadg.itemglintrelight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 26.1.2 keeps the screen setter on {@code Minecraft}. */
public final class ConfigScreenPlatformImpl implements ConfigScreenPlatform {
    @Override
    public void returnTo(Minecraft minecraft, Screen parent) {
        minecraft.setScreen(parent);
    }
}
