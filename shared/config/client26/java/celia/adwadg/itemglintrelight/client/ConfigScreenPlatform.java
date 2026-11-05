package celia.adwadg.itemglintrelight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The one screen API that moved between 26.1.2 and 26.2 ({@code Minecraft#setScreen} -> {@code Minecraft#gui.setScreen}). */
public interface ConfigScreenPlatform {
    void returnTo(Minecraft minecraft, Screen parent);
}
