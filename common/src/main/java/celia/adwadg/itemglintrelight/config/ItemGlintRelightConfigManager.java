package celia.adwadg.itemglintrelight.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public final class ItemGlintRelightConfigManager {
    public static final String FILE_NAME = "itemglintrelight.properties";
    private static final ItemGlintRelightConfig CONFIG = new ItemGlintRelightConfig();
    private static Path configFile;

    private ItemGlintRelightConfigManager() { }

    public static synchronized void register(Path configDirectory) {
        Objects.requireNonNull(configDirectory, "configDirectory");
        if (configFile != null) {
            return;
        }
        configFile = configDirectory.resolve(FILE_NAME);
        load();
    }

    public static synchronized ItemGlintRelightConfig get() {
        ensureRegistered();
        return CONFIG;
    }

    public static synchronized void save() {
        ensureRegistered();
        Properties values = new Properties();
        values.setProperty("enabled", Boolean.toString(CONFIG.enabled()));
        values.setProperty("colorMode", CONFIG.colorMode().name());
        values.setProperty("primaryColor", Integer.toUnsignedString(CONFIG.primaryColor()));
        values.setProperty("secondaryColor", Integer.toUnsignedString(CONFIG.secondaryColor()));
        values.setProperty("animationSpeed", Float.toString(CONFIG.animationSpeed()));
        values.setProperty("renderHeldItems", Boolean.toString(CONFIG.renderHeldItems()));
        values.setProperty("renderGuiItems", Boolean.toString(CONFIG.renderGuiItems()));
        values.setProperty("renderThirdPerson", Boolean.toString(CONFIG.renderThirdPerson()));
        values.setProperty("bloomEnabled", Boolean.toString(CONFIG.bloomEnabled()));
        values.setProperty("ruleSwitchDelayEnabled", Boolean.toString(CONFIG.ruleSwitchDelayEnabled()));

        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream output = Files.newOutputStream(configFile)) {
                values.store(output, "ItemGlintRelight configuration");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save configuration to " + configFile, exception);
        }
    }

    private static void load() {
        if (!Files.isRegularFile(configFile)) {
            return;
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            values.load(input);
            CONFIG.setEnabled(booleanValue(values, "enabled", ItemGlintRelightConfig.DEFAULT_ENABLED));
            CONFIG.setColorMode(enumValue(values, "colorMode", ItemGlintRelightConfig.DEFAULT_COLOR_MODE));
            CONFIG.setPrimaryColor(unsignedIntValue(values, "primaryColor", ItemGlintRelightConfig.DEFAULT_PRIMARY_COLOR));
            CONFIG.setSecondaryColor(unsignedIntValue(values, "secondaryColor", ItemGlintRelightConfig.DEFAULT_SECONDARY_COLOR));
            CONFIG.setAnimationSpeed(floatValue(values, "animationSpeed", ItemGlintRelightConfig.DEFAULT_ANIMATION_SPEED));
            CONFIG.setRenderHeldItems(booleanValue(values, "renderHeldItems", ItemGlintRelightConfig.DEFAULT_RENDER_HELD_ITEMS));
            CONFIG.setRenderGuiItems(booleanValue(values, "renderGuiItems", ItemGlintRelightConfig.DEFAULT_RENDER_GUI_ITEMS));
            CONFIG.setRenderThirdPerson(booleanValue(values, "renderThirdPerson", ItemGlintRelightConfig.DEFAULT_RENDER_THIRD_PERSON));
            CONFIG.setBloomEnabled(booleanValue(values, "bloomEnabled", ItemGlintRelightConfig.DEFAULT_BLOOM_ENABLED));
            CONFIG.setRuleSwitchDelayEnabled(booleanValue(values, "ruleSwitchDelayEnabled", ItemGlintRelightConfig.DEFAULT_RULE_SWITCH_DELAY));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load configuration from " + configFile, exception);
        }
    }

    private static boolean booleanValue(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static float floatValue(Properties values, String key, float fallback) {
        try { return Float.parseFloat(values.getProperty(key)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static int unsignedIntValue(Properties values, String key, int fallback) {
        try { return (int) Long.parseUnsignedLong(values.getProperty(key)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static GlintColorMode enumValue(Properties values, String key, GlintColorMode fallback) {
        try { return GlintColorMode.valueOf(values.getProperty(key)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static void ensureRegistered() {
        if (configFile == null) {
            throw new IllegalStateException("ItemGlintRelight configuration has not been registered");
        }
    }
}
