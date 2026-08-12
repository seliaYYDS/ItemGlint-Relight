package celia.adwadg.itemglintrelight.config;


public final class ItemGlintRelightConfig {
    public static final int DEFAULT_PRIMARY_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_SECONDARY_COLOR = 0xFF66CCFF;
    public static final boolean DEFAULT_RENDER_HELD_ITEMS = true;
    public static final boolean DEFAULT_RENDER_GUI_ITEMS = true;
    public static final boolean DEFAULT_RENDER_THIRD_PERSON = true;
    public static final boolean DEFAULT_BLOOM_ENABLED = true;
    public static final boolean DEFAULT_OUTLINE_ENABLED = true;
    public static final boolean DEFAULT_OUTLINE_MAIN_HAND = true;
    public static final boolean DEFAULT_OUTLINE_OFF_HAND = true;
    public static final boolean DEFAULT_OUTLINE_THIRD_PERSON = true;
    public static final boolean DEFAULT_OUTLINE_GUI_ITEMS = true;
    public static final float DEFAULT_OUTLINE_WIDTH = 1.0F;
    public static final float DEFAULT_OUTLINE_SOFTNESS = 0.5F;
    public static final float DEFAULT_OUTLINE_ALPHA_THRESHOLD = 0.1F;
    public static final float DEFAULT_OUTLINE_OPACITY = 1.0F;
    public static final RenderQuality DEFAULT_OUTLINE_QUALITY = RenderQuality.MEDIUM;
    public static final RenderQuality DEFAULT_GUI_OUTLINE_QUALITY = RenderQuality.MEDIUM;
    public static final float DEFAULT_OUTLINE_GLOW_INTENSITY = 1.0F;
    public static final boolean DEFAULT_OUTLINE_BLOOM_ENABLED = true;
    public static final RenderQuality DEFAULT_OUTLINE_BLOOM_QUALITY = RenderQuality.MEDIUM;
    public static final float DEFAULT_OUTLINE_BLOOM_RADIUS = 2.0F;
    public static final float DEFAULT_OUTLINE_BLOOM_INTENSITY = 1.0F;
    public static final int DEFAULT_OUTLINE_BLOOM_BLUR_PASSES = 2;
    public static final OutlineRenderMode DEFAULT_OUTLINE_RENDER_MODE = OutlineRenderMode.FLAT;
    public static final OutlineColorMode DEFAULT_OUTLINE_COLOR_MODE = OutlineColorMode.SINGLE;
    public static final int DEFAULT_OUTLINE_PRIMARY_COLOR = 0xFF30B8FF;
    public static final int DEFAULT_OUTLINE_SECONDARY_COLOR = 0xFF8CFF45;
    public static final float DEFAULT_OUTLINE_COLOR_SCROLL_SPEED = 1.0F;
    public static final float DEFAULT_OUTLINE_COLOR_SCROLL_DIRECTION = 0.0F;
    public static final float DEFAULT_OUTLINE_COLOR_SCROLL_INTERVAL = 0.75F;
    public static final ColorScrollMode DEFAULT_OUTLINE_COLOR_SCROLL_MODE = ColorScrollMode.PLANAR;
    public static final int DEFAULT_OUTLINE_SAMPLE_SIZE = 4;
    public static final int DEFAULT_OUTLINE_SAMPLE_COLOR_COUNT = 4;

    private int primaryColor = DEFAULT_PRIMARY_COLOR;
    private int secondaryColor = DEFAULT_SECONDARY_COLOR;
    private boolean renderHeldItems = DEFAULT_RENDER_HELD_ITEMS;
    private boolean renderGuiItems = DEFAULT_RENDER_GUI_ITEMS;
    private boolean renderThirdPerson = DEFAULT_RENDER_THIRD_PERSON;
    private boolean bloomEnabled = DEFAULT_BLOOM_ENABLED;
    private boolean outlineEnabled = DEFAULT_OUTLINE_ENABLED;
    private boolean outlineMainHand = DEFAULT_OUTLINE_MAIN_HAND;
    private boolean outlineOffHand = DEFAULT_OUTLINE_OFF_HAND;
    private boolean outlineThirdPerson = DEFAULT_OUTLINE_THIRD_PERSON;
    private boolean outlineGuiItems = DEFAULT_OUTLINE_GUI_ITEMS;
    private float outlineWidth = DEFAULT_OUTLINE_WIDTH;
    private float outlineSoftness = DEFAULT_OUTLINE_SOFTNESS;
    private float outlineAlphaThreshold = DEFAULT_OUTLINE_ALPHA_THRESHOLD;
    private float outlineOpacity = DEFAULT_OUTLINE_OPACITY;
    private RenderQuality outlineQuality = DEFAULT_OUTLINE_QUALITY;
    private RenderQuality guiOutlineQuality = DEFAULT_GUI_OUTLINE_QUALITY;
    private float outlineGlowIntensity = DEFAULT_OUTLINE_GLOW_INTENSITY;
    private boolean outlineBloomEnabled = DEFAULT_OUTLINE_BLOOM_ENABLED;
    private RenderQuality outlineBloomQuality = DEFAULT_OUTLINE_BLOOM_QUALITY;
    private float outlineBloomRadius = DEFAULT_OUTLINE_BLOOM_RADIUS;
    private float outlineBloomIntensity = DEFAULT_OUTLINE_BLOOM_INTENSITY;
    private int outlineBloomBlurPasses = DEFAULT_OUTLINE_BLOOM_BLUR_PASSES;
    private OutlineRenderMode outlineRenderMode = DEFAULT_OUTLINE_RENDER_MODE;
    private OutlineColorMode outlineColorMode = DEFAULT_OUTLINE_COLOR_MODE;
    private int outlinePrimaryColor = DEFAULT_OUTLINE_PRIMARY_COLOR;
    private int outlineSecondaryColor = DEFAULT_OUTLINE_SECONDARY_COLOR;
    private float outlineColorScrollSpeed = DEFAULT_OUTLINE_COLOR_SCROLL_SPEED;
    private float outlineColorScrollDirection = DEFAULT_OUTLINE_COLOR_SCROLL_DIRECTION;
    private float outlineColorScrollInterval = DEFAULT_OUTLINE_COLOR_SCROLL_INTERVAL;
    private ColorScrollMode outlineColorScrollMode = DEFAULT_OUTLINE_COLOR_SCROLL_MODE;
    private int outlineSampleSize = DEFAULT_OUTLINE_SAMPLE_SIZE;
    private int outlineSampleColorCount = DEFAULT_OUTLINE_SAMPLE_COLOR_COUNT;

    public ItemGlintRelightConfig copy() {
        ItemGlintRelightConfig copy = new ItemGlintRelightConfig();
        copy.primaryColor = primaryColor;
        copy.secondaryColor = secondaryColor;
        copy.renderHeldItems = renderHeldItems;
        copy.renderGuiItems = renderGuiItems;
        copy.renderThirdPerson = renderThirdPerson;
        copy.bloomEnabled = bloomEnabled;
        copy.outlineEnabled = outlineEnabled;
        copy.outlineMainHand = outlineMainHand;
        copy.outlineOffHand = outlineOffHand;
        copy.outlineThirdPerson = outlineThirdPerson;
        copy.outlineGuiItems = outlineGuiItems;
        copy.outlineWidth = outlineWidth;
        copy.outlineSoftness = outlineSoftness;
        copy.outlineAlphaThreshold = outlineAlphaThreshold;
        copy.outlineOpacity = outlineOpacity;
        copy.outlineQuality = outlineQuality;
        copy.guiOutlineQuality = guiOutlineQuality;
        copy.outlineGlowIntensity = outlineGlowIntensity;
        copy.outlineBloomEnabled = outlineBloomEnabled;
        copy.outlineBloomQuality = outlineBloomQuality;
        copy.outlineBloomRadius = outlineBloomRadius;
        copy.outlineBloomIntensity = outlineBloomIntensity;
        copy.outlineBloomBlurPasses = outlineBloomBlurPasses;
        copy.outlineRenderMode = outlineRenderMode;
        copy.outlineColorMode = outlineColorMode;
        copy.outlinePrimaryColor = outlinePrimaryColor;
        copy.outlineSecondaryColor = outlineSecondaryColor;
        copy.outlineColorScrollSpeed = outlineColorScrollSpeed;
        copy.outlineColorScrollDirection = outlineColorScrollDirection;
        copy.outlineColorScrollInterval = outlineColorScrollInterval;
        copy.outlineColorScrollMode = outlineColorScrollMode;
        copy.outlineSampleSize = outlineSampleSize;
        copy.outlineSampleColorCount = outlineSampleColorCount;
        return copy;
    }

    public void copyFrom(ItemGlintRelightConfig source) {
        primaryColor = source.primaryColor;
        secondaryColor = source.secondaryColor;
        renderHeldItems = source.renderHeldItems;
        renderGuiItems = source.renderGuiItems;
        renderThirdPerson = source.renderThirdPerson;
        bloomEnabled = source.bloomEnabled;
        outlineEnabled = source.outlineEnabled;
        outlineMainHand = source.outlineMainHand;
        outlineOffHand = source.outlineOffHand;
        outlineThirdPerson = source.outlineThirdPerson;
        outlineGuiItems = source.outlineGuiItems;
        outlineWidth = source.outlineWidth;
        outlineSoftness = source.outlineSoftness;
        outlineAlphaThreshold = source.outlineAlphaThreshold;
        outlineOpacity = source.outlineOpacity;
        outlineQuality = source.outlineQuality;
        guiOutlineQuality = source.guiOutlineQuality;
        outlineGlowIntensity = source.outlineGlowIntensity;
        outlineBloomEnabled = source.outlineBloomEnabled;
        outlineBloomQuality = source.outlineBloomQuality;
        outlineBloomRadius = source.outlineBloomRadius;
        outlineBloomIntensity = source.outlineBloomIntensity;
        outlineBloomBlurPasses = source.outlineBloomBlurPasses;
        outlineRenderMode = source.outlineRenderMode;
        outlineColorMode = source.outlineColorMode;
        outlinePrimaryColor = source.outlinePrimaryColor;
        outlineSecondaryColor = source.outlineSecondaryColor;
        outlineColorScrollSpeed = source.outlineColorScrollSpeed;
        outlineColorScrollDirection = source.outlineColorScrollDirection;
        outlineColorScrollInterval = source.outlineColorScrollInterval;
        outlineColorScrollMode = source.outlineColorScrollMode;
        outlineSampleSize = source.outlineSampleSize;
        outlineSampleColorCount = source.outlineSampleColorCount;
    }

    public int primaryColor() { return primaryColor; }
    public void setPrimaryColor(int primaryColor) { this.primaryColor = primaryColor; }
    public int secondaryColor() { return secondaryColor; }
    public void setSecondaryColor(int secondaryColor) { this.secondaryColor = secondaryColor; }
    public boolean renderHeldItems() { return renderHeldItems; }
    public void setRenderHeldItems(boolean renderHeldItems) { this.renderHeldItems = renderHeldItems; }
    public boolean renderGuiItems() { return renderGuiItems; }
    public void setRenderGuiItems(boolean renderGuiItems) { this.renderGuiItems = renderGuiItems; }
    public boolean renderThirdPerson() { return renderThirdPerson; }
    public void setRenderThirdPerson(boolean renderThirdPerson) { this.renderThirdPerson = renderThirdPerson; }
    public boolean bloomEnabled() { return bloomEnabled; }
    public void setBloomEnabled(boolean bloomEnabled) { this.bloomEnabled = bloomEnabled; }
    public boolean outlineEnabled() { return outlineEnabled; }
    public void setOutlineEnabled(boolean outlineEnabled) { this.outlineEnabled = outlineEnabled; }
    public boolean outlineMainHand() { return outlineMainHand; }
    public void setOutlineMainHand(boolean outlineMainHand) { this.outlineMainHand = outlineMainHand; }
    public boolean outlineOffHand() { return outlineOffHand; }
    public void setOutlineOffHand(boolean outlineOffHand) { this.outlineOffHand = outlineOffHand; }
    public boolean outlineThirdPerson() { return outlineThirdPerson; }
    public void setOutlineThirdPerson(boolean outlineThirdPerson) { this.outlineThirdPerson = outlineThirdPerson; }
    public boolean outlineGuiItems() { return outlineGuiItems; }
    public void setOutlineGuiItems(boolean outlineGuiItems) { this.outlineGuiItems = outlineGuiItems; }
    public float outlineWidth() { return outlineWidth; }
    public void setOutlineWidth(float outlineWidth) { this.outlineWidth = clamp(outlineWidth, 0.25F, 8.0F); }
    public float outlineSoftness() { return outlineSoftness; }
    public void setOutlineSoftness(float outlineSoftness) { this.outlineSoftness = clamp(outlineSoftness, 0.0F, 1.0F); }
    public float outlineAlphaThreshold() { return outlineAlphaThreshold; }
    public void setOutlineAlphaThreshold(float outlineAlphaThreshold) { this.outlineAlphaThreshold = clamp(outlineAlphaThreshold, 0.0F, 0.95F); }
    public float outlineOpacity() { return outlineOpacity; }
    public void setOutlineOpacity(float outlineOpacity) { this.outlineOpacity = clamp(outlineOpacity, 0.0F, 1.0F); }
    public RenderQuality outlineQuality() { return outlineQuality; }
    public void setOutlineQuality(RenderQuality outlineQuality) { this.outlineQuality = outlineQuality == null ? DEFAULT_OUTLINE_QUALITY : outlineQuality; }
    public RenderQuality guiOutlineQuality() { return guiOutlineQuality; }
    public void setGuiOutlineQuality(RenderQuality quality) { guiOutlineQuality = quality == null ? DEFAULT_GUI_OUTLINE_QUALITY : quality; }
    public float outlineGlowIntensity() { return outlineGlowIntensity; }
    public void setOutlineGlowIntensity(float outlineGlowIntensity) { this.outlineGlowIntensity = clamp(outlineGlowIntensity, 0.0F, 2.0F); }
    public boolean outlineBloomEnabled() { return outlineBloomEnabled; }
    public void setOutlineBloomEnabled(boolean outlineBloomEnabled) { this.outlineBloomEnabled = outlineBloomEnabled; }
    public RenderQuality outlineBloomQuality() { return outlineBloomQuality; }
    public void setOutlineBloomQuality(RenderQuality outlineBloomQuality) { this.outlineBloomQuality = outlineBloomQuality == null ? DEFAULT_OUTLINE_BLOOM_QUALITY : outlineBloomQuality; }
    public float outlineBloomRadius() { return outlineBloomRadius; }
    public void setOutlineBloomRadius(float outlineBloomRadius) { this.outlineBloomRadius = clamp(outlineBloomRadius, 0.25F, 10.0F); }
    public float outlineBloomIntensity() { return outlineBloomIntensity; }
    public void setOutlineBloomIntensity(float outlineBloomIntensity) { this.outlineBloomIntensity = clamp(outlineBloomIntensity, 0.25F, 8.0F); }
    public int outlineBloomBlurPasses() { return outlineBloomBlurPasses; }
    public void setOutlineBloomBlurPasses(int outlineBloomBlurPasses) { this.outlineBloomBlurPasses = Math.max(1, Math.min(6, outlineBloomBlurPasses)); }
    public OutlineRenderMode outlineRenderMode() { return outlineRenderMode; }
    public void setOutlineRenderMode(OutlineRenderMode outlineRenderMode) { this.outlineRenderMode = outlineRenderMode == null ? DEFAULT_OUTLINE_RENDER_MODE : outlineRenderMode; }
    public OutlineColorMode outlineColorMode() { return outlineColorMode; }
    public void setOutlineColorMode(OutlineColorMode outlineColorMode) { this.outlineColorMode = outlineColorMode == null ? DEFAULT_OUTLINE_COLOR_MODE : outlineColorMode; }
    public int outlinePrimaryColor() { return outlinePrimaryColor; }
    public void setOutlinePrimaryColor(int outlinePrimaryColor) { this.outlinePrimaryColor = outlinePrimaryColor; }
    public int outlineSecondaryColor() { return outlineSecondaryColor; }
    public void setOutlineSecondaryColor(int outlineSecondaryColor) { this.outlineSecondaryColor = outlineSecondaryColor; }
    public float outlineColorScrollSpeed() { return outlineColorScrollSpeed; }
    public void setOutlineColorScrollSpeed(float outlineColorScrollSpeed) { this.outlineColorScrollSpeed = clamp(outlineColorScrollSpeed, 0.1F, 2.0F); }
    public float outlineColorScrollDirection() { return outlineColorScrollDirection; }
    public void setOutlineColorScrollDirection(float outlineColorScrollDirection) {
        this.outlineColorScrollDirection = (outlineColorScrollDirection % 360.0F + 360.0F) % 360.0F;
    }
    public float outlineColorScrollInterval() { return outlineColorScrollInterval; }
    public void setOutlineColorScrollInterval(float outlineColorScrollInterval) { this.outlineColorScrollInterval = clamp(outlineColorScrollInterval, 0.25F, 1.5F); }
    public ColorScrollMode outlineColorScrollMode() { return outlineColorScrollMode; }
    public void setOutlineColorScrollMode(ColorScrollMode outlineColorScrollMode) { this.outlineColorScrollMode = outlineColorScrollMode == null ? DEFAULT_OUTLINE_COLOR_SCROLL_MODE : outlineColorScrollMode; }
    public int outlineSampleSize() { return outlineSampleSize; }
    public void setOutlineSampleSize(int outlineSampleSize) { this.outlineSampleSize = Math.max(1, Math.min(8, outlineSampleSize)); }
    public int outlineSampleColorCount() { return outlineSampleColorCount; }
    public void setOutlineSampleColorCount(int outlineSampleColorCount) { this.outlineSampleColorCount = Math.max(1, Math.min(8, outlineSampleColorCount)); }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
