package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineCompat;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlinePunchyCompat;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

public final class ItemGlintRelightClient implements ClientModInitializer {
    /** 1.21.1 takes the translation key as a plain String (1.21.11 replaced it with KeyMapping.Category). */
    private static final String KEY_CATEGORY = "key.category.itemglintrelight.main";

    private static final KeyMapping OPEN_CONFIG = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.itemglintrelight.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KEY_CATEGORY));

    /** Dev aid: -Ditemglintrelight.openConfig=true opens the screen on the next tick. */
    private static final boolean OPEN_CONFIG_ON_START = Boolean.getBoolean("itemglintrelight.openConfig")
            || "true".equalsIgnoreCase(System.getenv("ITEMGLINT_OPEN_CONFIG"));
    private static boolean pendingOpen;

    @Override
    public void onInitializeClient() {
        pendingOpen = OPEN_CONFIG_ON_START;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new ItemGlintRelightConfigScreen(null));
                }
            }
            if (pendingOpen && client.screen instanceof TitleScreen) {
                pendingOpen = false;
                client.setScreen(new ItemGlintRelightConfigScreen(client.screen));
            }
        });
        ItemGlintRelight.LOGGER.info("[Build] jar={} built={} punchy={} punchyClasses={} punchyHooks={} iris={} sodium={}",
                buildJar(), buildTime(), HeldItemOutlinePunchyCompat.isLoaded(), hasPunchyClasses(),
                HeldItemOutlinePunchyCompat.hooksAvailable(), HeldItemOutlineCompat.isIrisLoaded(), isLoaded("sodium"));
        ItemGlintRelight.LOGGER.info("ItemGlintRelight client initialized");
    }

    private static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /** True when Punchy's renderer class can actually be resolved, i.e. the reflection targets exist. */
    private static boolean hasPunchyClasses() {
        try {
            Class.forName("punchy.client.render.PunchyArmRenderer", false,
                    ItemGlintRelightClient.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String buildJar() {
        try {
            java.security.CodeSource source = ItemGlintRelightClient.class.getProtectionDomain().getCodeSource();
            return source == null ? "unknown" : new java.io.File(source.getLocation().toURI()).getName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String buildTime() {
        try {
            java.security.CodeSource source = ItemGlintRelightClient.class.getProtectionDomain().getCodeSource();
            return source == null ? "unknown"
                    : java.time.Instant.ofEpochMilli(new java.io.File(source.getLocation().toURI()).lastModified()).toString();
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
