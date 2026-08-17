package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.client.ui.ConfigUiBackground;
import celia.adwadg.itemglintrelight.client.ui.SmoothTextRenderer;
import celia.adwadg.itemglintrelight.client.ui.UiButton;
import celia.adwadg.itemglintrelight.client.ui.UiColorPicker;
import celia.adwadg.itemglintrelight.client.ui.UiColorScrollControl;
import celia.adwadg.itemglintrelight.client.ui.UiCyclingSelector;
import celia.adwadg.itemglintrelight.client.ui.UiDropdown;
import celia.adwadg.itemglintrelight.client.ui.UiMath;
import celia.adwadg.itemglintrelight.client.ui.UiMouseGlow;
import celia.adwadg.itemglintrelight.client.ui.UiPalette;
import celia.adwadg.itemglintrelight.client.ui.UiSlider;
import celia.adwadg.itemglintrelight.client.ui.UiTrailStarParticles;
import celia.adwadg.itemglintrelight.client.ui.UiToggle;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import celia.adwadg.itemglintrelight.client.render.ItemPreviewRenderState;
import celia.adwadg.itemglintrelight.mixin.client.GuiGraphicsAccessor;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.DisplayRule;
import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.OutlineRenderMode;
import celia.adwadg.itemglintrelight.config.ColorScrollMode;
import celia.adwadg.itemglintrelight.config.ui.ItemGlintRelightConfigScreenModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class ItemGlintRelightConfigScreen extends Screen {
    private static final int ITEM_PICKER_X = 156;
    private static final int ITEM_PICKER_Y = 36;
    private static final int ITEM_PICKER_WIDTH = 472;
    private static final int ITEM_PICKER_HEIGHT = 248;
    private static final int ITEM_PICKER_ROW_HEIGHT = 34;
    private static final int PREVIEW_X = 156;
    private static final int PREVIEW_Y = 28;
    private static final int PREVIEW_WIDTH = 472;
    private static final int PREVIEW_HEIGHT = 262;
    private static final int RULE_DIALOG_X = 156;
    private static final int RULE_DIALOG_Y = 36;
    private static final int RULE_DIALOG_WIDTH = 472;
    private static final int RULE_DIALOG_HEIGHT = 248;
    private static final int RIGHT_CONTENT_TOP = 28;
    private static final int RIGHT_CONTENT_BOTTOM = 50;
    private static final int QUICK_PREVIEW_X = 652;
    private static final int QUICK_PREVIEW_EXPANDED_WIDTH = 172;
    private static final int QUICK_PREVIEW_COLLAPSED_WIDTH = 24;
    private final Screen parent;
    private final ItemGlintRelightConfigScreenModel model = new ItemGlintRelightConfigScreenModel();
    private UiButton saveButton;
    private UiToggle outlineToggle;
    private UiToggle mainHandToggle;
    private UiToggle offHandToggle;
    private UiToggle thirdPersonToggle;
    private UiToggle guiItemsToggle;
    private UiSlider outlineWidthSlider;
    private UiSlider outlineSoftnessSlider;
    private UiSlider outlineThresholdSlider;
    private UiSlider outlineOpacitySlider;
    private UiDropdown outlineRenderModeDropdown;
    private UiDropdown outlineQualityDropdown;
    private UiDropdown guiOutlineQualityDropdown;
    private UiDropdown thirdPersonOutlineQualityDropdown;
    private UiSlider outlineGlowIntensitySlider;
    private UiToggle outlineBloomToggle;
    private UiDropdown outlineBloomQualityDropdown;
    private UiSlider outlineBloomRadiusSlider;
    private UiSlider outlineBloomIntensitySlider;
    private UiSlider outlineBloomSoftnessSlider;
    private UiSlider outlineBloomBlurPassesSlider;
    private UiDropdown outlineColorModeDropdown;
    private UiColorPicker outlinePrimaryColorPicker;
    private UiColorPicker outlineSecondaryColorPicker;
    private UiSlider outlineColorScrollSpeedSlider;
    private UiDropdown outlineColorScrollModeDropdown;
    private UiColorScrollControl outlineColorScrollControl;
    private UiSlider outlineColorLengthSlider;
    private UiSlider outlineSampleSizeSlider;
    private UiSlider outlineSampleColorCountSlider;
    private UiMouseGlow mouseGlow;
    private UiTrailStarParticles starParticles;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int sidebarRight;
    private float uiScale;
    private float originX;
    private float originY;
    private Page page = Page.GENERAL;
    private float pageTransition = 1.0F;
    private long lastPageTransitionFrame;
    private float renderScroll;
    private float renderScrollTarget;
    private int renderContentHeight;
    private long lastRenderScrollFrame;
    private final float[] navigationFill = new float[Page.values().length];
    private long lastNavigationFrame;
    private float previewPitch = -25.0F;
    private float previewYaw = -35.0F;
    private float previewRoll;
    private float previewOffsetX;
    private float previewOffsetY;
    private float previewZoom = 72.0F;
    private boolean draggingPreview;
    private boolean panningPreview;
    private boolean rollingPreview;
    private ItemStack previewItem = new ItemStack(Items.DIAMOND_SWORD);
    private final List<ItemChoice> allItemChoices = new ArrayList<>();
    private List<ItemChoice> filteredItemChoices = List.of();
    private boolean itemPickerOpen;
    private float itemPickerAnimation;
    private String itemSearch = "";
    private int itemSearchCursor;
    private float itemPickerScroll;
    private float itemPickerScrollTarget;
    private float previewItemNameHover;
    private long lastPreviewNameFrame;
    private ItemStack quickPreviewItem = new ItemStack(Items.DIAMOND_SWORD);
    private float quickPreviewPitch;
    private float quickPreviewYaw;
    private float quickPreviewRoll = 45.0F;
    private float quickPreviewZoom = 9.0F;
    private boolean draggingQuickPreview;
    private boolean rollingQuickPreview;
    private boolean quickPreviewCollapsed;
    private float quickPreviewWidth = QUICK_PREVIEW_EXPANDED_WIDTH;
    private float quickPreviewVisibility = 1.0F;
    private long lastQuickPreviewFrame;
    private float quickPreviewToggleHover;
    private long lastPickerScrollFrame;
    private final List<RuleTargetChoice> allRuleTargetChoices = new ArrayList<>();
    private List<RuleTargetChoice> filteredRuleTargetChoices = List.of();
    private boolean ruleTargetPickerOpen;
    private float ruleTargetPickerAnimation;
    private String ruleTargetSearch = "";
    private int ruleTargetSearchCursor;
    private float ruleTargetPickerScroll;
    private float ruleTargetPickerScrollTarget;
    private long lastRuleTargetPickerFrame;
    private UiDropdown ruleModeDropdown;
    private boolean ruleDialogOpen;
    private RuleMatchMode ruleMatchMode = RuleMatchMode.WHITELIST;
    private RuleInputFocus ruleInputFocus = RuleInputFocus.NONE;
    private int ruleInputCursor;
    private String ruleName = "";
    private String ruleItemId = "";
    private String ruleNbtPath = "";
    private String ruleNbtValue = "";
    private final List<NbtConditionDraft> additionalNbtConditions = new ArrayList<>();
    private int additionalNbtConditionIndex = -1;
    private String rulePriority = "";
    private String ruleOutlineParameters = "";
    private String editingRuleId;
    private boolean ruleOutlineDialogOpen;
    private ItemGlintRelightConfig ruleOutlineSettings;
    private ItemGlintRelightConfig ruleOutlineOriginal;
    private NbtMatchMode ruleNbtMatchMode = NbtMatchMode.EQUAL;
    private UiCyclingSelector primaryNbtMatchModeSelector;
    private final List<UiCyclingSelector> additionalNbtMatchModeSelectors = new ArrayList<>();
    private float addRuleHover;
    private float ruleListScroll;
    private float ruleListScrollTarget;
    private long lastRuleListFrame;
    private float presetHover;
    private boolean presetDialogOpen;
    private float presetDialogAnimation;
    private long lastPresetDialogFrame;
    private long lastRuleFrame;
    private float ruleDialogAnimation;
    private long lastRuleDialogFrame;
    private float ruleDialogScroll;
    private float ruleDialogScrollTarget;
    private int ruleDialogContentHeight;
    private float ruleDialogDelta;
    private final Map<String, Float> ruleHoverAmounts = new HashMap<>();
    private NbtMatchMode previousNbtMatchMode = NbtMatchMode.EQUAL;
    private float nbtMatchModeTransition = 1.0F;
    private int nbtMatchModeDirection = 1;
    private float nbtPreviousArrowHover;
    private float nbtNextArrowHover;
    private float nbtCenterHover;
    private boolean nbtMatchModeExpanded;
    private float nbtMatchModeExpansion;
    private final float[] nbtMatchModeOptionHovers = new float[NbtMatchMode.values().length];
    private int additionalNbtMatchModeExpandedIndex = -1;
    private float additionalNbtMatchModeExpansion;
    private final float[] additionalNbtMatchModeOptionHovers = new float[NbtMatchMode.values().length];
    private long lastNbtMatchModeFrame;

    public ItemGlintRelightConfigScreen(Screen parent) {
        super(Component.literal("ItemGlintRelight"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = 0;
        top = 0;
        right = 640;
        bottom = 340;
        sidebarRight = 144;
        uiScale = Math.min(1.0F, Math.min((this.width - 28.0F) / (QUICK_PREVIEW_X + QUICK_PREVIEW_EXPANDED_WIDTH + 4.0F), (this.height - 28.0F) / (bottom + 4.0F)));
        uiScale = Math.max(0.35F, uiScale);
        originX = (this.width - (QUICK_PREVIEW_X + QUICK_PREVIEW_EXPANDED_WIDTH) * uiScale) / 2.0F;
        originY = (this.height - bottom * uiScale) / 2.0F;
        saveButton = new UiButton(right - 104, bottom - 40, 80, 22, tr("ui.itemglintrelight.save"), () -> {
            model.save();
            onClose();
        });
        int contentX = sidebarRight + 24;
        int contentWidth = right - contentX - 24;
        outlineToggle = new UiToggle(contentX, top + 44, contentWidth, tr("ui.itemglintrelight.outline.enabled"),
                () -> model.draft().outlineEnabled(), value -> model.draft().setOutlineEnabled(value));
        mainHandToggle = new UiToggle(contentX, top + 78, contentWidth, tr("ui.itemglintrelight.outline.main_hand"),
                () -> model.draft().outlineMainHand(), value -> model.draft().setOutlineMainHand(value));
        offHandToggle = new UiToggle(contentX, top + 104, contentWidth, tr("ui.itemglintrelight.outline.off_hand"),
                () -> model.draft().outlineOffHand(), value -> model.draft().setOutlineOffHand(value));
        thirdPersonToggle = new UiToggle(contentX, top + 130, contentWidth, tr("ui.itemglintrelight.outline.third_person"),
                () -> model.draft().outlineThirdPerson(), value -> model.draft().setOutlineThirdPerson(value));
        guiItemsToggle = new UiToggle(contentX, top + 156, contentWidth, tr("ui.itemglintrelight.outline.gui_items"),
                () -> model.draft().outlineGuiItems(), value -> model.draft().setOutlineGuiItems(value));
        outlineWidthSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.outline_width"), 0.25D, 8.0D, 0.05D,
                () -> model.draft().outlineWidth(), value -> model.draft().setOutlineWidth(value.floatValue()));
        outlineSoftnessSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.softness"), 0.0D, 1.0D, 0.01D,
                () -> model.draft().outlineSoftness(), value -> model.draft().setOutlineSoftness(value.floatValue()));
        outlineThresholdSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.alpha_threshold"), 0.0D, 0.95D, 0.01D,
                () -> model.draft().outlineAlphaThreshold(), value -> model.draft().setOutlineAlphaThreshold(value.floatValue()));
        outlineOpacitySlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.outline_opacity"), 0.0D, 100.0D, 1.0D,
                () -> model.draft().outlineOpacity() * 100.0D, value -> model.draft().setOutlineOpacity(value.floatValue() / 100.0F));
        List<String> renderModeOptions = List.of(tr("ui.itemglintrelight.render_mode.flat"), tr("ui.itemglintrelight.render_mode.cubic"));
        outlineRenderModeDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.render_mode"), renderModeOptions,
                model.draft().outlineRenderMode().ordinal(), value -> model.draft().setOutlineRenderMode(OutlineRenderMode.values()[value]));
        List<String> qualityOptions = List.of(tr("ui.itemglintrelight.quality.low"), tr("ui.itemglintrelight.quality.medium"), tr("ui.itemglintrelight.quality.high"));
        outlineQualityDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.outline_quality"), qualityOptions,
                model.draft().outlineQuality().ordinal(), value -> model.draft().setOutlineQuality(RenderQuality.values()[value]));
        guiOutlineQualityDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.gui_outline_quality"), qualityOptions,
                model.draft().guiOutlineQuality().ordinal(), value -> model.draft().setGuiOutlineQuality(RenderQuality.values()[value]));
        thirdPersonOutlineQualityDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.third_person_outline_quality"), qualityOptions,
                model.draft().thirdPersonOutlineQuality().ordinal(), value -> model.draft().setThirdPersonOutlineQuality(RenderQuality.values()[value]));
        outlineGlowIntensitySlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.glow_intensity"), 0.0D, 2.0D, 0.01D,
                () -> model.draft().outlineGlowIntensity(), value -> model.draft().setOutlineGlowIntensity(value.floatValue()));
        outlineBloomToggle = new UiToggle(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_enabled"),
                () -> model.draft().outlineBloomEnabled(), value -> model.draft().setOutlineBloomEnabled(value));
        outlineBloomQualityDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_quality"), qualityOptions,
                model.draft().outlineBloomQuality().ordinal(), value -> model.draft().setOutlineBloomQuality(RenderQuality.values()[value]));
        outlineBloomRadiusSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_radius"), 0.25D, 10.0D, 0.05D,
                () -> model.draft().outlineBloomRadius(), value -> model.draft().setOutlineBloomRadius(value.floatValue()));
        outlineBloomIntensitySlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_intensity"), 0.0D, 1.5D, 0.01D,
                () -> model.draft().outlineBloomIntensity(), value -> model.draft().setOutlineBloomIntensity(value.floatValue()));
        outlineBloomSoftnessSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_softness"), 0.0D, 1.0D, 0.01D,
                () -> model.draft().outlineBloomSoftness(), value -> model.draft().setOutlineBloomSoftness(value.floatValue()));
        outlineBloomBlurPassesSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_blur_passes"), 1.0D, 6.0D, 1.0D,
                () -> model.draft().outlineBloomBlurPasses(), value -> model.draft().setOutlineBloomBlurPasses(value.intValue()));
        List<String> colorModeOptions = List.of(tr("ui.itemglintrelight.color_mode.single"), tr("ui.itemglintrelight.color_mode.dual"),
                tr("ui.itemglintrelight.color_mode.rainbow"), tr("ui.itemglintrelight.color_mode.texture_sample"));
        outlineColorModeDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.color_mode"), colorModeOptions,
                model.draft().outlineColorMode().ordinal(), value -> model.draft().setOutlineColorMode(OutlineColorMode.values()[value]));
        outlinePrimaryColorPicker = new UiColorPicker(contentX, top + 80, 132, tr("ui.itemglintrelight.render.primary_color"),
                () -> model.draft().outlinePrimaryColor(), value -> model.draft().setOutlinePrimaryColor(value));
        outlineSecondaryColorPicker = new UiColorPicker(contentX, top + 80, 132, tr("ui.itemglintrelight.render.secondary_color"),
                () -> model.draft().outlineSecondaryColor(), value -> model.draft().setOutlineSecondaryColor(value));
        outlineColorScrollSpeedSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.color_scroll_speed"), 0.1D, 2.0D, 0.01D,
                () -> model.draft().outlineColorScrollSpeed(), value -> model.draft().setOutlineColorScrollSpeed(value.floatValue()));
        List<String> scrollModeOptions = List.of(tr("ui.itemglintrelight.color_scroll_mode.planar"), tr("ui.itemglintrelight.color_scroll_mode.outline"));
        outlineColorScrollModeDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.color_scroll_mode"), scrollModeOptions,
                model.draft().outlineColorScrollMode().ordinal(), value -> model.draft().setOutlineColorScrollMode(ColorScrollMode.values()[value]));
        outlineColorScrollControl = new UiColorScrollControl(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.color_scroll_pattern"),
                () -> model.draft().outlineColorScrollDirection(), () -> model.draft().outlineColorScrollInterval(),
                (direction, interval) -> {
                    model.draft().setOutlineColorScrollDirection(direction);
                    model.draft().setOutlineColorScrollInterval(interval);
                });
        outlineColorLengthSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.color_length"), 0.25D, 1.5D, 0.01D,
                () -> model.draft().outlineColorScrollInterval(), value -> model.draft().setOutlineColorScrollInterval(value.floatValue()));
        outlineSampleSizeSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.sample_size"), 1.0D, 8.0D, 1.0D,
                () -> model.draft().outlineSampleSize(), value -> model.draft().setOutlineSampleSize(value.intValue()));
        outlineSampleColorCountSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.sample_color_count"), 1.0D, 8.0D, 1.0D,
                () -> model.draft().outlineSampleColorCount(), value -> model.draft().setOutlineSampleColorCount(value.intValue()));
        ruleModeDropdown = new UiDropdown(0, 0, RULE_DIALOG_WIDTH - 36, tr("ui.itemglintrelight.rules.match_mode"),
                List.of(tr("ui.itemglintrelight.rules.mode.whitelist"), tr("ui.itemglintrelight.rules.mode.nbt_match"), tr("ui.itemglintrelight.rules.mode.blacklist")),
                ruleMatchMode.ordinal(), value -> ruleMatchMode = RuleMatchMode.values()[value]);
        primaryNbtMatchModeSelector = new UiCyclingSelector(0, 0, 76, nbtMatchModeSymbols(), ruleNbtMatchMode.ordinal(),
                value -> ruleNbtMatchMode = NbtMatchMode.values()[value]);
        mouseGlow = new UiMouseGlow();
        starParticles = new UiTrailStarParticles();
        rebuildItemChoices();
        rebuildRuleTargetChoices();
        navigationFill[page.ordinal()] = 1.0F;
        lastNavigationFrame = System.nanoTime();
        lastPageTransitionFrame = System.nanoTime();
        lastPreviewNameFrame = System.nanoTime();
        lastQuickPreviewFrame = System.nanoTime();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.getWindow().isIconified()) {
            return;
        }
        updateQuickPreviewLayout();
        ConfigUiBackground.renderBackdrop(graphics, width, height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(uiScale, uiScale);
        ConfigUiBackground.renderPanel(graphics, left, top, right, bottom, sidebarRight);
        graphics.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        float localMouseX = (mouseX - originX) / uiScale;
        float localMouseY = (mouseY - originY) / uiScale;
        mouseGlow.render(graphics, localMouseX, localMouseY);
        starParticles.update(localMouseX, localMouseY);
        starParticles.render(graphics, System.nanoTime());
        graphics.disableScissor();
        renderStaticShell(graphics);
        int logicalMouseX = Math.round((mouseX - originX) / uiScale);
        int logicalMouseY = Math.round((mouseY - originY) / uiScale);
        renderPageContent(graphics, logicalMouseX, logicalMouseY);
        renderQuickPreview(graphics, logicalMouseX, logicalMouseY);
        saveButton.render(graphics, font, logicalMouseX, logicalMouseY);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.save_hint"), sidebarRight + 24, bottom - 35, 0.66F, UiPalette.MUTED_TEXT);
        if (itemPickerOpen) {
            renderItemPicker(graphics, logicalMouseX, logicalMouseY);
        }
        if (ruleDialogOpen) {
            renderRuleDialog(graphics, logicalMouseX, logicalMouseY);
        }
        if (ruleOutlineDialogOpen) {
            renderRuleOutlineDialog(graphics, logicalMouseX, logicalMouseY);
        }
        if (presetDialogOpen) {
            renderPresetDialog(graphics, logicalMouseX, logicalMouseY);
        }
        if (ruleTargetPickerOpen) {
            renderRuleTargetPicker(graphics, logicalMouseX, logicalMouseY);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = (event.x() - originX) / uiScale;
        double mouseY = (event.y() - originY) / uiScale;
        if (itemPickerOpen) {
            return handleItemPickerClick(mouseX, mouseY, event.button());
        }
        if (ruleTargetPickerOpen) {
            return handleRuleTargetPickerClick(mouseX, mouseY, event.button());
        }
        if (presetDialogOpen) {
            if (event.button() == 0) {
                if (UiMath.contains(RULE_DIALOG_X + 18, RULE_DIALOG_Y + 46, RULE_DIALOG_WIDTH - 36, 42, mouseX, mouseY)) {
                    applySimpleEnchantmentPreset();
                }
                if (UiMath.contains(RULE_DIALOG_X + 18, RULE_DIALOG_Y + 96, RULE_DIALOG_WIDTH - 36, 42, mouseX, mouseY)) {
                    applyToolOutlinePreset();
                }
                if (UiMath.contains(RULE_DIALOG_X + 18, RULE_DIALOG_Y + 146, RULE_DIALOG_WIDTH - 36, 42, mouseX, mouseY)) {
                    applyAdvancedEnchantmentPreset();
                }
                presetDialogOpen = false;
            }
            return true;
        }
        if (ruleOutlineDialogOpen) {
            return handleRuleOutlineDialogClick(mouseX, mouseY, event.button());
        }
        if (ruleDialogOpen) {
            return handleRuleDialogClick(mouseX, mouseY, event.button());
        }
        if (isQuickPreviewToggle(mouseX, mouseY)) {
            quickPreviewCollapsed = !quickPreviewCollapsed;
            return true;
        }
        if (isInQuickPreviewCanvas(mouseX, mouseY)) {
            if (event.button() == 0) draggingQuickPreview = true;
            if (event.button() == 1) rollingQuickPreview = true;
            return true;
        }
        Page nextPage = pageAt(mouseX, mouseY);
        if (nextPage != null) {
            if (nextPage != page) {
                pageTransition = 0.0F;
            }
            page = nextPage;
            return true;
        }
        if (saveButton.mouseClicked(mouseX, mouseY, event.button())) {
            return true;
        }
        if (page == Page.RULES && isAddRuleButton(mouseX, mouseY)) {
            openNewRuleDialog();
            return true;
        }
        if (page == Page.RULES && isPresetButton(mouseX, mouseY)) {
            presetDialogOpen = true;
            presetDialogAnimation = 0.0F;
            lastPresetDialogFrame = System.nanoTime();
            return true;
        }
        if (page == Page.RULES && handleRuleEntryClick(mouseX, mouseY)) return true;
        if (page == Page.GENERAL && outlineToggle.mouseClicked(mouseX, mouseY, event.button())) {
            return true;
        }
        if (page == Page.GENERAL && model.draft().outlineEnabled()) {
            if (mainHandToggle.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (offHandToggle.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (thirdPersonToggle.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (guiItemsToggle.mouseClicked(mouseX, mouseY, event.button())) return true;
        }
        if (page == Page.PREVIEW && isPreviewItemLabel(mouseX, mouseY)) {
            openItemPicker();
            return true;
        }
        if (page == Page.PREVIEW && isInPreviewCanvas(mouseX, mouseY)) {
            if (event.button() == 0) draggingPreview = true;
            if (event.button() == 2) panningPreview = true;
            if (event.button() == 1) rollingPreview = true;
            return event.button() == 0 || event.button() == 1 || event.button() == 2;
        }
        if (page == Page.RENDER) {
            if (outlineColorModeDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineRenderModeDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineQualityDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (model.draft().outlineThirdPerson() && thirdPersonOutlineQualityDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (model.draft().outlineGuiItems() && guiOutlineQualityDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesColorAnimation() && outlineColorScrollModeDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (model.draft().outlineBloomEnabled() && outlineBloomQualityDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesPrimaryColor() && outlinePrimaryColorPicker.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesSecondaryColor() && outlineSecondaryColorPicker.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineBloomToggle.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineWidthSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineSoftnessSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineThresholdSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineOpacitySlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesColorAnimation() && outlineColorScrollSpeedSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesPlanarColorScroll() && outlineColorScrollControl.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesOutlineColorScroll() && outlineColorLengthSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesTextureSampling() && outlineSampleSizeSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesTextureSampling() && outlineSampleColorCountSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineGlowIntensitySlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (model.draft().outlineBloomEnabled()) {
                if (outlineBloomRadiusSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
                if (outlineBloomIntensitySlider.mouseClicked(mouseX, mouseY, event.button())) return true;
                if (outlineBloomSoftnessSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
                if (outlineBloomBlurPassesSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            }
        }
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = (event.x() - originX) / uiScale;
        double mouseY = (event.y() - originY) / uiScale;
        if (ruleOutlineDialogOpen) {
            if (usesPrimaryColor() && outlinePrimaryColorPicker.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesSecondaryColor() && outlineSecondaryColorPicker.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineWidthSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineSoftnessSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineThresholdSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineOpacitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesColorAnimation() && outlineColorScrollSpeedSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesPlanarColorScroll() && outlineColorScrollControl.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesOutlineColorScroll() && outlineColorLengthSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesTextureSampling() && outlineSampleSizeSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesTextureSampling() && outlineSampleColorCountSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineGlowIntensitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomRadiusSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomIntensitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomSoftnessSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomBlurPassesSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
        }
        if (draggingQuickPreview && event.button() == 0) {
            quickPreviewYaw += (float) dragX / uiScale * 0.8F;
            quickPreviewPitch = Math.max(-89.0F, Math.min(89.0F, quickPreviewPitch + (float) dragY / uiScale * 0.8F));
            return true;
        }
        if (rollingQuickPreview && event.button() == 1) {
            quickPreviewRoll += (float) dragX / uiScale * 0.8F;
            return true;
        }
        if (page == Page.RENDER) {
            if (usesPrimaryColor() && outlinePrimaryColorPicker.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesSecondaryColor() && outlineSecondaryColorPicker.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineWidthSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineSoftnessSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineThresholdSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineOpacitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineColorScrollSpeedSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesPlanarColorScroll() && outlineColorScrollControl.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesOutlineColorScroll() && outlineColorLengthSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineSampleSizeSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineSampleColorCountSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineGlowIntensitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomRadiusSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomIntensitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomSoftnessSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomBlurPassesSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
        }
        if (page == Page.PREVIEW) {
            if (draggingPreview && event.button() == 0) {
                previewYaw += (float) dragX / uiScale * 0.8F;
                previewPitch = Math.max(-89.0F, Math.min(89.0F, previewPitch + (float) dragY / uiScale * 0.8F));
                return true;
            }
            if (panningPreview && event.button() == 2) {
                previewOffsetX += (float) dragX / uiScale;
                previewOffsetY += (float) dragY / uiScale;
                return true;
            }
            if (rollingPreview && event.button() == 1) {
                previewRoll += (float) dragX / uiScale * 0.8F;
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPreview = false;
        panningPreview = false;
        rollingPreview = false;
        draggingQuickPreview = false;
        rollingQuickPreview = false;
        outlineWidthSlider.stopDragging();
        outlineSoftnessSlider.stopDragging();
        outlineThresholdSlider.stopDragging();
        outlineOpacitySlider.stopDragging();
        outlineColorScrollSpeedSlider.stopDragging();
        outlineColorScrollControl.stopDragging();
        outlineColorLengthSlider.stopDragging();
        outlineSampleSizeSlider.stopDragging();
        outlineSampleColorCountSlider.stopDragging();
        outlineGlowIntensitySlider.stopDragging();
        outlineBloomRadiusSlider.stopDragging();
        outlineBloomIntensitySlider.stopDragging();
        outlineBloomSoftnessSlider.stopDragging();
        outlineBloomBlurPassesSlider.stopDragging();
        outlinePrimaryColorPicker.stopDragging();
        outlineSecondaryColorPicker.stopDragging();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double localMouseX = (mouseX - originX) / uiScale;
        double localMouseY = (mouseY - originY) / uiScale;
        if (itemPickerOpen) {
            itemPickerScrollTarget = clampItemPickerScroll(itemPickerScrollTarget - (float) verticalAmount * 2.5F);
            return true;
        }
        if (ruleTargetPickerOpen) {
            ruleTargetPickerScrollTarget = clampRuleTargetPickerScroll(ruleTargetPickerScrollTarget - (float) verticalAmount * 2.5F);
            return true;
        }
        if (ruleOutlineDialogOpen) {
            renderScrollTarget = Math.max(0.0F, Math.min(maxRenderScroll(), renderScrollTarget - (float) verticalAmount * 48.0F));
            return true;
        }
        if (ruleDialogOpen) {
            ruleDialogScrollTarget = clampRuleDialogScroll(ruleDialogScrollTarget - (float) verticalAmount * 28.0F);
            return true;
        }
        if (page == Page.RENDER && localMouseX >= sidebarRight && localMouseX < right
                && localMouseY >= top + RIGHT_CONTENT_TOP && localMouseY < bottom - RIGHT_CONTENT_BOTTOM) {
            renderScrollTarget = Math.max(0.0F, Math.min(maxRenderScroll(), renderScrollTarget - (float) verticalAmount * 48.0F));
            return true;
        }
        if (page == Page.RULES && localMouseX >= sidebarRight + 24 && localMouseX < right - 24
                && localMouseY >= top + 76 && localMouseY < bottom - 58) {
            int viewportHeight = bottom - 58 - (top + 76);
            int maximum = Math.max(0, DisplayRuleManager.rulesByPriority().size() * 44 - viewportHeight);
            ruleListScrollTarget = Math.max(0.0F, Math.min(maximum, ruleListScrollTarget - (float) verticalAmount * 36.0F));
            return true;
        }
        if (page == Page.PREVIEW && isInPreviewCanvas(localMouseX, localMouseY)) {
            zoomPreview((float) localMouseX, (float) localMouseY, (float) verticalAmount);
            return true;
        }
        if (isInQuickPreviewCanvas(localMouseX, localMouseY)) {
            quickPreviewZoom = Math.max(0.5F, Math.min(16.0F, quickPreviewZoom + (float) verticalAmount * 0.5F));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (presetDialogOpen) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) presetDialogOpen = false;
            return true;
        }
        if (ruleTargetPickerOpen) {
            return handleRuleTargetPickerKey(event);
        }
        if (ruleOutlineDialogOpen) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                closeRuleOutlineDialog(false);
            }
            return true;
        }
        if (ruleDialogOpen) {
            return handleRuleDialogKey(event);
        }
        if (!itemPickerOpen) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeItemPicker();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !itemSearch.isEmpty()) {
            if (itemSearchCursor > 0) {
                itemSearch = itemSearch.substring(0, itemSearchCursor - 1) + itemSearch.substring(itemSearchCursor);
                itemSearchCursor--;
            }
            filterItemChoices();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            itemSearchCursor = Math.max(0, itemSearchCursor - 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            itemSearchCursor = Math.min(itemSearch.length(), itemSearchCursor + 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && !filteredItemChoices.isEmpty()) {
            selectPreviewItem(filteredItemChoices.get(0));
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (ruleOutlineDialogOpen) {
            return true;
        }
        if (ruleTargetPickerOpen) {
            if (event.isAllowedChatCharacter() && ruleTargetSearch.length() < 96) {
                ruleTargetSearch = ruleTargetSearch.substring(0, ruleTargetSearchCursor) + event.codepointAsString()
                        + ruleTargetSearch.substring(ruleTargetSearchCursor);
                ruleTargetSearchCursor += event.codepointAsString().length();
                filterRuleTargetChoices();
            }
            return true;
        }
        if (ruleDialogOpen) {
            if (event.isAllowedChatCharacter()) {
                appendRuleInput(event.codepointAsString());
            }
            return true;
        }
        if (!itemPickerOpen) return super.charTyped(event);
        if (event.isAllowedChatCharacter() && itemSearch.length() < 96) {
            itemSearch = itemSearch.substring(0, itemSearchCursor) + event.codepointAsString() + itemSearch.substring(itemSearchCursor);
            itemSearchCursor += event.codepointAsString().length();
            filterItemChoices();
        }
        return true;
    }

    @Override
    public void onClose() {
        HeldItemOutlineRenderer.clearQueuedPreview();
        if (mouseGlow != null) {
            mouseGlow.close();
        }
        if (starParticles != null) {
            starParticles.clear();
        }
        if (outlineColorScrollControl != null) {
            outlineColorScrollControl.close();
        }
        SmoothTextRenderer.clear();
        this.minecraft.setScreen(parent);
    }

    private void renderStaticShell(GuiGraphics graphics) {
        updateNavigationAnimation();
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.brand"), left + 24, top + 18, 0.92F, UiPalette.TEXT);
        SmoothTextRenderer.drawGradient(graphics, font, tr("ui.itemglintrelight.relight"), left + 24, top + 38, 0.60F);
        drawPageLabel(graphics, Page.GENERAL, top + 88);
        drawPageLabel(graphics, Page.RENDER, top + 118);
        drawPageLabel(graphics, Page.RULES, top + 148);
        drawPageLabel(graphics, Page.PREVIEW, top + 178);
    }

    private void renderPageContent(GuiGraphics graphics, int mouseX, int mouseY) {
        updatePageTransition();
        graphics.enableScissor(sidebarRight + 12, top + RIGHT_CONTENT_TOP, right - 12, bottom - RIGHT_CONTENT_BOTTOM);
        graphics.pose().pushMatrix();
        graphics.pose().translate(Math.round((1.0F - pageTransition) * 24.0F), 0.0F);
        if (page != Page.GENERAL) {
            if (page == Page.RENDER) {
                layoutRenderControls();
                outlineWidthSlider.render(graphics, font, mouseX, mouseY);
                outlineSoftnessSlider.render(graphics, font, mouseX, mouseY);
                outlineThresholdSlider.render(graphics, font, mouseX, mouseY);
                outlineOpacitySlider.render(graphics, font, mouseX, mouseY);
                outlineRenderModeDropdown.render(graphics, font, mouseX, mouseY);
                outlineColorModeDropdown.render(graphics, font, mouseX, mouseY);
                if (usesPrimaryColor()) outlinePrimaryColorPicker.render(graphics, font, mouseX, mouseY);
                if (usesSecondaryColor()) outlineSecondaryColorPicker.render(graphics, font, mouseX, mouseY);
                if (usesColorAnimation()) outlineColorScrollSpeedSlider.render(graphics, font, mouseX, mouseY);
                if (usesColorAnimation()) outlineColorScrollModeDropdown.render(graphics, font, mouseX, mouseY);
                if (usesPlanarColorScroll()) outlineColorScrollControl.render(graphics, font, mouseX, mouseY);
                if (usesOutlineColorScroll()) outlineColorLengthSlider.render(graphics, font, mouseX, mouseY);
                if (usesTextureSampling()) {
                    outlineSampleSizeSlider.render(graphics, font, mouseX, mouseY);
                    outlineSampleColorCountSlider.render(graphics, font, mouseX, mouseY);
                }
                outlineQualityDropdown.render(graphics, font, mouseX, mouseY);
                if (model.draft().outlineThirdPerson()) thirdPersonOutlineQualityDropdown.render(graphics, font, mouseX, mouseY);
                if (model.draft().outlineGuiItems()) guiOutlineQualityDropdown.render(graphics, font, mouseX, mouseY);
                outlineGlowIntensitySlider.render(graphics, font, mouseX, mouseY);
                outlineBloomToggle.render(graphics, font, mouseX, mouseY);
                if (model.draft().outlineBloomEnabled()) {
                    outlineBloomQualityDropdown.render(graphics, font, mouseX, mouseY);
                    outlineBloomRadiusSlider.render(graphics, font, mouseX, mouseY);
                    outlineBloomIntensitySlider.render(graphics, font, mouseX, mouseY);
                    outlineBloomSoftnessSlider.render(graphics, font, mouseX, mouseY);
                    outlineBloomBlurPassesSlider.render(graphics, font, mouseX, mouseY);
                }
            } else if (page == Page.PREVIEW) {
                renderPreview(graphics, mouseX, mouseY);
            } else if (page == Page.RULES) {
                renderRules(graphics, mouseX, mouseY);
            }
            graphics.pose().popMatrix();
            graphics.disableScissor();
            if (page == Page.RENDER) renderScrollBar(graphics);
            return;
        }
        outlineToggle.render(graphics, font, mouseX, mouseY);
        if (model.draft().outlineEnabled()) {
            mainHandToggle.render(graphics, font, mouseX, mouseY);
            offHandToggle.render(graphics, font, mouseX, mouseY);
            thirdPersonToggle.render(graphics, font, mouseX, mouseY);
            guiItemsToggle.render(graphics, font, mouseX, mouseY);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();
    }

    private void updatePageTransition() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastPageTransitionFrame) / 1_000_000_000.0F);
        lastPageTransitionFrame = now;
        pageTransition = UiMath.approach(pageTransition, 1.0F, delta, 14.0F);
    }

    private void renderRules(GuiGraphics graphics, int mouseX, int mouseY) {
        updateRuleButtonAnimations(mouseX, mouseY);
        int presetX = sidebarRight + 24;
        int presetY = top + 40;
        String presetLabel = tr("ui.itemglintrelight.rules.presets");
        graphics.fill(presetX, presetY, presetX + 58, presetY + 24, UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, presetHover));
        graphics.fill(presetX + 1, presetY + 1, presetX + 57, presetY + 23, UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, presetHover));
        SmoothTextRenderer.drawCentered(graphics, font, presetLabel, presetX + 29.0F,
                presetY + (24 - SmoothTextRenderer.height(presetLabel, 0.62F, UiPalette.TEXT)) * 0.5F, 0.62F,
                UiMath.mix(UiPalette.MUTED_TEXT, UiPalette.TEXT, presetHover));
        int buttonRight = right - 24;
        int buttonWidth = Math.round(24.0F + addRuleHover * 66.0F);
        int buttonX = buttonRight - buttonWidth;
        int buttonY = top + 40;
        graphics.fill(buttonX, buttonY, buttonRight, buttonY + 24, UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, addRuleHover));
        graphics.fill(buttonX + 1, buttonY + 1, buttonRight - 1, buttonY + 23, UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, addRuleHover));
        String plus = "+";
        SmoothTextRenderer.drawCentered(graphics, font, plus, buttonRight - 12.0F,
                buttonY + (24 - SmoothTextRenderer.height(plus, 1.05F, UiPalette.PALE_BLUE)) / 2.0F, 1.05F, UiPalette.PALE_BLUE);
        if (addRuleHover > 0.05F) {
            graphics.enableScissor(buttonX, buttonY, buttonRight, buttonY + 24);
            SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.rules.add"), buttonX + 9, buttonY + 7, 0.62F,
                    UiMath.mix(UiPalette.MUTED_TEXT, UiPalette.TEXT, addRuleHover));
            graphics.disableScissor();
        }
        List<DisplayRule> rules = DisplayRuleManager.rulesByPriority();
        if (rules.isEmpty()) {
            SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.rules.empty"), sidebarRight + 24, top + 82, 0.72F, UiPalette.MUTED_TEXT);
            return;
        }
        int x = sidebarRight + 24;
        int width = right - x - 24;
        int viewportTop = top + 76;
        int viewportBottom = bottom - 58;
        updateRuleListScroll(rules.size(), viewportBottom - viewportTop);
        int y = viewportTop - Math.round(ruleListScroll);
        graphics.enableScissor(x, viewportTop, x + width, viewportBottom);
        for (DisplayRule rule : rules) {
            graphics.fill(x, y, x + width, y + 38, UiPalette.DIVIDER);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 37, UiPalette.SURFACE);
            String primary = rule.name().isBlank() ? (rule.target().isBlank() ? rule.nbtPath() : rule.target()) : rule.name();
            SmoothTextRenderer.draw(graphics, font, truncate(primary, width - 72, 0.70F), x + 10, y + 7, 0.70F, UiPalette.TEXT);
            String secondary = tr("ui.itemglintrelight.rules.mode." + rule.mode().name().toLowerCase(Locale.ROOT))
                    + "  P" + rule.priority() + "  " + rule.id().substring(0, 8);
            SmoothTextRenderer.draw(graphics, font, truncate(secondary, width - 72, 0.52F), x + 10, y + 21, 0.52F, UiPalette.MUTED_TEXT);
            graphics.fill(x + width - 52, y + 8, x + width - 30, y + 30, UiPalette.DIVIDER);
            graphics.fill(x + width - 51, y + 9, x + width - 31, y + 29, UiPalette.SURFACE);
            SmoothTextRenderer.draw(graphics, font, "E", x + width - 45, y + 14, 0.58F, UiPalette.PALE_BLUE);
            graphics.fill(x + width - 26, y + 8, x + width - 4, y + 30, UiPalette.DIVIDER);
            graphics.fill(x + width - 25, y + 9, x + width - 5, y + 29, UiPalette.SURFACE);
            SmoothTextRenderer.draw(graphics, font, "x", x + width - 19, y + 14, 0.58F, UiPalette.MUTED_TEXT);
            y += 44;
        }
        graphics.disableScissor();
        renderRuleListScrollBar(graphics, x, width, viewportTop, viewportBottom, rules.size());
    }

    private void updateRuleListScroll(int ruleCount, int viewportHeight) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRuleListFrame) / 1_000_000_000.0F);
        lastRuleListFrame = now;
        float maximum = Math.max(0, ruleCount * 44 - viewportHeight);
        ruleListScrollTarget = Math.max(0.0F, Math.min(maximum, ruleListScrollTarget));
        ruleListScroll = UiMath.approach(ruleListScroll, ruleListScrollTarget, delta, 18.0F);
    }

    private void renderRuleListScrollBar(GuiGraphics graphics, int x, int width, int viewportTop, int viewportBottom, int ruleCount) {
        int viewportHeight = viewportBottom - viewportTop;
        int contentHeight = ruleCount * 44;
        if (contentHeight <= viewportHeight) return;
        int thumbHeight = Math.max(18, Math.round(viewportHeight * viewportHeight / (float) contentHeight));
        int maximum = contentHeight - viewportHeight;
        int thumbY = viewportTop + Math.round((viewportHeight - thumbHeight) * ruleListScroll / maximum);
        int trackX = x + width + 4;
        graphics.fill(trackX, viewportTop, trackX + 1, viewportBottom, UiPalette.DIVIDER);
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, UiPalette.PALE_BLUE);
    }

    private void renderRuleDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRuleDialogFrame) / 1_000_000_000.0F);
        lastRuleDialogFrame = now;
        ruleDialogDelta = delta;
        ruleDialogAnimation = UiMath.approach(ruleDialogAnimation, 1.0F, delta, 9.0F);
        ruleDialogScroll = UiMath.approach(ruleDialogScroll, ruleDialogScrollTarget, delta, 18.0F);
        int x = RULE_DIALOG_X;
        int y = RULE_DIALOG_Y;
        int width = RULE_DIALOG_WIDTH;
        int height = RULE_DIALOG_HEIGHT;
        float scale = 0.96F + ruleDialogAnimation * 0.04F;
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        graphics.fill(x, y, x + width, y + height, ruleDialogColor(0xEE07111F));
        graphics.fill(x, y, x + width, y + 1, ruleDialogColor(UiPalette.DIVIDER));
        graphics.fill(x, y + height - 1, x + width, y + height, ruleDialogColor(UiPalette.DIVIDER));
        graphics.fill(x, y, x + 1, y + height, ruleDialogColor(UiPalette.DIVIDER));
        graphics.fill(x + width - 1, y, x + width, y + height, ruleDialogColor(UiPalette.DIVIDER));
        drawRuleDialogText(graphics, "x", x + width - 22, y + 13, 0.82F, UiPalette.MUTED_TEXT);
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int contentRowsHeight = 122 + (ruleMatchMode == RuleMatchMode.NBT_MATCH
                ? primaryNbtMatchModeSelector.expandedHeight() + additionalNbtConditions.size() * 44 + additionalNbtMatchModeExpandedHeight() + 28 : 0);
        ruleDialogContentHeight = 95 + ruleModeDropdown.expandedHeight() + contentRowsHeight;
        ruleDialogScrollTarget = clampRuleDialogScroll(ruleDialogScrollTarget);
        ruleDialogScroll = clampRuleDialogScroll(ruleDialogScroll);
        int scroll = Math.round(ruleDialogScroll);
        int formY = y + 31 - scroll;
        renderRuleTextField(graphics, x + 18, formY, width - 36, tr("ui.itemglintrelight.rules.name"), ruleName, RuleInputFocus.NAME, false, false, mouseX, mouseY);
        ruleModeDropdown.setPosition(x + 18, y + 72 - scroll);
        int contentY = y + 126 + ruleModeDropdown.expandedHeight() - scroll;
        int contentViewportY = y + 31;
        int contentViewportBottom = y + height - 42;
        if (ruleMatchMode == RuleMatchMode.WHITELIST) {
            renderRuleTextField(graphics, x + 18, contentY, width - 36, tr("ui.itemglintrelight.rules.item_id"), ruleItemId, RuleInputFocus.ITEM, true, true, mouseX, mouseY);
        } else if (ruleMatchMode == RuleMatchMode.NBT_MATCH) {
            int inputWidth = (width - 36 - 60 - 92) / 2;
            int deleteX = x + 18;
            int keyX = deleteX + 30;
            int modeX = keyX + inputWidth + 8;
            int valueX = modeX + 76 + 8;
            int addX = valueX + inputWidth + 6;
            primaryNbtMatchModeSelector.setPosition(modeX, contentY);
            renderRuleTextField(graphics, keyX, contentY, inputWidth, tr("ui.itemglintrelight.rules.nbt_key"), ruleNbtPath, RuleInputFocus.NBT_PATH, false, true, mouseX, mouseY);
            primaryNbtMatchModeSelector.setPosition(modeX, contentY);
            primaryNbtMatchModeSelector.render(graphics, font, mouseX, mouseY, ruleDialogAnimation);
            renderRuleTextField(graphics, valueX, contentY, inputWidth, tr("ui.itemglintrelight.rules.nbt_value"), ruleNbtValue, RuleInputFocus.NBT_VALUE, false, false, mouseX, mouseY);
            int additionalY = contentY + 44 + primaryNbtMatchModeSelector.expandedHeight();
            for (int index = 0; index < additionalNbtConditions.size(); index++) {
                renderAdditionalNbtCondition(graphics, keyX, additionalNbtConditionY(additionalY, index), inputWidth, modeX, valueX, index, mouseX, mouseY);
            }
            int actionY = additionalNbtConditions.isEmpty() ? contentY + 12 : additionalNbtConditionY(additionalY, additionalNbtConditions.size() - 1) + 12;
            renderNbtActionButton(graphics, addX, actionY, "+", true, mouseX, mouseY);
            if (!additionalNbtConditions.isEmpty()) {
                renderNbtActionButton(graphics, deleteX, contentY + 12, "x", false, mouseX, mouseY);
                for (int index = 0; index < additionalNbtConditions.size(); index++) {
                    renderNbtActionButton(graphics, deleteX, additionalNbtConditionY(additionalY, index) + 12, "x", false, mouseX, mouseY);
                }
            }
        } else {
            renderRuleTextField(graphics, x + 18, contentY, width - 36, tr("ui.itemglintrelight.rules.item_or_tag"), ruleItemId, RuleInputFocus.ITEM, true, true, mouseX, mouseY);
        }
        int priorityY = contentY + 44 + (ruleMatchMode == RuleMatchMode.NBT_MATCH
                ? primaryNbtMatchModeSelector.expandedHeight() + additionalNbtConditions.size() * 44 + additionalNbtMatchModeExpandedHeight() + 28 : 0);
        renderRuleTextField(graphics, x + 18, priorityY, width - 36, tr("ui.itemglintrelight.rules.priority"), rulePriority,
                RuleInputFocus.PRIORITY, false, false, mouseX, mouseY);
        int outlineParametersY = priorityY + 44;
        renderRuleOutlineParameterField(graphics, x + 18, outlineParametersY, width - 36, mouseX, mouseY);
        ruleModeDropdown.render(graphics, font, mouseX, mouseY, ruleDialogAnimation);
        graphics.disableScissor();
        renderRuleDialogScrollBar(graphics, x, contentViewportY, contentViewportBottom - contentViewportY);
        renderRuleAddButton(graphics, x, y, width, height, mouseX, mouseY);
        graphics.pose().popMatrix();
    }

    private void renderRuleOutlineDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = System.nanoTime();
        ruleDialogDelta = Math.min(0.05F, (now - lastRuleDialogFrame) / 1_000_000_000.0F);
        lastRuleDialogFrame = now;
        int x = RULE_DIALOG_X;
        int y = 20;
        int width = RULE_DIALOG_WIDTH;
        int height = 300;
        graphics.fill(x, y, x + width, y + height, 0xF207111F);
        graphics.fill(x, y, x + width, y + 1, UiPalette.DIVIDER);
        graphics.fill(x, y + height - 1, x + width, y + height, UiPalette.DIVIDER);
        graphics.fill(x, y, x + 1, y + height, UiPalette.DIVIDER);
        graphics.fill(x + width - 1, y, x + width, y + height, UiPalette.DIVIDER);
        graphics.enableScissor(x + 1, y + 12, x + width - 1, y + height - 42);
        layoutRenderControls();
        outlineWidthSlider.render(graphics, font, mouseX, mouseY);
        outlineSoftnessSlider.render(graphics, font, mouseX, mouseY);
        outlineThresholdSlider.render(graphics, font, mouseX, mouseY);
        outlineOpacitySlider.render(graphics, font, mouseX, mouseY);
        outlineRenderModeDropdown.render(graphics, font, mouseX, mouseY);
        outlineColorModeDropdown.render(graphics, font, mouseX, mouseY);
        if (usesPrimaryColor()) outlinePrimaryColorPicker.render(graphics, font, mouseX, mouseY);
        if (usesSecondaryColor()) outlineSecondaryColorPicker.render(graphics, font, mouseX, mouseY);
        if (usesColorAnimation()) outlineColorScrollSpeedSlider.render(graphics, font, mouseX, mouseY);
        if (usesColorAnimation()) outlineColorScrollModeDropdown.render(graphics, font, mouseX, mouseY);
        if (usesPlanarColorScroll()) outlineColorScrollControl.render(graphics, font, mouseX, mouseY);
        if (usesOutlineColorScroll()) outlineColorLengthSlider.render(graphics, font, mouseX, mouseY);
        if (usesTextureSampling()) {
            outlineSampleSizeSlider.render(graphics, font, mouseX, mouseY);
            outlineSampleColorCountSlider.render(graphics, font, mouseX, mouseY);
        }
        outlineQualityDropdown.render(graphics, font, mouseX, mouseY);
        outlineGlowIntensitySlider.render(graphics, font, mouseX, mouseY);
        outlineBloomToggle.render(graphics, font, mouseX, mouseY);
        if (model.draft().outlineBloomEnabled()) {
            outlineBloomQualityDropdown.render(graphics, font, mouseX, mouseY);
            outlineBloomRadiusSlider.render(graphics, font, mouseX, mouseY);
            outlineBloomIntensitySlider.render(graphics, font, mouseX, mouseY);
            outlineBloomSoftnessSlider.render(graphics, font, mouseX, mouseY);
            outlineBloomBlurPassesSlider.render(graphics, font, mouseX, mouseY);
        }
        graphics.disableScissor();
        int buttonX = x + width - 106;
        int buttonY = y + height - 32;
        boolean hovered = UiMath.contains(buttonX, buttonY, 88, 24, mouseX, mouseY);
        float hover = ruleHover("rule-outline-confirm", hovered);
        graphics.fill(buttonX, buttonY, buttonX + 88, buttonY + 24, UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, hover));
        graphics.fill(buttonX + 1, buttonY + 1, buttonX + 87, buttonY + 23, UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, hover));
        String label = tr("ui.itemglintrelight.rules.confirm");
        SmoothTextRenderer.drawCentered(graphics, font, label, buttonX + 44.0F,
                buttonY + (24 - SmoothTextRenderer.height(label, 0.72F, UiPalette.TEXT)) * 0.5F, 0.72F, UiPalette.TEXT);
    }

    private boolean handleRuleOutlineDialogClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int x = RULE_DIALOG_X;
        int y = 20;
        int width = RULE_DIALOG_WIDTH;
        int height = 300;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            closeRuleOutlineDialog(false);
            return true;
        }
        if (UiMath.contains(x + width - 106, y + height - 32, 88, 24, mouseX, mouseY)) {
            closeRuleOutlineDialog(true);
            return true;
        }
        if (outlineColorModeDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineRenderModeDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineQualityDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        if (model.draft().outlineGuiItems() && guiOutlineQualityDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesColorAnimation() && outlineColorScrollModeDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        if (model.draft().outlineBloomEnabled() && outlineBloomQualityDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesPrimaryColor() && outlinePrimaryColorPicker.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesSecondaryColor() && outlineSecondaryColorPicker.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineBloomToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineWidthSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineSoftnessSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineThresholdSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineOpacitySlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesColorAnimation() && outlineColorScrollSpeedSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesPlanarColorScroll() && outlineColorScrollControl.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesOutlineColorScroll() && outlineColorLengthSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesTextureSampling() && outlineSampleSizeSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (usesTextureSampling() && outlineSampleColorCountSlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (outlineGlowIntensitySlider.mouseClicked(mouseX, mouseY, button)) return true;
        if (model.draft().outlineBloomEnabled()) {
            if (outlineBloomRadiusSlider.mouseClicked(mouseX, mouseY, button)) return true;
            if (outlineBloomIntensitySlider.mouseClicked(mouseX, mouseY, button)) return true;
            if (outlineBloomSoftnessSlider.mouseClicked(mouseX, mouseY, button)) return true;
            if (outlineBloomBlurPassesSlider.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return true;
    }

    private void openRuleOutlineDialog() {
        ruleOutlineOriginal = model.draft().copy();
        ruleOutlineSettings = ruleOutlineSettings == null ? model.draft().copy() : ruleOutlineSettings.copy();
        model.draft().copyFrom(ruleOutlineSettings);
        renderScroll = 0.0F;
        renderScrollTarget = 0.0F;
        ruleOutlineDialogOpen = true;
    }

    private void closeRuleOutlineDialog(boolean confirm) {
        if (confirm) {
            ruleOutlineSettings = model.draft().copy();
            ruleOutlineParameters = serializeRuleOutlineParameters(ruleOutlineSettings);
        }
        if (ruleOutlineOriginal != null) model.draft().copyFrom(ruleOutlineOriginal);
        ruleOutlineDialogOpen = false;
        ruleOutlineOriginal = null;
    }

    private String serializeRuleOutlineParameters(ItemGlintRelightConfig config) {
        return String.format(Locale.ROOT, "width=%.2f; softness=%.2f; alpha=%.2f; opacity=%.0f%%; mode=%s; color=%s; quality=%s; glow=%.2f; bloom=%s",
                config.outlineWidth(), config.outlineSoftness(), config.outlineAlphaThreshold(), config.outlineOpacity() * 100.0F,
                config.outlineRenderMode().name(), config.outlineColorMode().name(), config.outlineQuality().name(),
                config.outlineGlowIntensity(), config.outlineBloomEnabled());
    }

    private void renderRuleTextField(GuiGraphics graphics, int x, int y, int width, String label, String value, RuleInputFocus focus, boolean selectable, boolean required, int mouseX, int mouseY) {
        drawRuleDialogText(graphics, label, x, y, 0.68F, UiPalette.MUTED_TEXT);
        int inputWidth = selectable ? width - 62 : width;
        boolean focused = ruleInputFocus == focus;
        boolean hovered = UiMath.contains(x, y + 12, inputWidth, 22, mouseX, mouseY);
        float hover = ruleHover("field:" + focus.name(), focused || hovered);
        graphics.fill(x, y + 12, x + inputWidth, y + 34, ruleDialogColor(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, hover)));
        graphics.fill(x + 1, y + 13, x + inputWidth - 1, y + 33, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, hover)));
        String visible = value.isEmpty() ? tr(required ? "ui.itemglintrelight.rules.required" : "ui.itemglintrelight.rules.optional") : value;
        float textY = y + 12 + (22 - SmoothTextRenderer.height(visible, 0.68F, UiPalette.TEXT)) * 0.5F;
        drawRuleDialogText(graphics, truncate(visible, inputWidth - 18, 0.68F), x + 8, textY, 0.68F, value.isEmpty() ? UiPalette.MUTED_TEXT : UiPalette.TEXT);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 8 + SmoothTextRenderer.width(value.substring(0, Math.min(ruleInputCursor, value.length())), 0.68F, UiPalette.TEXT);
            graphics.fill(cursorX, y + 17, cursorX + 1, y + 29, ruleDialogColor(UiPalette.PALE_BLUE));
        }
        if (selectable) {
            int buttonX = x + inputWidth + 6;
            boolean buttonHovered = UiMath.contains(buttonX, y + 12, 56, 22, mouseX, mouseY);
            float buttonHover = ruleHover("field-select:" + focus.name(), buttonHovered);
            graphics.fill(buttonX, y + 12, buttonX + 56, y + 34, ruleDialogColor(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, buttonHover)));
            graphics.fill(buttonX + 1, y + 13, buttonX + 55, y + 33, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, buttonHover)));
            drawRuleDialogText(graphics, tr("ui.itemglintrelight.rules.select"), buttonX + 11, y + 18, 0.58F, UiPalette.TEXT);
        }
    }

    private void renderNbtMatchModeControl(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.rules.nbt_match_mode"), x, y, 0.68F, UiPalette.MUTED_TEXT, ruleDialogAnimation);
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastNbtMatchModeFrame) / 1_000_000_000.0F);
        lastNbtMatchModeFrame = now;
        nbtMatchModeTransition = UiMath.approach(nbtMatchModeTransition, 1.0F, delta, 16.0F);
        nbtMatchModeExpansion = UiMath.approach(nbtMatchModeExpansion, nbtMatchModeExpanded ? 1.0F : 0.0F, delta, 18.0F);
        int fieldY = y + 12;
        boolean hovered = UiMath.contains(x, fieldY, width, 22, mouseX, mouseY);
        nbtPreviousArrowHover = UiMath.approach(nbtPreviousArrowHover, UiMath.contains(x + 1, fieldY + 1, 20, 20, mouseX, mouseY) ? 1.0F : 0.0F, delta, 14.0F);
        nbtNextArrowHover = UiMath.approach(nbtNextArrowHover, UiMath.contains(x + width - 21, fieldY + 1, 20, 20, mouseX, mouseY) ? 1.0F : 0.0F, delta, 14.0F);
        nbtCenterHover = UiMath.approach(nbtCenterHover, UiMath.contains(x + 22, fieldY + 1, width - 44, 20, mouseX, mouseY) ? 1.0F : 0.0F, delta, 14.0F);
        float fieldHover = ruleHover("nbt-primary-mode", hovered);
        graphics.fill(x, y + 12, x + width, y + 34, ruleDialogColor(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, fieldHover)));
        graphics.fill(x + 1, y + 13, x + width - 1, y + 33, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, fieldHover)));
        graphics.fill(x + 1, fieldY + 1, x + 21, fieldY + 21, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, nbtPreviousArrowHover)));
        graphics.fill(x + width - 21, fieldY + 1, x + width - 1, fieldY + 21, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, nbtNextArrowHover)));
        graphics.fill(x + 22, fieldY + 1, x + width - 22, fieldY + 21, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, nbtCenterHover)));
        float arrowY = fieldY + (22 - SmoothTextRenderer.height("<", 0.68F, UiPalette.PALE_BLUE)) * 0.5F;
        drawRuleDialogText(graphics, "<", x + 7, arrowY, 0.68F, UiMath.mix(UiPalette.PALE_BLUE, UiPalette.LIGHT_GREEN, nbtPreviousArrowHover));
        drawRuleDialogText(graphics, ">", x + width - 13, arrowY, 0.68F, UiMath.mix(UiPalette.PALE_BLUE, UiPalette.LIGHT_GREEN, nbtNextArrowHover));
        int centerX = x + width / 2;
        if (nbtMatchModeTransition < 0.995F) {
            drawNbtMatchModeSymbol(graphics, previousNbtMatchMode.symbol, centerX - nbtMatchModeDirection * nbtMatchModeTransition * 22.0F,
                    fieldY, 1.0F - nbtMatchModeTransition);
        }
        drawNbtMatchModeSymbol(graphics, ruleNbtMatchMode.symbol, centerX + nbtMatchModeDirection * (1.0F - nbtMatchModeTransition) * 22.0F,
                fieldY, 1.0F);
        int optionHeight = nbtMatchModeExpandedHeight();
        if (optionHeight > 0) {
            int optionsY = fieldY + 23;
            graphics.fill(x, optionsY, x + width, optionsY + optionHeight, ruleDialogColor(UiPalette.BRIGHT_BLUE));
            graphics.fill(x + 1, optionsY + 1, x + width - 1, optionsY + optionHeight - 1, ruleDialogColor(UiPalette.DEEP_BLUE_FADE));
            graphics.fill(x, optionsY + optionHeight - 1, x + width, optionsY + optionHeight, ruleDialogColor(UiPalette.BRIGHT_BLUE));
            for (int index = 0; index < NbtMatchMode.values().length; index++) {
                int optionY = optionsY + index * 22;
                if (optionY >= optionsY + optionHeight) break;
                boolean optionHovered = UiMath.contains(x, optionY, width, 22, mouseX, mouseY);
                nbtMatchModeOptionHovers[index] = UiMath.approach(nbtMatchModeOptionHovers[index], optionHovered ? 1.0F : 0.0F, delta, 12.0F);
                graphics.fill(x + 1, optionY, x + width - 1, optionY + 22,
                        ruleDialogColor(UiMath.mix(UiPalette.DEEP_BLUE_FADE, UiPalette.SURFACE_HOVER, nbtMatchModeOptionHovers[index])));
                NbtMatchMode mode = NbtMatchMode.values()[index];
                int symbolWidth = SmoothTextRenderer.width(mode.symbol, 0.68F, UiPalette.TEXT);
                float symbolY = optionY + (22 - SmoothTextRenderer.height(mode.symbol, 0.68F, UiPalette.TEXT)) * 0.5F;
                drawRuleDialogText(graphics, mode.symbol, x + (width - symbolWidth) * 0.5F, symbolY, 0.68F,
                        mode == ruleNbtMatchMode ? UiPalette.LIGHT_GREEN : UiPalette.TEXT);
            }
        }
    }

    private void drawNbtMatchModeSymbol(GuiGraphics graphics, String symbol, float centerX, int fieldY, float opacity) {
        int symbolWidth = SmoothTextRenderer.width(symbol, 0.68F, UiPalette.TEXT);
        float symbolY = fieldY + (22 - SmoothTextRenderer.height(symbol, 0.68F, UiPalette.TEXT)) * 0.5F;
        SmoothTextRenderer.draw(graphics, font, symbol, centerX - symbolWidth * 0.5F, symbolY, 0.68F,
                ruleDialogColor(UiPalette.TEXT), ruleDialogAnimation * opacity);
    }

    private void renderRuleAddButton(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        int buttonX = x + width - 106;
        int buttonY = y + height - 32;
        boolean hovered = UiMath.contains(buttonX, buttonY, 88, 24, mouseX, mouseY);
        float hover = ruleHover("rule-add", hovered);
        graphics.fill(buttonX, buttonY, buttonX + 88, buttonY + 24, ruleDialogColor(UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, hover)));
        graphics.fill(buttonX + 1, buttonY + 1, buttonX + 87, buttonY + 23, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, hover)));
        String label = tr("ui.itemglintrelight.rules.confirm_add");
        SmoothTextRenderer.drawCentered(graphics, font, label, buttonX + 44.0F,
                buttonY + (24 - SmoothTextRenderer.height(label, 0.72F, UiPalette.TEXT)) * 0.5F, 0.72F, ruleDialogColor(UiPalette.TEXT), ruleDialogAnimation);
    }

    private boolean isAddRuleButton(double mouseX, double mouseY) {
        int buttonRight = right - 24;
        int buttonWidth = Math.round(24.0F + addRuleHover * 66.0F);
        return UiMath.contains(buttonRight - buttonWidth, top + 40, buttonWidth, 24, mouseX, mouseY)
                || UiMath.contains(buttonRight - 24, top + 40, 24, 24, mouseX, mouseY);
    }

    private void updateRuleButtonAnimations(int mouseX, int mouseY) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRuleFrame) / 1_000_000_000.0F);
        lastRuleFrame = now;
        addRuleHover = UiMath.approach(addRuleHover, isAddRuleButton(mouseX, mouseY) ? 1.0F : 0.0F, delta, 13.0F);
        presetHover = UiMath.approach(presetHover, isPresetButton(mouseX, mouseY) ? 1.0F : 0.0F, delta, 13.0F);
    }

    private void renderAdditionalNbtCondition(GuiGraphics graphics, int keyX, int y, int inputWidth, int modeX, int valueX,
                                              int index, int mouseX, int mouseY) {
        NbtConditionDraft condition = additionalNbtConditions.get(index);
        renderAdditionalNbtField(graphics, keyX, y, inputWidth, tr("ui.itemglintrelight.rules.nbt_key"), condition.path,
                RuleInputFocus.ADDITIONAL_NBT_PATH, index, mouseX, mouseY, true);
        UiCyclingSelector selector = additionalNbtMatchModeSelectors.get(index);
        selector.setPosition(modeX, y);
        selector.render(graphics, font, mouseX, mouseY, ruleDialogAnimation);
        renderAdditionalNbtField(graphics, valueX, y, inputWidth, tr("ui.itemglintrelight.rules.nbt_value"), condition.value,
                RuleInputFocus.ADDITIONAL_NBT_VALUE, index, mouseX, mouseY, false);
    }

    private void renderNbtActionButton(GuiGraphics graphics, int x, int y, String label, boolean add, int mouseX, int mouseY) {
        boolean hovered = UiMath.contains(x, y, 24, 22, mouseX, mouseY);
        float hover = ruleHover("nbt-action:" + x + ':' + y, hovered);
        graphics.fill(x, y, x + 24, y + 22, ruleDialogColor(UiMath.mix(UiPalette.DIVIDER, add ? UiPalette.BRIGHT_BLUE : UiPalette.PALE_BLUE, hover)));
        graphics.fill(x + 1, y + 1, x + 23, y + 21, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, hover)));
        SmoothTextRenderer.drawCentered(graphics, font, label, x + 12.0F,
                y + (22 - SmoothTextRenderer.height(label, 0.68F, UiPalette.TEXT)) * 0.5F, 0.68F,
                UiMath.mix(UiPalette.MUTED_TEXT, add ? UiPalette.LIGHT_GREEN : UiPalette.PALE_BLUE, hover), ruleDialogAnimation);
    }

    private void renderAdditionalNbtField(GuiGraphics graphics, int x, int y, int width, String label, String value,
                                          RuleInputFocus focus, int index, int mouseX, int mouseY, boolean required) {
        drawRuleDialogText(graphics, label, x, y, 0.68F, UiPalette.MUTED_TEXT);
        boolean focused = ruleInputFocus == focus && additionalNbtConditionIndex == index;
        boolean hovered = UiMath.contains(x, y + 12, width, 22, mouseX, mouseY);
        float hover = ruleHover("additional-field:" + index + ':' + focus.name(), focused || hovered);
        graphics.fill(x, y + 12, x + width, y + 34, ruleDialogColor(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, hover)));
        graphics.fill(x + 1, y + 13, x + width - 1, y + 33, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, hover)));
        String visible = value.isEmpty() ? tr(required ? "ui.itemglintrelight.rules.required" : "ui.itemglintrelight.rules.optional") : value;
        float textY = y + 12 + (22 - SmoothTextRenderer.height(visible, 0.68F, UiPalette.TEXT)) * 0.5F;
        drawRuleDialogText(graphics, truncate(visible, width - 18, 0.68F), x + 8, textY, 0.68F, value.isEmpty() ? UiPalette.MUTED_TEXT : UiPalette.TEXT);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 8 + SmoothTextRenderer.width(value.substring(0, Math.min(ruleInputCursor, value.length())), 0.68F, UiPalette.TEXT);
            graphics.fill(cursorX, y + 17, cursorX + 1, y + 29, ruleDialogColor(UiPalette.PALE_BLUE));
        }
    }

    private void openRuleDialog() {
        ruleDialogOpen = true;
        ruleDialogAnimation = 0.0F;
        lastRuleDialogFrame = System.nanoTime();
        ruleDialogScroll = 0.0F;
        ruleDialogScrollTarget = 0.0F;
        focusRuleInput(RuleInputFocus.NAME);
    }

    private boolean isPresetButton(double mouseX, double mouseY) {
        return UiMath.contains(sidebarRight + 24, top + 40, 58, 24, mouseX, mouseY);
    }

    private void renderPresetDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastPresetDialogFrame) / 1_000_000_000.0F);
        lastPresetDialogFrame = now;
        presetDialogAnimation = UiMath.approach(presetDialogAnimation, 1.0F, delta, 9.0F);
        int x = RULE_DIALOG_X;
        int y = RULE_DIALOG_Y;
        int width = RULE_DIALOG_WIDTH;
        int height = RULE_DIALOG_HEIGHT;
        float scale = 0.96F + presetDialogAnimation * 0.04F;
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        graphics.fill(x, y, x + width, y + height, presetDialogColor(0xF207111F));
        graphics.fill(x, y, x + width, y + 1, presetDialogColor(UiPalette.DIVIDER));
        graphics.fill(x, y + height - 1, x + width, y + height, presetDialogColor(UiPalette.DIVIDER));
        graphics.fill(x, y, x + 1, y + height, presetDialogColor(UiPalette.DIVIDER));
        graphics.fill(x + width - 1, y, x + width, y + height, presetDialogColor(UiPalette.DIVIDER));
        boolean hovered = UiMath.contains(x + width - 32, y + 6, 24, 24, mouseX, mouseY);
        SmoothTextRenderer.draw(graphics, font, "x", x + width - 22, y + 13, 0.82F,
                hovered ? UiPalette.PALE_BLUE : UiPalette.MUTED_TEXT, presetDialogAnimation);
        int cardY = y + 46;
        boolean cardHovered = UiMath.contains(x + 18, cardY, width - 36, 42, mouseX, mouseY);
        graphics.fill(x + 18, cardY, x + width - 18, cardY + 42, presetDialogColor(cardHovered ? UiPalette.BRIGHT_BLUE : UiPalette.DIVIDER));
        graphics.fill(x + 19, cardY + 1, x + width - 19, cardY + 41, presetDialogColor(cardHovered ? UiPalette.SURFACE_HOVER : UiPalette.SURFACE));
        String title = tr("ui.itemglintrelight.rules.preset.simple_enchantment");
        SmoothTextRenderer.draw(graphics, font, title, x + 30, cardY + 9, 0.76F, UiPalette.TEXT, presetDialogAnimation);
        SmoothTextRenderer.draw(graphics, font, "minecraft:enchantments !=", x + 30, cardY + 25, 0.56F, UiPalette.MUTED_TEXT, presetDialogAnimation);
        int toolCardY = cardY + 50;
        boolean toolCardHovered = UiMath.contains(x + 18, toolCardY, width - 36, 42, mouseX, mouseY);
        graphics.fill(x + 18, toolCardY, x + width - 18, toolCardY + 42, presetDialogColor(toolCardHovered ? UiPalette.BRIGHT_BLUE : UiPalette.DIVIDER));
        graphics.fill(x + 19, toolCardY + 1, x + width - 19, toolCardY + 41, presetDialogColor(toolCardHovered ? UiPalette.SURFACE_HOVER : UiPalette.SURFACE));
        String toolTitle = tr("ui.itemglintrelight.rules.preset.tool_outline");
        SmoothTextRenderer.draw(graphics, font, toolTitle, x + 30, toolCardY + 9, 0.76F, UiPalette.TEXT, presetDialogAnimation);
        SmoothTextRenderer.draw(graphics, font, "#c:tools", x + 30, toolCardY + 25, 0.56F, UiPalette.MUTED_TEXT, presetDialogAnimation);
        int advancedCardY = toolCardY + 50;
        boolean advancedCardHovered = UiMath.contains(x + 18, advancedCardY, width - 36, 42, mouseX, mouseY);
        graphics.fill(x + 18, advancedCardY, x + width - 18, advancedCardY + 42, presetDialogColor(advancedCardHovered ? UiPalette.BRIGHT_BLUE : UiPalette.DIVIDER));
        graphics.fill(x + 19, advancedCardY + 1, x + width - 19, advancedCardY + 41, presetDialogColor(advancedCardHovered ? UiPalette.SURFACE_HOVER : UiPalette.SURFACE));
        String advancedTitle = tr("ui.itemglintrelight.rules.preset.advanced_enchantment");
        SmoothTextRenderer.draw(graphics, font, advancedTitle, x + 30, advancedCardY + 9, 0.76F, UiPalette.TEXT, presetDialogAnimation);
        SmoothTextRenderer.draw(graphics, font, "9 enchantment rules", x + 30, advancedCardY + 25, 0.56F, UiPalette.MUTED_TEXT, presetDialogAnimation);
        graphics.pose().popMatrix();
    }

    private void applySimpleEnchantmentPreset() {
        ItemGlintRelightConfig outline = new ItemGlintRelightConfig();
        outline.setOutlineWidth(4.85F);
        outline.setOutlineSoftness(0.0F);
        outline.setOutlineAlphaThreshold(0.13F);
        outline.setOutlineOpacity(0.7F);
        outline.setOutlineQuality(RenderQuality.LOW);
        outline.setOutlineGlowIntensity(0.33F);
        outline.setOutlineBloomEnabled(false);
        outline.setOutlineBloomQuality(RenderQuality.HIGH);
        outline.setOutlineBloomRadius(9.15F);
        outline.setOutlineBloomIntensity(1.65F);
        outline.setOutlineBloomBlurPasses(6);
        outline.setOutlineRenderMode(OutlineRenderMode.CUBIC);
        outline.setOutlineColorMode(OutlineColorMode.SINGLE);
        outline.setOutlinePrimaryColor(-2982401);
        outline.setOutlineSecondaryColor(-6881373);
        outline.setOutlineColorScrollSpeed(2.0F);
        outline.setOutlineColorScrollDirection(223.16925F);
        outline.setOutlineColorScrollInterval(1.5F);
        outline.setOutlineColorScrollMode(ColorScrollMode.PLANAR);
        outline.setOutlineSampleSize(4);
        outline.setOutlineSampleColorCount(5);
        String parameters = "width=4.85; softness=0.00; alpha=0.13; opacity=70%; mode=CUBIC; color=SINGLE; quality=LOW; glow=0.33; bloom=false";
        DisplayRuleManager.add(new DisplayRule(null, "简单附魔描边", DisplayRule.Mode.NBT_MATCH, "", "minecraft:enchantments", "!=", "", 0,
                parameters, outline, List.of(new DisplayRule.NbtCondition("minecraft:enchantments", "!=", ""))));
    }

    private void applyToolOutlinePreset() {
        ItemGlintRelightConfig outline = new ItemGlintRelightConfig();
        outline.setOutlineWidth(4.95F);
        outline.setOutlineSoftness(0.0F);
        outline.setOutlineAlphaThreshold(0.13F);
        outline.setOutlineOpacity(0.79F);
        outline.setOutlineQuality(RenderQuality.LOW);
        outline.setOutlineGlowIntensity(0.33F);
        outline.setOutlineBloomEnabled(false);
        outline.setOutlineBloomQuality(RenderQuality.HIGH);
        outline.setOutlineBloomRadius(9.15F);
        outline.setOutlineBloomIntensity(1.65F);
        outline.setOutlineBloomBlurPasses(6);
        outline.setOutlineRenderMode(OutlineRenderMode.FLAT);
        outline.setOutlineColorMode(OutlineColorMode.SINGLE);
        outline.setOutlinePrimaryColor(-9248769);
        outline.setOutlineSecondaryColor(-6881373);
        outline.setOutlineColorScrollSpeed(2.0F);
        outline.setOutlineColorScrollDirection(223.16925F);
        outline.setOutlineColorScrollInterval(1.5F);
        outline.setOutlineColorScrollMode(ColorScrollMode.PLANAR);
        outline.setOutlineSampleSize(4);
        outline.setOutlineSampleColorCount(5);
        String parameters = "width=4.95; softness=0.00; alpha=0.13; opacity=79%; mode=FLAT; color=SINGLE; quality=LOW; glow=0.33; bloom=false";
        DisplayRuleManager.add(new DisplayRule(null, "工具描边", DisplayRule.Mode.WHITELIST, "#c:tools", "", "=", "", 0,
                parameters, outline, List.of()));
    }

    private void applyAdvancedEnchantmentPreset() {
        addEnchantmentPresetRule("Efficiency", "minecraft:efficiency", 3.6F, OutlineColorMode.DUAL, -11865601, -1838856, 2.0F, 267.8789F, 0.5058571F);
        addEnchantmentPresetRule("FireAspect", "minecraft:fire_aspect", 3.8F, OutlineColorMode.DUAL, -38294, -199589, 2.0F, 260.2176F, 0.52867305F);
        addEnchantmentPresetRule("Fortune", "minecraft:fortune", 3.5F, OutlineColorMode.DUAL, -9542642, -1507488, 0.73F, 323.97263F, 0.63640547F);
        addEnchantmentPresetRule("Knockback", "minecraft:knockback", 3.5F, OutlineColorMode.SINGLE, -16732155, -6881373, 2.0F, 223.16925F, 1.5F);
        addEnchantmentPresetRule("Looting", "minecraft:looting", 3.5F, OutlineColorMode.DUAL, -6874155, -10154387, 2.0F, 270.40067F, 1.5F);
        addEnchantmentPresetRule("Mending", "minecraft:mending", 3.5F, OutlineColorMode.DUAL, -8454305, -327866, 2.0F, 268.51215F, 1.5F);
        addEnchantmentPresetRule("Smite", "minecraft:smite", 3.5F, OutlineColorMode.DUAL, -1, -36238, 2.0F, 270.0F, 1.5F);
        addEnchantmentPresetRule("Sharpness", "minecraft:sharpness", 3.5F, OutlineColorMode.DUAL, -12974245, -6448229, 2.0F, 313.35297F, 1.5F);
        addEnchantmentPresetRule("Unbreaking", "minecraft:unbreaking", 3.5F, OutlineColorMode.SINGLE, -16329985, -6881373, 2.0F, 223.16925F, 1.5F);
    }

    private void addEnchantmentPresetRule(String name, String enchantment, float width, OutlineColorMode colorMode, int primaryColor,
                                          int secondaryColor, float speed, float direction, float interval) {
        ItemGlintRelightConfig outline = new ItemGlintRelightConfig();
        outline.setOutlineWidth(width);
        outline.setOutlineSoftness(0.0F);
        outline.setOutlineAlphaThreshold(0.13F);
        outline.setOutlineOpacity(1.0F);
        outline.setOutlineQuality(RenderQuality.LOW);
        outline.setOutlineGlowIntensity(0.33F);
        outline.setOutlineBloomEnabled(false);
        outline.setOutlineBloomQuality(RenderQuality.HIGH);
        outline.setOutlineBloomRadius(9.15F);
        outline.setOutlineBloomIntensity(1.65F);
        outline.setOutlineBloomBlurPasses(6);
        outline.setOutlineRenderMode(OutlineRenderMode.FLAT);
        outline.setOutlineColorMode(colorMode);
        outline.setOutlinePrimaryColor(primaryColor);
        outline.setOutlineSecondaryColor(secondaryColor);
        outline.setOutlineColorScrollSpeed(speed);
        outline.setOutlineColorScrollDirection(direction);
        outline.setOutlineColorScrollInterval(interval);
        outline.setOutlineColorScrollMode(ColorScrollMode.PLANAR);
        outline.setOutlineSampleSize(4);
        outline.setOutlineSampleColorCount(5);
        String parameters = String.format(Locale.ROOT, "width=%.2f; softness=0.00; alpha=0.13; opacity=100%%; mode=FLAT; color=%s; quality=LOW; glow=0.33; bloom=false",
                width, colorMode.name());
        DisplayRuleManager.add(new DisplayRule(null, name, DisplayRule.Mode.NBT_MATCH, "", "minecraft:enchantments", "⊃", enchantment, 0,
                parameters, outline, List.of(new DisplayRule.NbtCondition("minecraft:enchantments", "⊃", enchantment))));
    }

    private void openNewRuleDialog() {
        editingRuleId = null;
        ruleName = "";
        ruleItemId = "";
        ruleNbtPath = "";
        ruleNbtValue = "";
        additionalNbtConditions.clear();
        additionalNbtMatchModeSelectors.clear();
        additionalNbtConditionIndex = -1;
        rulePriority = "";
        ruleOutlineParameters = "";
        ruleNbtMatchMode = NbtMatchMode.EQUAL;
        primaryNbtMatchModeSelector.setSelected(ruleNbtMatchMode.ordinal());
        ruleOutlineSettings = null;
        openRuleDialog();
    }

    private boolean handleRuleEntryClick(double mouseX, double mouseY) {
        int x = sidebarRight + 24;
        int width = right - x - 24;
        int viewportTop = top + 76;
        int viewportBottom = bottom - 58;
        if (!UiMath.contains(x, viewportTop, width, viewportBottom - viewportTop, mouseX, mouseY)) return false;
        int y = viewportTop - Math.round(ruleListScroll);
        for (DisplayRule rule : DisplayRuleManager.rulesByPriority()) {
            if (UiMath.contains(x + width - 52, y + 8, 22, 22, mouseX, mouseY)) {
                editRule(rule);
                return true;
            }
            if (UiMath.contains(x + width - 26, y + 8, 22, 22, mouseX, mouseY)) {
                DisplayRuleManager.remove(rule.id());
                return true;
            }
            y += 44;
        }
        return false;
    }

    private void editRule(DisplayRule rule) {
        editingRuleId = rule.id();
        ruleName = rule.name();
        ruleMatchMode = RuleMatchMode.valueOf(rule.mode().name());
        ruleModeDropdown.setSelected(ruleMatchMode.ordinal());
        ruleItemId = rule.target();
        ruleNbtPath = rule.nbtPath();
        ruleNbtValue = rule.nbtValue();
        additionalNbtConditions.clear();
        List<DisplayRule.NbtCondition> conditions = rule.nbtConditions();
        if (!conditions.isEmpty()) {
            DisplayRule.NbtCondition first = conditions.getFirst();
            ruleNbtPath = first.path();
            ruleNbtValue = first.value();
            ruleNbtMatchMode = NbtMatchMode.fromSymbol(first.matchMode());
            for (int index = 1; index < conditions.size(); index++) {
                DisplayRule.NbtCondition condition = conditions.get(index);
                NbtConditionDraft draft = new NbtConditionDraft(condition.path(), NbtMatchMode.fromSymbol(condition.matchMode()), condition.value());
                additionalNbtConditions.add(draft);
                additionalNbtMatchModeSelectors.add(createNbtMatchModeSelector(draft));
            }
        }
        additionalNbtConditionIndex = -1;
        primaryNbtMatchModeSelector.setSelected(ruleNbtMatchMode.ordinal());
        rulePriority = Integer.toString(rule.priority());
        ruleOutlineParameters = rule.outlineParameters();
        ruleOutlineSettings = rule.outlineConfig() == null ? null : rule.outlineConfig().copy();
        for (NbtMatchMode mode : NbtMatchMode.values()) {
            if (mode.symbol.equals(rule.nbtMatchMode())) {
                ruleNbtMatchMode = mode;
                break;
            }
        }
        openRuleDialog();
    }

    private boolean handleRuleDialogClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int x = RULE_DIALOG_X;
        int y = RULE_DIALOG_Y;
        int width = RULE_DIALOG_WIDTH;
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + RULE_DIALOG_HEIGHT) {
            ruleDialogOpen = false;
            ruleInputFocus = RuleInputFocus.NONE;
            return true;
        }
        if (UiMath.contains(x + width - 32, y + 6, 24, 24, mouseX, mouseY)) {
            ruleDialogOpen = false;
            ruleInputFocus = RuleInputFocus.NONE;
            return true;
        }
        if (UiMath.contains(x + width - 106, y + RULE_DIALOG_HEIGHT - 32, 88, 24, mouseX, mouseY)) {
            if (!isRuleComplete()) return true;
            saveRule();
            ruleDialogOpen = false;
            ruleInputFocus = RuleInputFocus.NONE;
            return true;
        }
        int scroll = Math.round(ruleDialogScroll);
        ruleModeDropdown.setPosition(x + 18, y + 72 - scroll);
        boolean modeExpanded = ruleModeDropdown.isExpanded();
        if (ruleModeDropdown.mouseClicked(mouseX, mouseY, button) || modeExpanded) return true;
        if (UiMath.contains(x + 18, y + 43 - scroll, width - 36, 22, mouseX, mouseY)) {
            focusRuleInput(RuleInputFocus.NAME);
            return true;
        }
        int contentY = y + 126 + ruleModeDropdown.expandedHeight() - scroll;
        if (usesRuleTargetPicker() && UiMath.contains(x + width - 74, contentY + 12, 56, 22, mouseX, mouseY)) {
            openRuleTargetPicker();
            return true;
        }
        if (ruleMatchMode != RuleMatchMode.NBT_MATCH && UiMath.contains(x + 18, contentY + 12, width - 36, 22, mouseX, mouseY)) {
            focusRuleInput(RuleInputFocus.ITEM);
            return true;
        }
        if (ruleMatchMode == RuleMatchMode.NBT_MATCH) {
            int inputWidth = (width - 36 - 60 - 92) / 2;
            int deleteX = x + 18;
            int keyX = deleteX + 30;
            int modeX = keyX + inputWidth + 8;
            int valueX = modeX + 76 + 8;
            int addX = valueX + inputWidth + 6;
            int optionsY = contentY + 35;
            primaryNbtMatchModeSelector.setPosition(modeX, contentY);
            if (primaryNbtMatchModeSelector.mouseClicked(mouseX, mouseY, button)) return true;
            if (nbtMatchModeExpanded) {
                for (int index = 0; index < NbtMatchMode.values().length; index++) {
                    if (UiMath.contains(modeX, optionsY + index * 22, 76, 22, mouseX, mouseY)) {
                        selectNbtMatchMode(NbtMatchMode.values()[index], index >= ruleNbtMatchMode.ordinal() ? 1 : -1);
                        nbtMatchModeExpanded = false;
                        return true;
                    }
                }
            }
            if (UiMath.contains(keyX, contentY + 12, inputWidth, 22, mouseX, mouseY)) {
                focusRuleInput(RuleInputFocus.NBT_PATH);
                return true;
            }
            if (UiMath.contains(valueX, contentY + 12, inputWidth, 22, mouseX, mouseY)) {
                focusRuleInput(RuleInputFocus.NBT_VALUE);
                return true;
            }
            if (UiMath.contains(modeX, contentY + 12, 24, 22, mouseX, mouseY)) {
                selectNbtMatchMode(ruleNbtMatchMode.previous(), -1);
                return true;
            }
            if (UiMath.contains(modeX + 52, contentY + 12, 24, 22, mouseX, mouseY)) {
                selectNbtMatchMode(ruleNbtMatchMode.next(), 1);
                return true;
            }
            if (UiMath.contains(modeX + 24, contentY + 12, 28, 22, mouseX, mouseY)) {
                nbtMatchModeExpanded = !nbtMatchModeExpanded;
                return true;
            }
            int additionalY = contentY + 44 + primaryNbtMatchModeSelector.expandedHeight();
            for (int index = 0; index < additionalNbtConditions.size(); index++) {
                int rowY = additionalNbtConditionY(additionalY, index);
                UiCyclingSelector selector = additionalNbtMatchModeSelectors.get(index);
                selector.setPosition(modeX, rowY);
                if (selector.mouseClicked(mouseX, mouseY, button)) return true;
                if (UiMath.contains(keyX, rowY + 12, inputWidth, 22, mouseX, mouseY)) {
                    focusAdditionalNbtInput(index, RuleInputFocus.ADDITIONAL_NBT_PATH);
                    return true;
                }
                if (UiMath.contains(valueX, rowY + 12, inputWidth, 22, mouseX, mouseY)) {
                    focusAdditionalNbtInput(index, RuleInputFocus.ADDITIONAL_NBT_VALUE);
                    return true;
                }
                if (UiMath.contains(modeX, rowY + 12, 24, 22, mouseX, mouseY)) {
                    NbtConditionDraft condition = additionalNbtConditions.get(index);
                    selectAdditionalNbtMatchMode(condition, condition.mode.previous(), -1);
                    return true;
                }
                if (UiMath.contains(modeX + 52, rowY + 12, 24, 22, mouseX, mouseY)) {
                    NbtConditionDraft condition = additionalNbtConditions.get(index);
                    selectAdditionalNbtMatchMode(condition, condition.mode.next(), 1);
                    return true;
                }
                if (UiMath.contains(deleteX, rowY + 12, 24, 22, mouseX, mouseY)) {
                    additionalNbtConditions.remove(index);
                    additionalNbtMatchModeSelectors.remove(index);
                    additionalNbtConditionIndex = -1;
                    return true;
                }
            }
            int actionY = additionalNbtConditions.isEmpty() ? contentY + 12 : additionalY + (additionalNbtConditions.size() - 1) * 44 + 12;
            if (UiMath.contains(addX, actionY, 24, 22, mouseX, mouseY)) {
                NbtConditionDraft draft = new NbtConditionDraft("", NbtMatchMode.EQUAL, "");
                additionalNbtConditions.add(draft);
                additionalNbtMatchModeSelectors.add(createNbtMatchModeSelector(draft));
                return true;
            }
            if (!additionalNbtConditions.isEmpty() && UiMath.contains(deleteX, contentY + 12, 24, 22, mouseX, mouseY)) {
                NbtConditionDraft next = additionalNbtConditions.removeFirst();
                additionalNbtMatchModeSelectors.removeFirst();
                ruleNbtPath = next.path;
                ruleNbtMatchMode = next.mode;
                ruleNbtValue = next.value;
                additionalNbtConditionIndex = -1;
                return true;
            }
        }
        int priorityY = contentY + 44 + (ruleMatchMode == RuleMatchMode.NBT_MATCH
                ? primaryNbtMatchModeSelector.expandedHeight() + additionalNbtConditions.size() * 44 + additionalNbtMatchModeExpandedHeight() + 28 : 0);
        if (UiMath.contains(x + 18, priorityY + 12, width - 36, 22, mouseX, mouseY)) {
            focusRuleInput(RuleInputFocus.PRIORITY);
            return true;
        }
        int outlineParametersY = priorityY + 44;
        int outlineInputWidth = width - 36 - 70;
        if (UiMath.contains(x + 18 + outlineInputWidth + 6, outlineParametersY + 12, 64, 22, mouseX, mouseY)) {
            openRuleOutlineDialog();
            return true;
        }
        if (UiMath.contains(x + 18, outlineParametersY + 12, outlineInputWidth, 22, mouseX, mouseY)) {
            focusRuleInput(RuleInputFocus.OUTLINE_PARAMETERS);
            return true;
        }
        ruleInputFocus = RuleInputFocus.NONE;
        return true;
    }

    private boolean usesRuleTargetPicker() {
        return ruleMatchMode == RuleMatchMode.WHITELIST || ruleMatchMode == RuleMatchMode.BLACKLIST;
    }

    private void selectNbtMatchMode(NbtMatchMode next, int direction) {
        previousNbtMatchMode = ruleNbtMatchMode;
        ruleNbtMatchMode = next;
        nbtMatchModeDirection = direction;
        nbtMatchModeTransition = 0.0F;
    }

    private int nbtMatchModeExpandedHeight() {
        return Math.round(NbtMatchMode.values().length * 22.0F * nbtMatchModeExpansion);
    }

    private boolean handleRuleDialogKey(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            ruleDialogOpen = false;
            ruleInputFocus = RuleInputFocus.NONE;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER) {
            ruleInputFocus = RuleInputFocus.NONE;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            ruleInputCursor = Math.max(0, ruleInputCursor - 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            ruleInputCursor = Math.min(ruleInputValue().length(), ruleInputCursor + 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            deleteRuleInputCharacter();
            return true;
        }
        return true;
    }

    private void appendRuleInput(String value) {
        if (ruleInputFocus == RuleInputFocus.NONE) return;
        String current = ruleInputValue();
        if (current.length() >= 128) return;
        int cursor = Math.min(ruleInputCursor, current.length());
        String next = current.substring(0, cursor) + value + current.substring(cursor);
        if (ruleInputFocus == RuleInputFocus.PRIORITY && !next.matches("-?\\d*")) return;
        setRuleInputValue(next);
        ruleInputCursor = cursor + value.length();
    }

    private String appendRuleInput(String current, String value) {
        return current.length() >= 128 ? current : current + value;
    }

    private void deleteRuleInputCharacter() {
        if (ruleInputFocus == RuleInputFocus.NONE || ruleInputCursor == 0) return;
        String current = ruleInputValue();
        int cursor = Math.min(ruleInputCursor, current.length());
        setRuleInputValue(current.substring(0, cursor - 1) + current.substring(cursor));
        ruleInputCursor = cursor - 1;
    }

    private void selectAdditionalNbtMatchMode(NbtConditionDraft condition, NbtMatchMode next, int direction) {
        condition.previousMode = condition.mode;
        condition.mode = next;
        condition.direction = direction;
        condition.transition = 0.0F;
    }

    private int additionalNbtMatchModeExpandedHeight() {
        return additionalNbtMatchModeSelectors.stream().mapToInt(UiCyclingSelector::expandedHeight).sum();
    }

    private int additionalNbtConditionY(int firstY, int index) {
        int y = firstY + index * 44;
        for (int previous = 0; previous < index; previous++) y += additionalNbtMatchModeSelectors.get(previous).expandedHeight();
        return y;
    }

    private List<String> nbtMatchModeSymbols() {
        return java.util.Arrays.stream(NbtMatchMode.values()).map(mode -> mode.symbol).toList();
    }

    private UiCyclingSelector createNbtMatchModeSelector(NbtConditionDraft condition) {
        return new UiCyclingSelector(0, 0, 76, nbtMatchModeSymbols(), condition.mode.ordinal(),
                value -> condition.mode = NbtMatchMode.values()[value]);
    }

    private void saveRule() {
        List<DisplayRule.NbtCondition> conditions = new ArrayList<>();
        conditions.add(new DisplayRule.NbtCondition(ruleNbtPath, ruleNbtMatchMode.symbol, ruleNbtValue));
        for (NbtConditionDraft condition : additionalNbtConditions) {
            conditions.add(new DisplayRule.NbtCondition(condition.path, condition.mode.symbol, condition.value));
        }
        DisplayRule rule = new DisplayRule(editingRuleId, ruleName, DisplayRule.Mode.valueOf(ruleMatchMode.name()), ruleItemId,
                ruleNbtPath, ruleNbtMatchMode.symbol, ruleNbtValue, parseRulePriority(), ruleOutlineParameters,
                ruleOutlineSettings == null ? null : ruleOutlineSettings.copy(), conditions);
        if (editingRuleId == null) DisplayRuleManager.add(rule); else DisplayRuleManager.replace(rule);
        ruleName = "";
        ruleItemId = "";
        ruleNbtPath = "";
        ruleNbtValue = "";
        additionalNbtConditions.clear();
        additionalNbtMatchModeSelectors.clear();
        additionalNbtConditionIndex = -1;
        rulePriority = "";
        ruleOutlineParameters = "";
        ruleOutlineSettings = null;
        editingRuleId = null;
    }

    private boolean isRuleComplete() {
        if (ruleMatchMode == RuleMatchMode.NBT_MATCH) {
            if (ruleNbtPath.isBlank()) {
                focusRuleInput(RuleInputFocus.NBT_PATH);
                return false;
            }
            for (int index = 0; index < additionalNbtConditions.size(); index++) {
                NbtConditionDraft condition = additionalNbtConditions.get(index);
                if (condition.path.isBlank()) {
                    focusAdditionalNbtInput(index, RuleInputFocus.ADDITIONAL_NBT_PATH);
                    return false;
                }
            }
            return true;
        }
        if (ruleItemId.isBlank()) {
            focusRuleInput(RuleInputFocus.ITEM);
            return false;
        }
        return true;
    }

    private int parseRulePriority() {
        try {
            return rulePriority.isBlank() ? 0 : Integer.parseInt(rulePriority);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String ruleInputValue() {
        return switch (ruleInputFocus) {
            case NAME -> ruleName;
            case ITEM -> ruleItemId;
            case NBT_PATH -> ruleNbtPath;
            case NBT_VALUE -> ruleNbtValue;
            case ADDITIONAL_NBT_PATH -> additionalNbtConditions.get(additionalNbtConditionIndex).path;
            case ADDITIONAL_NBT_VALUE -> additionalNbtConditions.get(additionalNbtConditionIndex).value;
            case PRIORITY -> rulePriority;
            case OUTLINE_PARAMETERS -> ruleOutlineParameters;
            case NONE -> "";
        };
    }

    private void setRuleInputValue(String value) {
        switch (ruleInputFocus) {
            case NAME -> ruleName = value;
            case ITEM -> ruleItemId = value;
            case NBT_PATH -> ruleNbtPath = value;
            case NBT_VALUE -> ruleNbtValue = value;
            case ADDITIONAL_NBT_PATH -> additionalNbtConditions.get(additionalNbtConditionIndex).path = value;
            case ADDITIONAL_NBT_VALUE -> additionalNbtConditions.get(additionalNbtConditionIndex).value = value;
            case PRIORITY -> rulePriority = value;
            case OUTLINE_PARAMETERS -> ruleOutlineParameters = value;
            case NONE -> { }
        }
    }

    private void focusRuleInput(RuleInputFocus focus) {
        ruleInputFocus = focus;
        additionalNbtConditionIndex = -1;
        ruleInputCursor = ruleInputValue().length();
    }

    private void focusAdditionalNbtInput(int index, RuleInputFocus focus) {
        additionalNbtConditionIndex = index;
        ruleInputFocus = focus;
        ruleInputCursor = ruleInputValue().length();
    }

    private String deleteRuleInputCharacter(String value) {
        return value.isEmpty() ? value : value.substring(0, value.offsetByCodePoints(value.length(), -1));
    }

    private int ruleDialogColor(int color) {
        int alpha = Math.round((color >>> 24) * ruleDialogAnimation);
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    private float ruleHover(String key, boolean hovered) {
        float current = ruleHoverAmounts.getOrDefault(key, 0.0F);
        float next = UiMath.approach(current, hovered ? 1.0F : 0.0F, ruleDialogDelta, 12.0F);
        ruleHoverAmounts.put(key, next);
        return next;
    }

    private int presetDialogColor(int color) {
        int alpha = Math.round((color >>> 24) * presetDialogAnimation);
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    private void drawRuleDialogText(GuiGraphics graphics, String text, float x, float y, float scale, int color) {
        SmoothTextRenderer.draw(graphics, font, text, x, y, scale, ruleDialogColor(color), ruleDialogAnimation);
    }

    private int ruleDialogViewportHeight() {
        return RULE_DIALOG_HEIGHT - 73;
    }

    private float clampRuleDialogScroll(float value) {
        return Math.max(0.0F, Math.min(Math.max(0, ruleDialogContentHeight - ruleDialogViewportHeight()), value));
    }

    private void renderRuleDialogScrollBar(GuiGraphics graphics, int x, int y, int height) {
        int maximum = Math.max(0, ruleDialogContentHeight - height);
        if (maximum == 0) return;
        int trackX = x + RULE_DIALOG_WIDTH - 7;
        int thumbHeight = Math.max(18, Math.round(height * height / (float) ruleDialogContentHeight));
        int thumbY = y + Math.round((height - thumbHeight) * ruleDialogScroll / maximum);
        graphics.fill(trackX, y, trackX + 1, y + height, ruleDialogColor(UiPalette.DIVIDER));
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, ruleDialogColor(UiPalette.PALE_BLUE));
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        renderPreviewGrid(graphics);
        String itemName = previewItem.getHoverName().getString();
        boolean hovered = isPreviewItemLabel(mouseX, mouseY);
        previewItemNameHover = UiMath.approach(previewItemNameHover, hovered ? 1.0F : 0.0F, previewDeltaSeconds(), 12.0F);
        int itemNameColor = UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, previewItemNameHover);
        SmoothTextRenderer.draw(graphics, font, itemName, PREVIEW_X + 14, PREVIEW_Y + 12, 0.88F, itemNameColor);
        int itemNameWidth = SmoothTextRenderer.width(itemName, 0.88F, itemNameColor);
        graphics.fill(PREVIEW_X + 14, PREVIEW_Y + 29, PREVIEW_X + 14 + Math.round(itemNameWidth * previewItemNameHover), PREVIEW_Y + 30, itemNameColor);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.preview.change_item"), PREVIEW_X + 14, PREVIEW_Y + 31, 0.56F, UiPalette.MUTED_TEXT);

        int previewLeft = Math.round(originX + PREVIEW_X * uiScale);
        int previewTop = Math.round(originY + PREVIEW_Y * uiScale);
        int previewRight = Math.round(originX + (PREVIEW_X + PREVIEW_WIDTH) * uiScale);
        int previewBottom = Math.round(originY + (PREVIEW_Y + PREVIEW_HEIGHT) * uiScale);
        ((GuiGraphicsAccessor) graphics).itemglintrelight$getGuiRenderState().submitPicturesInPictureState(
                new ItemPreviewRenderState(previewItem.copy(), previewLeft, previewTop, previewRight, previewBottom,
                        previewZoom * uiScale, previewPitch, previewYaw, previewRoll, previewOffsetX, previewOffsetY, previewZoom / 72.0F, model.draft().copy(),
                        new ScreenRectangle(previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop)));
    }

    private void renderRuleOutlineParameterField(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        drawRuleDialogText(graphics, tr("ui.itemglintrelight.rules.outline_parameters"), x, y, 0.68F, UiPalette.MUTED_TEXT);
        int inputWidth = width - 70;
        boolean focused = ruleInputFocus == RuleInputFocus.OUTLINE_PARAMETERS;
        boolean hovered = UiMath.contains(x, y + 12, inputWidth, 22, mouseX, mouseY);
        float hover = ruleHover("rule-outline-field", focused || hovered);
        graphics.fill(x, y + 12, x + inputWidth, y + 34, ruleDialogColor(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, hover)));
        graphics.fill(x + 1, y + 13, x + inputWidth - 1, y + 33, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, hover)));
        String visible = ruleOutlineParameters.isEmpty() ? tr("ui.itemglintrelight.rules.outline_default") : ruleOutlineParameters;
        float textY = y + 12 + (22 - SmoothTextRenderer.height(visible, 0.68F, UiPalette.TEXT)) * 0.5F;
        drawRuleDialogText(graphics, truncate(visible, inputWidth - 18, 0.68F), x + 8, textY, 0.68F,
                ruleOutlineParameters.isEmpty() ? UiPalette.MUTED_TEXT : UiPalette.TEXT);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 8 + SmoothTextRenderer.width(ruleOutlineParameters.substring(0,
                    Math.min(ruleInputCursor, ruleOutlineParameters.length())), 0.68F, UiPalette.TEXT);
            graphics.fill(cursorX, y + 17, cursorX + 1, y + 29, ruleDialogColor(UiPalette.PALE_BLUE));
        }
        int buttonX = x + inputWidth + 6;
        boolean buttonHovered = UiMath.contains(buttonX, y + 12, 64, 22, mouseX, mouseY);
        float buttonHover = ruleHover("rule-outline-custom", buttonHovered);
        graphics.fill(buttonX, y + 12, buttonX + 64, y + 34, ruleDialogColor(UiMath.mix(UiPalette.DIVIDER, UiPalette.BRIGHT_BLUE, buttonHover)));
        graphics.fill(buttonX + 1, y + 13, buttonX + 63, y + 33, ruleDialogColor(UiMath.mix(UiPalette.SURFACE, UiPalette.SURFACE_HOVER, buttonHover)));
        drawRuleDialogText(graphics, tr("ui.itemglintrelight.rules.custom"), buttonX + 12, y + 18, 0.58F, UiPalette.TEXT);
    }

    private void renderQuickPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (quickPreviewWidth < 1.0F || quickPreviewVisibility < 0.01F) {
            return;
        }
        int panelRight = QUICK_PREVIEW_X + Math.round(quickPreviewWidth);
        ConfigUiBackground.renderCompanionPanel(graphics, QUICK_PREVIEW_X, top, panelRight, bottom, quickPreviewVisibility);
        boolean expanded = quickPreviewWidth > QUICK_PREVIEW_COLLAPSED_WIDTH + 8.0F && quickPreviewVisibility >= 0.01F;
        if (expanded) {
            graphics.enableScissor(QUICK_PREVIEW_X + 1, top + 1, panelRight - 1, bottom - 1);
            SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.quick_preview"), QUICK_PREVIEW_X + 12, top + 12, 0.68F, quickPreviewColor(UiPalette.TEXT));
            graphics.fill(QUICK_PREVIEW_X + 12, top + 30, panelRight - 12, top + 31, quickPreviewColor(UiPalette.DIVIDER));
            int canvasLeft = QUICK_PREVIEW_X + 10;
            int canvasTop = top + 40;
            int canvasRight = panelRight - 10;
            int canvasBottom = bottom - 10;
            for (int x = canvasLeft; x < canvasRight; x += 12) {
                graphics.fill(x, canvasTop, x + 1, canvasBottom, quickPreviewColor((x - canvasLeft) % 48 == 0 ? 0x4A1D4664 : 0x261D4664));
            }
            for (int y = canvasTop; y < canvasBottom; y += 12) {
                graphics.fill(canvasLeft, y, canvasRight, y + 1, quickPreviewColor((y - canvasTop) % 48 == 0 ? 0x4A1D4664 : 0x261D4664));
            }
            graphics.disableScissor();
            if (page != Page.PREVIEW) {
                int previewLeft = Math.round(originX + canvasLeft * uiScale);
                int previewTop = Math.round(originY + canvasTop * uiScale);
                int previewRight = Math.round(originX + canvasRight * uiScale);
                int previewBottom = Math.round(originY + canvasBottom * uiScale);
                ((GuiGraphicsAccessor) graphics).itemglintrelight$getGuiRenderState().submitPicturesInPictureState(
                        new ItemPreviewRenderState(quickPreviewItem.copy(), previewLeft, previewTop, previewRight, previewBottom,
                                quickPreviewZoom * 16.0F * uiScale, quickPreviewPitch, quickPreviewYaw, quickPreviewRoll, 0.0F, 0.0F,
                                quickPreviewZoom * 16.0F / 72.0F, model.draft().copy(),
                                new ScreenRectangle(previewLeft, previewTop, previewRight - previewLeft, previewBottom - previewTop)));
            }
        }
        int toggleLeft = panelRight - 22;
        boolean hovered = isQuickPreviewToggle(mouseX, mouseY);
        quickPreviewToggleHover = UiMath.approach(quickPreviewToggleHover, hovered ? 1.0F : 0.0F, 0.016F, 12.0F);
        graphics.fill(toggleLeft, top + 8, panelRight - 8, top + 26,
                quickPreviewColor(UiMath.mix(UiPalette.DEEP_BLUE, UiPalette.SURFACE_HOVER, quickPreviewToggleHover)));
        SmoothTextRenderer.draw(graphics, font, quickPreviewCollapsed ? ">" : "<", toggleLeft + 6, top + 12, 0.66F,
                quickPreviewColor(UiMath.mix(UiPalette.PALE_BLUE, UiPalette.LIGHT_GREEN, quickPreviewToggleHover)));
    }

    private void updateQuickPreviewLayout() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastQuickPreviewFrame) / 1_000_000_000.0F);
        lastQuickPreviewFrame = now;
        float targetWidth = quickPreviewCollapsed ? QUICK_PREVIEW_COLLAPSED_WIDTH : QUICK_PREVIEW_EXPANDED_WIDTH;
        float targetVisibility = page == Page.PREVIEW ? 0.0F : 1.0F;
        quickPreviewWidth = UiMath.approach(quickPreviewWidth, targetWidth, delta, 12.0F);
        quickPreviewVisibility = UiMath.approach(quickPreviewVisibility, targetVisibility, delta, 12.0F);
        float expandedOriginX = (this.width - (QUICK_PREVIEW_X + QUICK_PREVIEW_EXPANDED_WIDTH) * uiScale) / 2.0F;
        float collapsedOriginX = (this.width - right * uiScale) / 2.0F;
        float progress = (quickPreviewWidth - QUICK_PREVIEW_COLLAPSED_WIDTH)
                / (float) (QUICK_PREVIEW_EXPANDED_WIDTH - QUICK_PREVIEW_COLLAPSED_WIDTH) * quickPreviewVisibility;
        originX = collapsedOriginX + (expandedOriginX - collapsedOriginX) * Math.max(0.0F, Math.min(1.0F, progress));
    }

    private boolean isQuickPreviewToggle(double mouseX, double mouseY) {
        if (page == Page.PREVIEW || quickPreviewWidth < 1.0F || quickPreviewVisibility < 0.01F) return false;
        int panelRight = QUICK_PREVIEW_X + Math.round(quickPreviewWidth);
        return mouseX >= panelRight - 22 && mouseX < panelRight - 8 && mouseY >= top + 8 && mouseY < top + 26;
    }

    private boolean isPreviewItemLabel(double mouseX, double mouseY) {
        int labelWidth = Math.max(96, Math.round(SmoothTextRenderer.width(previewItem.getHoverName().getString(), 0.88F, UiPalette.BRIGHT_BLUE)));
        return mouseX >= PREVIEW_X + 10 && mouseX < PREVIEW_X + 16 + labelWidth
                && mouseY >= PREVIEW_Y + 8 && mouseY < PREVIEW_Y + 29;
    }

    private void openItemPicker() {
        itemPickerOpen = true;
        itemPickerAnimation = 0.0F;
        itemSearch = "";
        itemSearchCursor = 0;
        itemPickerScroll = 0;
        itemPickerScrollTarget = 0;
        lastPickerScrollFrame = System.nanoTime();
        filterItemChoices();
    }

    private void rebuildItemChoices() {
        allItemChoices.clear();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier id = entry.getKey().identifier();
            if (entry.getValue() == Items.AIR) continue;
            ItemStack stack = new ItemStack(entry.getValue());
            String name = stack.getHoverName().getString();
            String modName = FabricLoader.getInstance().getModContainer(id.getNamespace())
                    .map(container -> container.getMetadata().getName()).orElse(id.getNamespace());
            allItemChoices.add(new ItemChoice(stack, name, id.toString(), id.getNamespace(), modName));
        }
        allItemChoices.sort(Comparator.comparing(ItemChoice::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ItemChoice::id, String.CASE_INSENSITIVE_ORDER));
        filterItemChoices();
    }

    private void rebuildRuleTargetChoices() {
        allRuleTargetChoices.clear();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Identifier id = entry.getKey().identifier();
            if (entry.getValue() == Items.AIR) continue;
            ItemStack stack = new ItemStack(entry.getValue());
            allRuleTargetChoices.add(new RuleTargetChoice(RuleTargetKind.ITEM, stack, stack.getHoverName().getString(), id.toString(), id.toString()));
        }
        FabricLoader.getInstance().getAllMods().forEach(container -> {
            String id = container.getMetadata().getId();
            allRuleTargetChoices.add(new RuleTargetChoice(RuleTargetKind.MOD, ItemStack.EMPTY, container.getMetadata().getName(), "@" + id, id));
        });
        if (minecraft != null && minecraft.level != null) {
            minecraft.level.registryAccess().lookupOrThrow(Registries.ITEM).listTags().forEach(tag -> {
                Identifier id = tag.key().location();
                allRuleTargetChoices.add(new RuleTargetChoice(RuleTargetKind.TAG, ItemStack.EMPTY,
                        tr("ui.itemglintrelight.rules.target.tag"), "#" + id, id.toString()));
            });
        }
        allRuleTargetChoices.sort(Comparator.comparing((RuleTargetChoice choice) -> choice.kind().ordinal())
                .thenComparing(RuleTargetChoice::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RuleTargetChoice::value, String.CASE_INSENSITIVE_ORDER));
        filterRuleTargetChoices();
    }

    private void filterRuleTargetChoices() {
        String query = ruleTargetSearch.trim().toLowerCase(Locale.ROOT);
        filteredRuleTargetChoices = allRuleTargetChoices.stream()
                .filter(choice -> query.startsWith("#") ? choice.kind() == RuleTargetKind.TAG && choice.value().toLowerCase(Locale.ROOT).contains(query)
                        : query.startsWith("@") ? choice.kind() == RuleTargetKind.MOD && choice.value().toLowerCase(Locale.ROOT).contains(query)
                        : query.isEmpty() || choice.value().toLowerCase(Locale.ROOT).contains(query)
                        || choice.label().toLowerCase(Locale.ROOT).contains(query)
                        || choice.searchText().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        ruleTargetPickerScroll = clampRuleTargetPickerScroll(ruleTargetPickerScroll);
        ruleTargetPickerScrollTarget = clampRuleTargetPickerScroll(ruleTargetPickerScrollTarget);
    }

    private void filterItemChoices() {
        String query = itemSearch.trim().toLowerCase(Locale.ROOT);
        if (query.startsWith("@")) {
            String modQuery = query.substring(1).trim();
            filteredItemChoices = allItemChoices.stream()
                    .filter(choice -> choice.namespace().toLowerCase(Locale.ROOT).contains(modQuery)
                            || choice.modName().toLowerCase(Locale.ROOT).contains(modQuery))
                    .toList();
        } else if (query.isEmpty()) {
            filteredItemChoices = List.copyOf(allItemChoices);
        } else {
            filteredItemChoices = allItemChoices.stream()
                    .filter(choice -> choice.name().toLowerCase(Locale.ROOT).contains(query) || choice.id().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }
        itemPickerScroll = clampItemPickerScroll(itemPickerScroll);
        itemPickerScrollTarget = clampItemPickerScroll(itemPickerScrollTarget);
    }

    private void renderItemPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastPickerScrollFrame) / 1_000_000_000.0F);
        lastPickerScrollFrame = now;
        itemPickerAnimation = UiMath.approach(itemPickerAnimation, 1.0F, delta, 9.0F);
        itemPickerScroll = UiMath.approach(itemPickerScroll, itemPickerScrollTarget, delta, 18.0F);
        int x = ITEM_PICKER_X;
        int y = ITEM_PICKER_Y;
        int rightEdge = x + ITEM_PICKER_WIDTH;
        int bottomEdge = y + ITEM_PICKER_HEIGHT;
        float scale = 0.96F + itemPickerAnimation * 0.04F;
        float centerX = x + ITEM_PICKER_WIDTH * 0.5F;
        float centerY = y + ITEM_PICKER_HEIGHT * 0.5F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        graphics.fill(x, y, rightEdge, bottomEdge, pickerColor(0xF20A1724));
        graphics.fill(x, y, rightEdge, y + 1, pickerColor(UiPalette.DIVIDER));
        graphics.fill(x, bottomEdge - 1, rightEdge, bottomEdge, pickerColor(UiPalette.DIVIDER));
        graphics.fill(x, y, x + 1, bottomEdge, pickerColor(UiPalette.DIVIDER));
        graphics.fill(rightEdge - 1, y, rightEdge, bottomEdge, pickerColor(UiPalette.DIVIDER));
        drawPickerText(graphics, tr("ui.itemglintrelight.preview.picker.title"), x + 12, y + 10, 0.76F, UiPalette.TEXT);
        String count = tr("ui.itemglintrelight.preview.picker.count", filteredItemChoices.size());
        int countX = Math.max(x + 12, rightEdge - 12 - SmoothTextRenderer.width(count, 0.58F, UiPalette.MUTED_TEXT));
        drawPickerText(graphics, count, countX, y + 12, 0.58F, UiPalette.MUTED_TEXT);

        int searchY = y + 31;
        graphics.fill(x + 12, searchY, rightEdge - 12, searchY + 22, pickerColor(UiPalette.DIVIDER));
        graphics.fill(x + 13, searchY + 1, rightEdge - 13, searchY + 21, pickerColor(UiPalette.SURFACE));
        String searchText = itemSearch.isEmpty() ? tr("ui.itemglintrelight.preview.picker.search") : itemSearch;
        int searchColor = itemSearch.isEmpty() ? UiPalette.MUTED_TEXT : UiPalette.TEXT;
        drawPickerText(graphics, searchText, x + 20,
                searchY + (22 - SmoothTextRenderer.height(searchText, 0.62F, searchColor)) * 0.5F, 0.62F, searchColor);
        if ((System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 20 + Math.round(SmoothTextRenderer.width(itemSearch.substring(0, itemSearchCursor), 0.62F, UiPalette.TEXT));
            graphics.fill(cursorX, searchY + 5, cursorX + 1, searchY + 17, pickerColor(UiPalette.PALE_BLUE));
        }

        int rowsY = searchY + 30;
        int visibleRows = itemPickerVisibleRows();
        int firstRow = (int) Math.floor(itemPickerScroll);
        int rowOffset = Math.round((itemPickerScroll - firstRow) * ITEM_PICKER_ROW_HEIGHT);
        graphics.enableScissor(x + 2, rowsY, rightEdge - 2, bottomEdge - 4);
        for (int row = 0; row <= visibleRows; row++) {
            int index = firstRow + row;
            if (index >= filteredItemChoices.size()) break;
            ItemChoice choice = filteredItemChoices.get(index);
            int rowY = rowsY + row * ITEM_PICKER_ROW_HEIGHT - rowOffset;
            boolean hovered = mouseX >= x + 6 && mouseX < rightEdge - 6 && mouseY >= rowY && mouseY < rowY + ITEM_PICKER_ROW_HEIGHT - 2;
            graphics.fill(x + 8, rowY, rightEdge - 8, rowY + ITEM_PICKER_ROW_HEIGHT - 2,
                    pickerColor(hovered ? UiPalette.SURFACE_HOVER : UiPalette.DEEP_BLUE_FADE));
            graphics.renderItem(choice.stack(), x + 14, rowY + 7);
            int iconVeilAlpha = Math.round((1.0F - itemPickerAnimation) * 255.0F);
            if (iconVeilAlpha > 0) {
                graphics.fill(x + 14, rowY + 7, x + 30, rowY + 23, iconVeilAlpha << 24 | 0x000A1724);
            }
            drawPickerText(graphics, truncate(choice.name(), 214, 0.70F), x + 38, rowY + 6, 0.70F, UiPalette.TEXT);
            drawPickerText(graphics, truncate(choice.id(), 300, 0.54F), x + 38, rowY + 19, 0.54F, UiPalette.MUTED_TEXT);
        }
        graphics.disableScissor();
        renderItemPickerScrollBar(graphics, x, rowsY, visibleRows);
        graphics.pose().popMatrix();
    }

    private boolean handleItemPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int x = ITEM_PICKER_X;
        int y = ITEM_PICKER_Y;
        if (mouseX < x || mouseX >= x + ITEM_PICKER_WIDTH || mouseY < y || mouseY >= y + ITEM_PICKER_HEIGHT) {
            closeItemPicker();
            return true;
        }
        int rowsY = y + 61;
        int row = (int) ((mouseY - rowsY + (itemPickerScroll - Math.floor(itemPickerScroll)) * ITEM_PICKER_ROW_HEIGHT) / ITEM_PICKER_ROW_HEIGHT);
        int index = (int) Math.floor(itemPickerScroll) + row;
        if (mouseY >= rowsY && row >= 0 && row < itemPickerVisibleRows() && index < filteredItemChoices.size()) {
            selectPreviewItem(filteredItemChoices.get(index));
        }
        return true;
    }

    private void selectPreviewItem(ItemChoice choice) {
        previewItem = choice.stack().copy();
        closeItemPicker();
    }

    private void closeItemPicker() {
        itemPickerOpen = false;
        itemPickerAnimation = 0.0F;
    }

    private void openRuleTargetPicker() {
        rebuildRuleTargetChoices();
        ruleTargetPickerOpen = true;
        ruleTargetPickerAnimation = 0.0F;
        ruleTargetSearch = "";
        ruleTargetSearchCursor = 0;
        ruleTargetPickerScroll = 0.0F;
        ruleTargetPickerScrollTarget = 0.0F;
        lastRuleTargetPickerFrame = System.nanoTime();
        filterRuleTargetChoices();
    }

    private void closeRuleTargetPicker() {
        ruleTargetPickerOpen = false;
        ruleTargetPickerAnimation = 0.0F;
    }

    private void renderRuleTargetPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRuleTargetPickerFrame) / 1_000_000_000.0F);
        lastRuleTargetPickerFrame = now;
        ruleTargetPickerAnimation = UiMath.approach(ruleTargetPickerAnimation, 1.0F, delta, 9.0F);
        ruleTargetPickerScroll = UiMath.approach(ruleTargetPickerScroll, ruleTargetPickerScrollTarget, delta, 18.0F);
        int x = ITEM_PICKER_X;
        int y = ITEM_PICKER_Y;
        int rightEdge = x + ITEM_PICKER_WIDTH;
        int bottomEdge = y + ITEM_PICKER_HEIGHT;
        float scale = 0.96F + ruleTargetPickerAnimation * 0.04F;
        float centerX = x + ITEM_PICKER_WIDTH * 0.5F;
        float centerY = y + ITEM_PICKER_HEIGHT * 0.5F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-centerX, -centerY);
        graphics.fill(x, y, rightEdge, bottomEdge, ruleTargetPickerColor(0xF20A1724));
        graphics.fill(x, y, rightEdge, y + 1, ruleTargetPickerColor(UiPalette.DIVIDER));
        graphics.fill(x, bottomEdge - 1, rightEdge, bottomEdge, ruleTargetPickerColor(UiPalette.DIVIDER));
        graphics.fill(x, y, x + 1, bottomEdge, ruleTargetPickerColor(UiPalette.DIVIDER));
        graphics.fill(rightEdge - 1, y, rightEdge, bottomEdge, ruleTargetPickerColor(UiPalette.DIVIDER));
        drawRuleTargetPickerText(graphics, tr("ui.itemglintrelight.rules.target.title"), x + 12, y + 10, 0.76F, UiPalette.TEXT);
        String count = tr("ui.itemglintrelight.rules.target.count", filteredRuleTargetChoices.size());
        int countX = Math.max(x + 12, rightEdge - 12 - SmoothTextRenderer.width(count, 0.58F, UiPalette.MUTED_TEXT));
        drawRuleTargetPickerText(graphics, count, countX, y + 12, 0.58F, UiPalette.MUTED_TEXT);
        int searchY = y + 31;
        graphics.fill(x + 12, searchY, rightEdge - 12, searchY + 22, ruleTargetPickerColor(UiPalette.DIVIDER));
        graphics.fill(x + 13, searchY + 1, rightEdge - 13, searchY + 21, ruleTargetPickerColor(UiPalette.SURFACE));
        String searchText = ruleTargetSearch.isEmpty() ? tr("ui.itemglintrelight.rules.target.search") : ruleTargetSearch;
        int ruleTargetSearchColor = ruleTargetSearch.isEmpty() ? UiPalette.MUTED_TEXT : UiPalette.TEXT;
        drawRuleTargetPickerText(graphics, searchText, x + 20,
                searchY + (22 - SmoothTextRenderer.height(searchText, 0.62F, ruleTargetSearchColor)) * 0.5F, 0.62F, ruleTargetSearchColor);
        if ((System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 20 + Math.round(SmoothTextRenderer.width(ruleTargetSearch.substring(0, ruleTargetSearchCursor), 0.62F, UiPalette.TEXT));
            graphics.fill(cursorX, searchY + 5, cursorX + 1, searchY + 17, ruleTargetPickerColor(UiPalette.PALE_BLUE));
        }
        int rowsY = searchY + 30;
        int visibleRows = itemPickerVisibleRows();
        int firstRow = (int) Math.floor(ruleTargetPickerScroll);
        int rowOffset = Math.round((ruleTargetPickerScroll - firstRow) * ITEM_PICKER_ROW_HEIGHT);
        graphics.enableScissor(x + 2, rowsY, rightEdge - 2, bottomEdge - 4);
        for (int row = 0; row <= visibleRows; row++) {
            int index = firstRow + row;
            if (index >= filteredRuleTargetChoices.size()) break;
            RuleTargetChoice choice = filteredRuleTargetChoices.get(index);
            int rowY = rowsY + row * ITEM_PICKER_ROW_HEIGHT - rowOffset;
            boolean hovered = mouseX >= x + 6 && mouseX < rightEdge - 6 && mouseY >= rowY && mouseY < rowY + ITEM_PICKER_ROW_HEIGHT - 2;
            graphics.fill(x + 8, rowY, rightEdge - 8, rowY + ITEM_PICKER_ROW_HEIGHT - 2,
                    ruleTargetPickerColor(hovered ? UiPalette.SURFACE_HOVER : UiPalette.DEEP_BLUE_FADE));
            if (choice.kind() == RuleTargetKind.ITEM) {
                graphics.renderItem(choice.stack(), x + 14, rowY + 7);
            } else {
                String marker = choice.kind() == RuleTargetKind.MOD ? "@" : "#";
                drawRuleTargetPickerText(graphics, marker, x + 17, rowY + 8, 0.88F, UiPalette.PALE_BLUE);
            }
            drawRuleTargetPickerText(graphics, truncate(choice.label(), 214, 0.70F), x + 38, rowY + 6, 0.70F, UiPalette.TEXT);
            drawRuleTargetPickerText(graphics, truncate(choice.value(), 300, 0.54F), x + 38, rowY + 19, 0.54F, UiPalette.MUTED_TEXT);
        }
        graphics.disableScissor();
        renderRuleTargetPickerScrollBar(graphics, x, rowsY, visibleRows);
        graphics.pose().popMatrix();
    }

    private boolean handleRuleTargetPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        int x = ITEM_PICKER_X;
        int y = ITEM_PICKER_Y;
        if (mouseX < x || mouseX >= x + ITEM_PICKER_WIDTH || mouseY < y || mouseY >= y + ITEM_PICKER_HEIGHT) {
            closeRuleTargetPicker();
            return true;
        }
        int rowsY = y + 61;
        int row = (int) ((mouseY - rowsY + (ruleTargetPickerScroll - Math.floor(ruleTargetPickerScroll)) * ITEM_PICKER_ROW_HEIGHT) / ITEM_PICKER_ROW_HEIGHT);
        int index = (int) Math.floor(ruleTargetPickerScroll) + row;
        if (mouseY >= rowsY && row >= 0 && row < itemPickerVisibleRows() && index < filteredRuleTargetChoices.size()) {
            ruleItemId = filteredRuleTargetChoices.get(index).value();
            focusRuleInput(RuleInputFocus.ITEM);
            closeRuleTargetPicker();
        }
        return true;
    }

    private boolean handleRuleTargetPickerKey(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeRuleTargetPicker();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && !ruleTargetSearch.isEmpty()) {
            if (ruleTargetSearchCursor > 0) {
                ruleTargetSearch = ruleTargetSearch.substring(0, ruleTargetSearchCursor - 1) + ruleTargetSearch.substring(ruleTargetSearchCursor);
                ruleTargetSearchCursor--;
            }
            filterRuleTargetChoices();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            ruleTargetSearchCursor = Math.max(0, ruleTargetSearchCursor - 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            ruleTargetSearchCursor = Math.min(ruleTargetSearch.length(), ruleTargetSearchCursor + 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && !filteredRuleTargetChoices.isEmpty()) {
            ruleItemId = filteredRuleTargetChoices.get(0).value();
            focusRuleInput(RuleInputFocus.ITEM);
            closeRuleTargetPicker();
            return true;
        }
        return true;
    }

    private float clampRuleTargetPickerScroll(float value) {
        return Math.max(0.0F, Math.min(Math.max(0, filteredRuleTargetChoices.size() - itemPickerVisibleRows()), value));
    }

    private void renderRuleTargetPickerScrollBar(GuiGraphics graphics, int x, int rowsY, int visibleRows) {
        if (filteredRuleTargetChoices.size() <= visibleRows) return;
        int trackX = x + ITEM_PICKER_WIDTH - 7;
        int trackHeight = visibleRows * ITEM_PICKER_ROW_HEIGHT - 2;
        int thumbHeight = Math.max(20, Math.round(trackHeight * visibleRows / (float) filteredRuleTargetChoices.size()));
        float maximum = Math.max(1.0F, filteredRuleTargetChoices.size() - visibleRows);
        int thumbY = rowsY + Math.round((trackHeight - thumbHeight) * ruleTargetPickerScroll / maximum);
        graphics.fill(trackX, rowsY, trackX + 1, rowsY + trackHeight, ruleTargetPickerColor(UiPalette.DIVIDER));
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, ruleTargetPickerColor(UiPalette.PALE_BLUE));
    }

    private int ruleTargetPickerColor(int color) {
        int alpha = Math.round((color >>> 24) * ruleTargetPickerAnimation);
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    private void drawRuleTargetPickerText(GuiGraphics graphics, String text, float x, float y, float scale, int color) {
        SmoothTextRenderer.draw(graphics, font, text, x, y, scale, ruleTargetPickerColor(color), ruleTargetPickerAnimation);
    }

    private int itemPickerVisibleRows() {
        return (ITEM_PICKER_HEIGHT - 65) / ITEM_PICKER_ROW_HEIGHT;
    }

    private float clampItemPickerScroll(float value) {
        return Math.max(0.0F, Math.min(Math.max(0, filteredItemChoices.size() - itemPickerVisibleRows()), value));
    }

    private void renderItemPickerScrollBar(GuiGraphics graphics, int x, int rowsY, int visibleRows) {
        if (filteredItemChoices.size() <= visibleRows) return;
        int trackX = x + ITEM_PICKER_WIDTH - 7;
        int trackHeight = visibleRows * ITEM_PICKER_ROW_HEIGHT - 2;
        int thumbHeight = Math.max(20, Math.round(trackHeight * visibleRows / (float) filteredItemChoices.size()));
        float maximum = Math.max(1.0F, filteredItemChoices.size() - visibleRows);
        int thumbY = rowsY + Math.round((trackHeight - thumbHeight) * itemPickerScroll / maximum);
        graphics.fill(trackX, rowsY, trackX + 1, rowsY + trackHeight, pickerColor(UiPalette.DIVIDER));
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, pickerColor(UiPalette.PALE_BLUE));
    }

    private int pickerColor(int color) {
        int alpha = Math.round((color >>> 24) * itemPickerAnimation);
        return alpha << 24 | (color & 0x00FFFFFF);
    }

    private void drawPickerText(GuiGraphics graphics, String text, float x, float y, float scale, int color) {
        SmoothTextRenderer.draw(graphics, font, text, x, y, scale, color, itemPickerAnimation);
    }

    private float previewDeltaSeconds() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastPreviewNameFrame) / 1_000_000_000.0F);
        lastPreviewNameFrame = now;
        return delta;
    }

    private String truncate(String value, int maxWidth, float scale) {
        if (SmoothTextRenderer.width(value, scale, UiPalette.TEXT) <= maxWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && SmoothTextRenderer.width(value.substring(0, end) + suffix, scale, UiPalette.TEXT) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    private void renderPreviewGrid(GuiGraphics graphics) {
        int rightEdge = PREVIEW_X + PREVIEW_WIDTH;
        int bottomEdge = PREVIEW_Y + PREVIEW_HEIGHT;
        for (int x = PREVIEW_X; x < rightEdge; x += 16) {
            graphics.fill(x, PREVIEW_Y, x + 1, bottomEdge, (x - PREVIEW_X) % 64 == 0 ? 0x4A1D4664 : 0x261D4664);
        }
        for (int y = PREVIEW_Y; y < bottomEdge; y += 16) {
            graphics.fill(PREVIEW_X, y, rightEdge, y + 1, (y - PREVIEW_Y) % 64 == 0 ? 0x4A1D4664 : 0x261D4664);
        }
    }

    private void zoomPreview(float mouseX, float mouseY, float wheelDelta) {
        previewZoom = Math.max(16.0F, Math.min(256.0F, previewZoom + wheelDelta * 6.0F));
    }

    private boolean isInPreviewCanvas(double mouseX, double mouseY) {
        return mouseX >= PREVIEW_X && mouseX < PREVIEW_X + PREVIEW_WIDTH
                && mouseY >= PREVIEW_Y && mouseY < PREVIEW_Y + PREVIEW_HEIGHT;
    }

    private boolean isInQuickPreviewCanvas(double mouseX, double mouseY) {
        int panelRight = QUICK_PREVIEW_X + Math.round(quickPreviewWidth);
        return page != Page.PREVIEW && quickPreviewVisibility >= 0.01F && quickPreviewWidth > QUICK_PREVIEW_COLLAPSED_WIDTH + 8.0F
                && mouseX >= QUICK_PREVIEW_X + 10 && mouseX < panelRight - 10
                && mouseY >= top + 40 && mouseY < bottom - 10;
    }

    private int quickPreviewColor(int color) {
        return Math.round((color >>> 24) * quickPreviewVisibility) << 24 | color & 0x00FFFFFF;
    }

    private void layoutRenderControls() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRenderScrollFrame) / 1_000_000_000.0F);
        lastRenderScrollFrame = now;
        renderScroll = UiMath.approach(renderScroll, renderScrollTarget, delta, 16.0F);
        int contentX = sidebarRight + 24;
        int contentWidth = right - contentX - 24;
        int startY = top + 44;
        int scroll = Math.round(renderScroll);
        int y = startY - scroll;
        outlineWidthSlider.setPosition(contentX, y); y += 42;
        outlineSoftnessSlider.setPosition(contentX, y); y += 42;
        outlineThresholdSlider.setPosition(contentX, y); y += 42;
        outlineOpacitySlider.setPosition(contentX, y); y += 42;
        outlineRenderModeDropdown.setPosition(contentX, y); y += 46 + outlineRenderModeDropdown.expandedHeight();
        outlineColorModeDropdown.setPosition(contentX, y); y += 46 + outlineColorModeDropdown.expandedHeight();
        if (usesPrimaryColor()) {
            outlinePrimaryColorPicker.setPosition(contentX, y); y += outlinePrimaryColorPicker.height() + 16;
        }
        if (usesSecondaryColor()) {
            outlineSecondaryColorPicker.setPosition(contentX, y); y += outlineSecondaryColorPicker.height() + 16;
        }
        if (usesColorAnimation()) {
            outlineColorScrollSpeedSlider.setPosition(contentX, y); y += 42;
            outlineColorScrollModeDropdown.setPosition(contentX, y); y += 46 + outlineColorScrollModeDropdown.expandedHeight();
            if (usesPlanarColorScroll()) {
                outlineColorScrollControl.setPosition(contentX, y); y += outlineColorScrollControl.height() + 10;
            } else {
                outlineColorLengthSlider.setPosition(contentX, y); y += 42;
            }
        }
        if (usesTextureSampling()) {
            outlineSampleSizeSlider.setPosition(contentX, y); y += 42;
            outlineSampleColorCountSlider.setPosition(contentX, y); y += 42;
        }
        outlineQualityDropdown.setPosition(contentX, y); y += 46 + outlineQualityDropdown.expandedHeight();
        if (model.draft().outlineThirdPerson()) {
            thirdPersonOutlineQualityDropdown.setPosition(contentX, y); y += 46 + thirdPersonOutlineQualityDropdown.expandedHeight();
        }
        if (model.draft().outlineGuiItems()) {
            guiOutlineQualityDropdown.setPosition(contentX, y); y += 46 + guiOutlineQualityDropdown.expandedHeight();
        }
        outlineGlowIntensitySlider.setPosition(contentX, y); y += 42;
        outlineBloomToggle.setPosition(contentX, y); y += 38;
        if (model.draft().outlineBloomEnabled()) {
            outlineBloomQualityDropdown.setPosition(contentX, y); y += 46 + outlineBloomQualityDropdown.expandedHeight();
            outlineBloomRadiusSlider.setPosition(contentX, y); y += 42;
            outlineBloomIntensitySlider.setPosition(contentX, y); y += 42;
            outlineBloomSoftnessSlider.setPosition(contentX, y); y += 42;
            outlineBloomBlurPassesSlider.setPosition(contentX, y); y += 42;
        }
        y += 40;
        renderContentHeight = y + scroll - startY;
        int maximum = maxRenderScroll();
        renderScrollTarget = Math.max(0.0F, Math.min(renderScrollTarget, maximum));
        renderScroll = Math.max(0.0F, Math.min(renderScroll, maximum));
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int viewportHeight = bottom - RIGHT_CONTENT_BOTTOM - (top + RIGHT_CONTENT_TOP);
        int maximum = maxRenderScroll();
        if (maximum == 0) {
            return;
        }
        int trackX = right - 8;
        int trackY = top + RIGHT_CONTENT_TOP + 4;
        int trackHeight = viewportHeight - 8;
        int thumbHeight = Math.max(18, Math.round(trackHeight * viewportHeight / (float) renderContentHeight));
        int thumbY = trackY + Math.round((trackHeight - thumbHeight) * renderScroll / maximum);
        graphics.fill(trackX, trackY, trackX + 1, trackY + trackHeight, UiPalette.DIVIDER);
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, UiPalette.PALE_BLUE);
    }

    private int maxRenderScroll() {
        return Math.max(0, renderContentHeight - (bottom - RIGHT_CONTENT_BOTTOM - (top + RIGHT_CONTENT_TOP)));
    }

    private boolean usesPrimaryColor() {
        return model.draft().outlineColorMode() == OutlineColorMode.SINGLE || model.draft().outlineColorMode() == OutlineColorMode.DUAL;
    }

    private boolean usesSecondaryColor() {
        return model.draft().outlineColorMode() == OutlineColorMode.DUAL;
    }

    private boolean usesColorAnimation() {
        return model.draft().outlineColorMode() != OutlineColorMode.SINGLE;
    }

    private boolean usesPlanarColorScroll() {
        return usesColorAnimation() && model.draft().outlineColorScrollMode() == ColorScrollMode.PLANAR;
    }

    private boolean usesOutlineColorScroll() {
        return usesColorAnimation() && model.draft().outlineColorScrollMode() == ColorScrollMode.OUTLINE;
    }

    private boolean usesTextureSampling() {
        return model.draft().outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE;
    }

    private void drawPageLabel(GuiGraphics graphics, Page candidate, int y) {
        String label = tr(candidate.labelKey);
        int x = left + 24;
        float fill = navigationFill[candidate.ordinal()];
        SmoothTextRenderer.draw(graphics, font, label, x, y, 0.76F, UiPalette.MUTED_TEXT);
        if (fill <= 0.001F) {
            return;
        }
        int width = Math.max(1, Math.round(SmoothTextRenderer.width(label, 0.76F, UiPalette.BRIGHT_BLUE) * fill));
        int height = SmoothTextRenderer.height(label, 0.76F, UiPalette.BRIGHT_BLUE);
        graphics.enableScissor(x, y - 3, x + width, y + height + 3);
        SmoothTextRenderer.draw(graphics, font, label, x, y, 0.76F, UiPalette.BRIGHT_BLUE);
        graphics.disableScissor();
    }

    private void updateNavigationAnimation() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastNavigationFrame) / 1_000_000_000.0F);
        lastNavigationFrame = now;
        for (Page candidate : Page.values()) {
            float target = candidate == page ? 1.0F : 0.0F;
            navigationFill[candidate.ordinal()] = UiMath.approach(navigationFill[candidate.ordinal()], target, delta, 10.0F);
        }
    }

    private Page pageAt(double mouseX, double mouseY) {
        if (mouseX < left + 16 || mouseX >= sidebarRight - 10) {
            return null;
        }
        if (mouseY >= top + 78 && mouseY < top + 108) return Page.GENERAL;
        if (mouseY >= top + 108 && mouseY < top + 138) return Page.RENDER;
        if (mouseY >= top + 138 && mouseY < top + 168) return Page.RULES;
        if (mouseY >= top + 168 && mouseY < top + 198) return Page.PREVIEW;
        return null;
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private record ItemChoice(ItemStack stack, String name, String id, String namespace, String modName) { }

    private record RuleTargetChoice(RuleTargetKind kind, ItemStack stack, String label, String value, String searchText) { }

    private enum RuleTargetKind {
        ITEM,
        MOD,
        TAG
    }

    private enum NbtMatchMode {
        EQUAL("="),
        NOT_EQUAL("!="),
        GREATER_THAN(">"),
        LESS_THAN("<"),
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        CONTAINS("⊃"),
        NOT_CONTAINS("!⊃"),
        CONTAINED_BY("⊂"),
        NOT_CONTAINED_BY("!⊂");

        private final String symbol;

        NbtMatchMode(String symbol) {
            this.symbol = symbol;
        }

        private NbtMatchMode previous() {
            return values()[(ordinal() + values().length - 1) % values().length];
        }

        private NbtMatchMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        private static NbtMatchMode fromSymbol(String symbol) {
            for (NbtMatchMode value : values()) {
                if (value.symbol.equals(symbol)) return value;
            }
            return EQUAL;
        }
    }

    private enum RuleMatchMode {
        WHITELIST,
        NBT_MATCH,
        BLACKLIST
    }

    private enum RuleInputFocus {
        NONE,
        NAME,
        ITEM,
        NBT_PATH,
        NBT_VALUE,
        ADDITIONAL_NBT_PATH,
        ADDITIONAL_NBT_VALUE,
        PRIORITY,
        OUTLINE_PARAMETERS
    }

    private static final class NbtConditionDraft {
        private String path;
        private NbtMatchMode mode;
        private NbtMatchMode previousMode;
        private float transition = 1.0F;
        private int direction = 1;
        private String value;

        private NbtConditionDraft(String path, NbtMatchMode mode, String value) {
            this.path = path;
            this.mode = mode;
            this.previousMode = mode;
            this.value = value;
        }
    }

    private enum Page {
        GENERAL("ui.itemglintrelight.nav.general"),
        RENDER("ui.itemglintrelight.nav.render"),
        RULES("ui.itemglintrelight.nav.rules"),
        PREVIEW("ui.itemglintrelight.nav.preview");

        private final String labelKey;

        Page(String labelKey) {
            this.labelKey = labelKey;
        }
    }
}
