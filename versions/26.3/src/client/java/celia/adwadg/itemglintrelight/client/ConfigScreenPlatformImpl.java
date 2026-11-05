package celia.adwadg.itemglintrelight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 26.3 sets the parent screen through {@code Minecraft#gui}. */
public final class ConfigScreenPlatformImpl implements ConfigScreenPlatform {
    @Override
    public void returnTo(Minecraft minecraft, Screen parent) {
        minecraft.gui.setScreen(parent);
    }
}
