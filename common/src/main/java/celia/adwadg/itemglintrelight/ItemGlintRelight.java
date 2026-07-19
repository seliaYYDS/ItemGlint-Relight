package celia.adwadg.itemglintrelight;

import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemGlintRelight implements ModInitializer {
    public static final String MOD_ID = "itemglintrelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ItemGlintRelightConfigManager.register(FabricLoader.getInstance().getConfigDir());
        LOGGER.info("ItemGlintRelight initialized");
    }
}
