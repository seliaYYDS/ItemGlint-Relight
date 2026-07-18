package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import net.fabricmc.api.ClientModInitializer;

/** Client-only entry point for rendering and configuration initialization. */
public final class ItemGlintRelightClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemGlintRelight.LOGGER.info("ItemGlintRelight client initialized");
    }
}
