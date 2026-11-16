package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** 客户端接线：按键注册与每 tick 处理，行为与 Fabric 1.21.1 的 ItemGlintRelightClient 一致。 */
public final class ItemGlintRelightClient {
    /** 1.21.1 takes the translation key as a plain String (1.21.11 replaced it with KeyMapping.Category). */
    private static final String KEY_CATEGORY = "key.category.itemglintrelight.main";

    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.itemglintrelight.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KEY_CATEGORY);

    /** Dev aid: -Ditemglintrelight.openConfig=true opens the screen on the next tick. */
    private static final boolean OPEN_CONFIG_ON_START = Boolean.getBoolean("itemglintrelight.openConfig")
            || "true".equalsIgnoreCase(System.getenv("ITEMGLINT_OPEN_CONFIG"));
    private static boolean pendingOpen;

    private ItemGlintRelightClient() { }

    public static void init(IEventBus modEventBus) {
        pendingOpen = OPEN_CONFIG_ON_START;
        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> event.register(OPEN_CONFIG));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onEndTick());
        ItemGlintRelight.LOGGER.info("ItemGlintRelight client initialized");
    }

    private static void onEndTick() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        while (OPEN_CONFIG.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new ItemGlintRelightConfigScreen(null));
            }
        }
        if (pendingOpen && client.screen instanceof TitleScreen) {
            pendingOpen = false;
            client.setScreen(new ItemGlintRelightConfigScreen(client.screen));
        }
    }
}