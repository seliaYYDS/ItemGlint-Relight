package celia.adwadg.itemglintrelight;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point. Keep loader- and version-neutral logic here. */
public final class ItemGlintRelight implements ModInitializer {
    public static final String MOD_ID = "itemglintrelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("ItemGlintRelight initialized");
    }
}
