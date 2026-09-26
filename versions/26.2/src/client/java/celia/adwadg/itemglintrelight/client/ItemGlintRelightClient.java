package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineCompat;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlinePunchyCompat;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ItemGlintRelightClient implements ClientModInitializer {
    private static final KeyMapping OPEN_CONFIG = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.itemglintrelight.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "main"))));

    @Override
    public void onInitializeClient() {
        // TODO 26.2: WorldRenderEvents was removed; hook the replacement world-pass entry point.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new ItemGlintRelightConfigScreen(null));
            }
        });
        ItemGlintRelight.LOGGER.info("[Compat] punchy={} punchyHooksReady={} iris={}",
                HeldItemOutlinePunchyCompat.isLoaded(), HeldItemOutlinePunchyCompat.hooksAvailable(),
                HeldItemOutlineCompat.isIrisLoaded());
        ItemGlintRelight.LOGGER.info("ItemGlintRelight client initialized");
    }
}
