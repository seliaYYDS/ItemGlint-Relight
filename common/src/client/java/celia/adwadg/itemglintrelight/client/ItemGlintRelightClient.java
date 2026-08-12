package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ItemGlintRelightClient implements ClientModInitializer {
    private static final KeyMapping OPEN_CONFIG = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.itemglintrelight.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "main"))));

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG.consumeClick()) {
                if (client.screen == null) client.setScreen(new ItemGlintRelightConfigScreen(null));
            }
        });
        ItemGlintRelight.LOGGER.info("ItemGlintRelight client initialized");
    }
}
