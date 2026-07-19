package celia.adwadg.itemglintrelight.config;

public final class ItemGlintRelightConfig {
    public static final boolean DEFAULT_ENABLED = true;
    public static final GlintColorMode DEFAULT_COLOR_MODE = GlintColorMode.STATIC;
    public static final int DEFAULT_PRIMARY_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_SECONDARY_COLOR = 0xFF66CCFF;
    public static final float DEFAULT_ANIMATION_SPEED = 1.0F;
    public static final boolean DEFAULT_RENDER_HELD_ITEMS = true;
    public static final boolean DEFAULT_RENDER_GUI_ITEMS = true;
    public static final boolean DEFAULT_RENDER_THIRD_PERSON = true;
    public static final boolean DEFAULT_BLOOM_ENABLED = true;
    public static final boolean DEFAULT_RULE_SWITCH_DELAY = true;

    private boolean enabled = DEFAULT_ENABLED;
    private GlintColorMode colorMode = DEFAULT_COLOR_MODE;
    private int primaryColor = DEFAULT_PRIMARY_COLOR;
    private int secondaryColor = DEFAULT_SECONDARY_COLOR;
    private float animationSpeed = DEFAULT_ANIMATION_SPEED;
    private boolean renderHeldItems = DEFAULT_RENDER_HELD_ITEMS;
    private boolean renderGuiItems = DEFAULT_RENDER_GUI_ITEMS;
    private boolean renderThirdPerson = DEFAULT_RENDER_THIRD_PERSON;
    private boolean bloomEnabled = DEFAULT_BLOOM_ENABLED;
    private boolean ruleSwitchDelayEnabled = DEFAULT_RULE_SWITCH_DELAY;

    public ItemGlintRelightConfig copy() {
        ItemGlintRelightConfig copy = new ItemGlintRelightConfig();
        copy.enabled = enabled;
        copy.colorMode = colorMode;
        copy.primaryColor = primaryColor;
        copy.secondaryColor = secondaryColor;
        copy.animationSpeed = animationSpeed;
        copy.renderHeldItems = renderHeldItems;
        copy.renderGuiItems = renderGuiItems;
        copy.renderThirdPerson = renderThirdPerson;
        copy.bloomEnabled = bloomEnabled;
        copy.ruleSwitchDelayEnabled = ruleSwitchDelayEnabled;
        return copy;
    }

    public void copyFrom(ItemGlintRelightConfig source) {
        enabled = source.enabled;
        colorMode = source.colorMode;
        primaryColor = source.primaryColor;
        secondaryColor = source.secondaryColor;
        animationSpeed = source.animationSpeed;
        renderHeldItems = source.renderHeldItems;
        renderGuiItems = source.renderGuiItems;
        renderThirdPerson = source.renderThirdPerson;
        bloomEnabled = source.bloomEnabled;
        ruleSwitchDelayEnabled = source.ruleSwitchDelayEnabled;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public GlintColorMode colorMode() { return colorMode; }
    public void setColorMode(GlintColorMode colorMode) { this.colorMode = colorMode == null ? DEFAULT_COLOR_MODE : colorMode; }
    public int primaryColor() { return primaryColor; }
    public void setPrimaryColor(int primaryColor) { this.primaryColor = primaryColor; }
    public int secondaryColor() { return secondaryColor; }
    public void setSecondaryColor(int secondaryColor) { this.secondaryColor = secondaryColor; }
    public float animationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(float animationSpeed) { this.animationSpeed = clamp(animationSpeed, 0.0F, 10.0F); }
    public boolean renderHeldItems() { return renderHeldItems; }
    public void setRenderHeldItems(boolean renderHeldItems) { this.renderHeldItems = renderHeldItems; }
    public boolean renderGuiItems() { return renderGuiItems; }
    public void setRenderGuiItems(boolean renderGuiItems) { this.renderGuiItems = renderGuiItems; }
    public boolean renderThirdPerson() { return renderThirdPerson; }
    public void setRenderThirdPerson(boolean renderThirdPerson) { this.renderThirdPerson = renderThirdPerson; }
    public boolean bloomEnabled() { return bloomEnabled; }
    public void setBloomEnabled(boolean bloomEnabled) { this.bloomEnabled = bloomEnabled; }
    public boolean ruleSwitchDelayEnabled() { return ruleSwitchDelayEnabled; }
    public void setRuleSwitchDelayEnabled(boolean ruleSwitchDelayEnabled) { this.ruleSwitchDelayEnabled = ruleSwitchDelayEnabled; }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
