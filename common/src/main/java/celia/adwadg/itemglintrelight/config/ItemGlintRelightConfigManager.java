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
        values.setProperty("primaryColor", Integer.toUnsignedString(CONFIG.primaryColor()));
        values.setProperty("secondaryColor", Integer.toUnsignedString(CONFIG.secondaryColor()));
        values.setProperty("renderHeldItems", Boolean.toString(CONFIG.renderHeldItems()));
        values.setProperty("renderGuiItems", Boolean.toString(CONFIG.renderGuiItems()));
        values.setProperty("renderThirdPerson", Boolean.toString(CONFIG.renderThirdPerson()));
        values.setProperty("bloomEnabled", Boolean.toString(CONFIG.bloomEnabled()));
        values.setProperty("outlineEnabled", Boolean.toString(CONFIG.outlineEnabled()));
        values.setProperty("outlineMainHand", Boolean.toString(CONFIG.outlineMainHand()));
        values.setProperty("outlineOffHand", Boolean.toString(CONFIG.outlineOffHand()));
        values.setProperty("outlineThirdPerson", Boolean.toString(CONFIG.outlineThirdPerson()));
        values.setProperty("outlineGuiItems", Boolean.toString(CONFIG.outlineGuiItems()));
        values.setProperty("outlineWidth", Float.toString(CONFIG.outlineWidth()));
        values.setProperty("outlineSoftness", Float.toString(CONFIG.outlineSoftness()));
        values.setProperty("outlineAlphaThreshold", Float.toString(CONFIG.outlineAlphaThreshold()));
        values.setProperty("outlineOpacity", Float.toString(CONFIG.outlineOpacity()));
        values.setProperty("outlineQuality", CONFIG.outlineQuality().name());
        values.setProperty("outlineGlowIntensity", Float.toString(CONFIG.outlineGlowIntensity()));
        values.setProperty("outlineBloomEnabled", Boolean.toString(CONFIG.outlineBloomEnabled()));
        values.setProperty("outlineBloomQuality", CONFIG.outlineBloomQuality().name());
        values.setProperty("outlineBloomRadius", Float.toString(CONFIG.outlineBloomRadius()));
        values.setProperty("outlineBloomIntensity", Float.toString(CONFIG.outlineBloomIntensity()));
        values.setProperty("outlineBloomBlurPasses", Integer.toString(CONFIG.outlineBloomBlurPasses()));
        values.setProperty("outlineRenderMode", CONFIG.outlineRenderMode().name());
        values.setProperty("outlineColorMode", CONFIG.outlineColorMode().name());
        values.setProperty("outlinePrimaryColor", Integer.toUnsignedString(CONFIG.outlinePrimaryColor()));
        values.setProperty("outlineSecondaryColor", Integer.toUnsignedString(CONFIG.outlineSecondaryColor()));
        values.setProperty("outlineColorScrollSpeed", Float.toString(CONFIG.outlineColorScrollSpeed()));
        values.setProperty("outlineColorScrollDirection", Float.toString(CONFIG.outlineColorScrollDirection()));
        values.setProperty("outlineColorScrollInterval", Float.toString(CONFIG.outlineColorScrollInterval()));
        values.setProperty("outlineSampleSize", Integer.toString(CONFIG.outlineSampleSize()));
        values.setProperty("outlineSampleColorCount", Integer.toString(CONFIG.outlineSampleColorCount()));

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
        boolean containsRemovedSettings;
        try (InputStream input = Files.newInputStream(configFile)) {
            values.load(input);
            containsRemovedSettings = values.containsKey("enabled")
                    || values.containsKey("colorMode")
                    || values.containsKey("animationSpeed")
                    || values.containsKey("ruleSwitchDelayEnabled");
            CONFIG.setPrimaryColor(unsignedIntValue(values, "primaryColor", ItemGlintRelightConfig.DEFAULT_PRIMARY_COLOR));
            CONFIG.setSecondaryColor(unsignedIntValue(values, "secondaryColor", ItemGlintRelightConfig.DEFAULT_SECONDARY_COLOR));
            CONFIG.setRenderHeldItems(booleanValue(values, "renderHeldItems", ItemGlintRelightConfig.DEFAULT_RENDER_HELD_ITEMS));
            CONFIG.setRenderGuiItems(booleanValue(values, "renderGuiItems", ItemGlintRelightConfig.DEFAULT_RENDER_GUI_ITEMS));
            CONFIG.setRenderThirdPerson(booleanValue(values, "renderThirdPerson", ItemGlintRelightConfig.DEFAULT_RENDER_THIRD_PERSON));
            CONFIG.setBloomEnabled(booleanValue(values, "bloomEnabled", ItemGlintRelightConfig.DEFAULT_BLOOM_ENABLED));
            CONFIG.setOutlineEnabled(booleanValue(values, "outlineEnabled", ItemGlintRelightConfig.DEFAULT_OUTLINE_ENABLED));
            CONFIG.setOutlineMainHand(booleanValue(values, "outlineMainHand", ItemGlintRelightConfig.DEFAULT_OUTLINE_MAIN_HAND));
            CONFIG.setOutlineOffHand(booleanValue(values, "outlineOffHand", ItemGlintRelightConfig.DEFAULT_OUTLINE_OFF_HAND));
            CONFIG.setOutlineThirdPerson(booleanValue(values, "outlineThirdPerson", ItemGlintRelightConfig.DEFAULT_OUTLINE_THIRD_PERSON));
            CONFIG.setOutlineGuiItems(booleanValue(values, "outlineGuiItems", ItemGlintRelightConfig.DEFAULT_OUTLINE_GUI_ITEMS));
            CONFIG.setOutlineWidth(floatValue(values, "outlineWidth", ItemGlintRelightConfig.DEFAULT_OUTLINE_WIDTH));
            CONFIG.setOutlineSoftness(floatValue(values, "outlineSoftness", ItemGlintRelightConfig.DEFAULT_OUTLINE_SOFTNESS));
            CONFIG.setOutlineAlphaThreshold(floatValue(values, "outlineAlphaThreshold", ItemGlintRelightConfig.DEFAULT_OUTLINE_ALPHA_THRESHOLD));
            CONFIG.setOutlineOpacity(floatValue(values, "outlineOpacity", ItemGlintRelightConfig.DEFAULT_OUTLINE_OPACITY));
            CONFIG.setOutlineQuality(enumValue(values, "outlineQuality", ItemGlintRelightConfig.DEFAULT_OUTLINE_QUALITY));
            CONFIG.setOutlineGlowIntensity(floatValue(values, "outlineGlowIntensity", ItemGlintRelightConfig.DEFAULT_OUTLINE_GLOW_INTENSITY));
            CONFIG.setOutlineBloomEnabled(booleanValue(values, "outlineBloomEnabled", ItemGlintRelightConfig.DEFAULT_OUTLINE_BLOOM_ENABLED));
            CONFIG.setOutlineBloomQuality(enumValue(values, "outlineBloomQuality", ItemGlintRelightConfig.DEFAULT_OUTLINE_BLOOM_QUALITY));
            CONFIG.setOutlineBloomRadius(floatValue(values, "outlineBloomRadius", ItemGlintRelightConfig.DEFAULT_OUTLINE_BLOOM_RADIUS));
            CONFIG.setOutlineBloomIntensity(floatValue(values, "outlineBloomIntensity", ItemGlintRelightConfig.DEFAULT_OUTLINE_BLOOM_INTENSITY));
            CONFIG.setOutlineBloomBlurPasses(intValue(values, "outlineBloomBlurPasses", ItemGlintRelightConfig.DEFAULT_OUTLINE_BLOOM_BLUR_PASSES));
            CONFIG.setOutlineRenderMode(enumValue(values, "outlineRenderMode", ItemGlintRelightConfig.DEFAULT_OUTLINE_RENDER_MODE));
            CONFIG.setOutlineColorMode(enumValue(values, "outlineColorMode", ItemGlintRelightConfig.DEFAULT_OUTLINE_COLOR_MODE));
            CONFIG.setOutlinePrimaryColor(unsignedIntValue(values, "outlinePrimaryColor", ItemGlintRelightConfig.DEFAULT_OUTLINE_PRIMARY_COLOR));
            CONFIG.setOutlineSecondaryColor(unsignedIntValue(values, "outlineSecondaryColor", ItemGlintRelightConfig.DEFAULT_OUTLINE_SECONDARY_COLOR));
            CONFIG.setOutlineColorScrollSpeed(floatValue(values, "outlineColorScrollSpeed", ItemGlintRelightConfig.DEFAULT_OUTLINE_COLOR_SCROLL_SPEED));
            CONFIG.setOutlineColorScrollDirection(floatValue(values, "outlineColorScrollDirection", ItemGlintRelightConfig.DEFAULT_OUTLINE_COLOR_SCROLL_DIRECTION));
            CONFIG.setOutlineColorScrollInterval(floatValue(values, "outlineColorScrollInterval", ItemGlintRelightConfig.DEFAULT_OUTLINE_COLOR_SCROLL_INTERVAL));
            CONFIG.setOutlineSampleSize(intValue(values, "outlineSampleSize", ItemGlintRelightConfig.DEFAULT_OUTLINE_SAMPLE_SIZE));
            CONFIG.setOutlineSampleColorCount(intValue(values, "outlineSampleColorCount", ItemGlintRelightConfig.DEFAULT_OUTLINE_SAMPLE_COLOR_COUNT));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load configuration from " + configFile, exception);
        }
        if (containsRemovedSettings) {
            save();
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

    private static int intValue(Properties values, String key, int fallback) {
        try { return Integer.parseInt(values.getProperty(key)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static <T extends Enum<T>> T enumValue(Properties values, String key, T fallback) {
        try { return Enum.valueOf(fallback.getDeclaringClass(), values.getProperty(key)); } catch (RuntimeException ignored) { return fallback; }
    }

    private static void ensureRegistered() {
        if (configFile == null) {
            throw new IllegalStateException("ItemGlintRelight configuration has not been registered");
        }
    }
}
