package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineCompat;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlinePunchyCompat;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlinePipelines;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderState;
import celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class ItemGlintRelightClient {
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "main"));

    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.itemglintrelight.open_config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, KEY_CATEGORY);

    /** Dev aid: -Ditemglintrelight.openConfig=true opens the screen on the next tick. */
    private static final boolean OPEN_CONFIG_ON_START = Boolean.getBoolean("itemglintrelight.openConfig")
            || "true".equalsIgnoreCase(System.getenv("ITEMGLINT_OPEN_CONFIG"));
    private static boolean pendingOpen;

    private ItemGlintRelightClient() { }

    public static void init(IEventBus modEventBus) {
        pendingOpen = OPEN_CONFIG_ON_START;
        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.registerCategory(KEY_CATEGORY);
            event.register(OPEN_CONFIG);
        });
        modEventBus.addListener(RegisterPictureInPictureRenderersEvent.class,
                event -> event.register(ItemPreviewRenderState.class, ItemPreviewRenderer::new));
        modEventBus.addListener(RegisterRenderPipelinesEvent.class,
                event -> HeldItemOutlinePipelines.register(event::registerPipeline));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> onEndTick());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterSky.class,
                event -> HeldItemOutlineRenderer.beginMainWorldPass());

        ItemGlintRelight.LOGGER.info("[Compat] punchy={} punchyHooksReady={} iris={}",
                HeldItemOutlinePunchyCompat.isLoaded(), HeldItemOutlinePunchyCompat.hooksAvailable(),
                HeldItemOutlineCompat.isIrisLoaded());
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