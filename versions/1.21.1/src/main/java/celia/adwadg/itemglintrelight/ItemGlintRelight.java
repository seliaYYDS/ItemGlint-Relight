package celia.adwadg.itemglintrelight;

import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.platform.ConfigPlatforms;
import celia.adwadg.itemglintrelight.config.platform.ResourceLocationConfigPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemGlintRelight implements ModInitializer {
    public static final String MOD_ID = "itemglintrelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 1.21.1 names the resource location ResourceLocation; the shared config code needs this seam before it reads rules.
        ConfigPlatforms.set(new ResourceLocationConfigPlatform());
        ItemGlintRelightConfigManager.register(FabricLoader.getInstance().getConfigDir());
        LOGGER.info("ItemGlintRelight initialized");
    }
}
