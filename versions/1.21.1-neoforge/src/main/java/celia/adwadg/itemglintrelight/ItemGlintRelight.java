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

/**
 * NeoForge 21.1 客户端入口。
 *
 * <p>配置模型、显示规则与配置界面直接复用 {@code shared/} 下的同一份源码；本类只负责
 * 平台接线：配置目录、配置平台实现、Mod 列表里的配置界面入口，以及按键/每 tick 处理。
 */
@Mod(value = ItemGlintRelight.MOD_ID, dist = Dist.CLIENT)
public final class ItemGlintRelight {
    public static final String MOD_ID = "itemglintrelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ItemGlintRelight(IEventBus modEventBus, ModContainer modContainer) {
        ConfigPlatforms.set(new NeoForgeConfigPlatform());
        ItemGlintRelightConfigManager.register(FMLPaths.CONFIGDIR.get());

        // 与 Fabric 版的 ModMenu 入口等价：NeoForge 的 Mods 列表会显示 Config 按钮。
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new ItemGlintRelightConfigScreen(parent));

        ItemGlintRelightClient.init(modEventBus);
        LOGGER.info("ItemGlintRelight initialized");
    }
}