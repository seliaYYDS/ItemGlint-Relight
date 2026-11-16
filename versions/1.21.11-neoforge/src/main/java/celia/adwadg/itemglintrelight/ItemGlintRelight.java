package celia.adwadg.itemglintrelight;

import celia.adwadg.itemglintrelight.client.ItemGlintRelightClient;
import celia.adwadg.itemglintrelight.client.ItemGlintRelightConfigScreen;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.platform.ConfigPlatforms;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = ItemGlintRelight.MOD_ID, dist = Dist.CLIENT)
public final class ItemGlintRelight {
    public static final String MOD_ID = "itemglintrelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ItemGlintRelight(IEventBus modEventBus, ModContainer modContainer) {
        ConfigPlatforms.set(new NeoForgeConfigPlatform());
        ItemGlintRelightConfigManager.register(FMLPaths.CONFIGDIR.get());

        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new ItemGlintRelightConfigScreen(parent));

        ItemGlintRelightClient.init(modEventBus);
        LOGGER.info("ItemGlintRelight initialized");
    }
}