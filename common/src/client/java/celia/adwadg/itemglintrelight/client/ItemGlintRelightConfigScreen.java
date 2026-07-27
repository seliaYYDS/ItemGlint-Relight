package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.client.ui.ConfigUiBackground;
import celia.adwadg.itemglintrelight.client.ui.SmoothTextRenderer;
import celia.adwadg.itemglintrelight.client.ui.UiButton;
import celia.adwadg.itemglintrelight.client.ui.UiColorPicker;
import celia.adwadg.itemglintrelight.client.ui.UiColorScrollControl;
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
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.OutlineRenderMode;
import celia.adwadg.itemglintrelight.config.ColorScrollMode;
import celia.adwadg.itemglintrelight.config.ui.ItemGlintRelightConfigScreenModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private UiSlider outlineGlowIntensitySlider;
    private UiToggle outlineBloomToggle;
    private UiDropdown outlineBloomQualityDropdown;
    private UiSlider outlineBloomRadiusSlider;
    private UiSlider outlineBloomIntensitySlider;
    private UiSlider outlineBloomBlurPassesSlider;
    private UiDropdown outlineColorModeDropdown;
    private UiColorPicker outlinePrimaryColorPicker;
    private UiColorPicker outlineSecondaryColorPicker;
    private UiSlider outlineColorScrollSpeedSlider;
    private UiDropdown outlineColorScrollModeDropdown;
    private UiColorScrollControl outlineColorScrollControl;
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
    private float itemPickerScroll;
    private float itemPickerScrollTarget;
    private float previewItemNameHover;
    private long lastPreviewNameFrame;
    private long lastPickerScrollFrame;
    private final List<RuleTargetChoice> allRuleTargetChoices = new ArrayList<>();
    private List<RuleTargetChoice> filteredRuleTargetChoices = List.of();
    private boolean ruleTargetPickerOpen;
    private float ruleTargetPickerAnimation;
    private String ruleTargetSearch = "";
    private float ruleTargetPickerScroll;
    private float ruleTargetPickerScrollTarget;
    private long lastRuleTargetPickerFrame;
    private UiDropdown ruleModeDropdown;
    private boolean ruleDialogOpen;
    private RuleMatchMode ruleMatchMode = RuleMatchMode.WHITELIST;
    private RuleInputFocus ruleInputFocus = RuleInputFocus.NONE;
    private String ruleName = "";
    private String ruleItemId = "";
    private String ruleNbtPath = "";
    private String ruleNbtValue = "";
    private NbtMatchMode ruleNbtMatchMode = NbtMatchMode.EQUAL;
    private float addRuleHover;
    private long lastRuleFrame;
    private float ruleDialogAnimation;
    private long lastRuleDialogFrame;
    private float ruleDialogScroll;
    private float ruleDialogScrollTarget;
    private int ruleDialogContentHeight;
    private NbtMatchMode previousNbtMatchMode = NbtMatchMode.EQUAL;
    private float nbtMatchModeTransition = 1.0F;
    private int nbtMatchModeDirection = 1;
    private float nbtPreviousArrowHover;
    private float nbtNextArrowHover;
    private float nbtCenterHover;
    private boolean nbtMatchModeExpanded;
    private float nbtMatchModeExpansion;
    private final float[] nbtMatchModeOptionHovers = new float[NbtMatchMode.values().length];
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
        uiScale = Math.min(1.0F, Math.min((this.width - 28.0F) / (right + 4.0F), (this.height - 28.0F) / (bottom + 4.0F)));
        uiScale = Math.max(0.35F, uiScale);
        originX = (this.width - right * uiScale) / 2.0F;
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
        outlineGlowIntensitySlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.glow_intensity"), 0.0D, 2.0D, 0.01D,
                () -> model.draft().outlineGlowIntensity(), value -> model.draft().setOutlineGlowIntensity(value.floatValue()));
        outlineBloomToggle = new UiToggle(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_enabled"),
                () -> model.draft().outlineBloomEnabled(), value -> model.draft().setOutlineBloomEnabled(value));
        outlineBloomQualityDropdown = new UiDropdown(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_quality"), qualityOptions,
                model.draft().outlineBloomQuality().ordinal(), value -> model.draft().setOutlineBloomQuality(RenderQuality.values()[value]));
        outlineBloomRadiusSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_radius"), 0.25D, 10.0D, 0.05D,
                () -> model.draft().outlineBloomRadius(), value -> model.draft().setOutlineBloomRadius(value.floatValue()));
        outlineBloomIntensitySlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.bloom_intensity"), 0.25D, 8.0D, 0.05D,
                () -> model.draft().outlineBloomIntensity(), value -> model.draft().setOutlineBloomIntensity(value.floatValue()));
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
        outlineSampleSizeSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.sample_size"), 1.0D, 8.0D, 1.0D,
                () -> model.draft().outlineSampleSize(), value -> model.draft().setOutlineSampleSize(value.intValue()));
        outlineSampleColorCountSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.sample_color_count"), 1.0D, 8.0D, 1.0D,
                () -> model.draft().outlineSampleColorCount(), value -> model.draft().setOutlineSampleColorCount(value.intValue()));
        ruleModeDropdown = new UiDropdown(0, 0, RULE_DIALOG_WIDTH - 36, tr("ui.itemglintrelight.rules.match_mode"),
                List.of(tr("ui.itemglintrelight.rules.mode.whitelist"), tr("ui.itemglintrelight.rules.mode.nbt_match"), tr("ui.itemglintrelight.rules.mode.blacklist")),
                ruleMatchMode.ordinal(), value -> ruleMatchMode = RuleMatchMode.values()[value]);
        mouseGlow = new UiMouseGlow();
        starParticles = new UiTrailStarParticles();
        rebuildItemChoices();
        rebuildRuleTargetChoices();
        navigationFill[page.ordinal()] = 1.0F;
        lastNavigationFrame = System.nanoTime();
        lastPageTransitionFrame = System.nanoTime();
        lastPreviewNameFrame = System.nanoTime();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.getWindow().isIconified()) {
            return;
        }
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
        saveButton.render(graphics, font, logicalMouseX, logicalMouseY);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.save_hint"), sidebarRight + 24, bottom - 35, 0.66F, UiPalette.MUTED_TEXT);
        if (itemPickerOpen) {
            renderItemPicker(graphics, logicalMouseX, logicalMouseY);
        }
        if (ruleDialogOpen) {
            renderRuleDialog(graphics, logicalMouseX, logicalMouseY);
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
        if (ruleDialogOpen) {
            return handleRuleDialogClick(mouseX, mouseY, event.button());
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
            openRuleDialog();
            return true;
        }
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
            if (usesColorAnimation() && outlineColorScrollControl.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesTextureSampling() && outlineSampleSizeSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (usesTextureSampling() && outlineSampleColorCountSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineGlowIntensitySlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (model.draft().outlineBloomEnabled()) {
                if (outlineBloomRadiusSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
                if (outlineBloomIntensitySlider.mouseClicked(mouseX, mouseY, event.button())) return true;
                if (outlineBloomBlurPassesSlider.mouseClicked(mouseX, mouseY, event.button())) return true;
            }
        }
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = (event.x() - originX) / uiScale;
        double mouseY = (event.y() - originY) / uiScale;
        if (page == Page.RENDER) {
            if (usesPrimaryColor() && outlinePrimaryColorPicker.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (usesSecondaryColor() && outlineSecondaryColorPicker.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineWidthSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineSoftnessSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineThresholdSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineOpacitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineColorScrollSpeedSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineColorScrollControl.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineSampleSizeSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineSampleColorCountSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineGlowIntensitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomRadiusSlider.mouseDragged(mouseX, mouseY, event.button())) return true;
            if (outlineBloomIntensitySlider.mouseDragged(mouseX, mouseY, event.button())) return true;
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
        outlineWidthSlider.stopDragging();
        outlineSoftnessSlider.stopDragging();
        outlineThresholdSlider.stopDragging();
        outlineOpacitySlider.stopDragging();
        outlineColorScrollSpeedSlider.stopDragging();
        outlineColorScrollControl.stopDragging();
        outlineSampleSizeSlider.stopDragging();
        outlineSampleColorCountSlider.stopDragging();
        outlineGlowIntensitySlider.stopDragging();
        outlineBloomRadiusSlider.stopDragging();
        outlineBloomIntensitySlider.stopDragging();
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
        if (ruleDialogOpen) {
            ruleDialogScrollTarget = clampRuleDialogScroll(ruleDialogScrollTarget - (float) verticalAmount * 28.0F);
            return true;
        }
        if (page == Page.RENDER && localMouseX >= sidebarRight && localMouseX < right
                && localMouseY >= top + RIGHT_CONTENT_TOP && localMouseY < bottom - RIGHT_CONTENT_BOTTOM) {
            renderScrollTarget = Math.max(0.0F, Math.min(maxRenderScroll(), renderScrollTarget - (float) verticalAmount * 48.0F));
            return true;
        }
        if (page == Page.PREVIEW && isInPreviewCanvas(localMouseX, localMouseY)) {
            zoomPreview((float) localMouseX, (float) localMouseY, (float) verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (ruleTargetPickerOpen) {
            return handleRuleTargetPickerKey(event);
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
            itemSearch = itemSearch.substring(0, itemSearch.offsetByCodePoints(itemSearch.length(), -1));
            filterItemChoices();
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
        if (ruleTargetPickerOpen) {
            if (event.isAllowedChatCharacter() && ruleTargetSearch.length() < 96) {
                ruleTargetSearch += event.codepointAsString();
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
            itemSearch += event.codepointAsString();
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
                if (usesColorAnimation()) outlineColorScrollControl.render(graphics, font, mouseX, mouseY);
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
        updateAddRuleAnimation(mouseX, mouseY);
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
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.rules.empty"), sidebarRight + 24, top + 82, 0.72F, UiPalette.MUTED_TEXT);
    }

    private void renderRuleDialog(GuiGraphics graphics, int mouseX, int mouseY) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRuleDialogFrame) / 1_000_000_000.0F);
        lastRuleDialogFrame = now;
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
        drawRuleDialogText(graphics, tr("ui.itemglintrelight.rules.create"), x + 18, y + 10, 0.86F, UiPalette.TEXT);
        drawRuleDialogText(graphics, "x", x + width - 22, y + 13, 0.82F, UiPalette.MUTED_TEXT);
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        int contentRowsHeight = 34 + (ruleMatchMode == RuleMatchMode.NBT_MATCH ? nbtMatchModeExpandedHeight() : 0);
        ruleDialogContentHeight = 95 + ruleModeDropdown.expandedHeight() + contentRowsHeight;
        ruleDialogScrollTarget = clampRuleDialogScroll(ruleDialogScrollTarget);
        ruleDialogScroll = clampRuleDialogScroll(ruleDialogScroll);
        int scroll = Math.round(ruleDialogScroll);
        int formY = y + 31 - scroll;
        renderRuleTextField(graphics, x + 18, formY, width - 36, tr("ui.itemglintrelight.rules.name"), ruleName, RuleInputFocus.NAME, false, mouseX, mouseY);
        ruleModeDropdown.setPosition(x + 18, y + 72 - scroll);
        int contentY = y + 126 + ruleModeDropdown.expandedHeight() - scroll;
        int contentViewportY = y + 31;
        int contentViewportBottom = y + height - 42;
        if (ruleMatchMode == RuleMatchMode.WHITELIST) {
            renderRuleTextField(graphics, x + 18, contentY, width - 36, tr("ui.itemglintrelight.rules.item_id"), ruleItemId, RuleInputFocus.ITEM, true, mouseX, mouseY);
        } else if (ruleMatchMode == RuleMatchMode.NBT_MATCH) {
            int inputWidth = (width - 36 - 92) / 2;
            int keyX = x + 18;
            int modeX = keyX + inputWidth + 8;
            int valueX = modeX + 76 + 8;
            renderRuleTextField(graphics, keyX, contentY, inputWidth, tr("ui.itemglintrelight.rules.nbt_key"), ruleNbtPath, RuleInputFocus.NBT_PATH, false, mouseX, mouseY);
            renderNbtMatchModeControl(graphics, modeX, contentY, 76, mouseX, mouseY);
            renderRuleTextField(graphics, valueX, contentY, inputWidth, tr("ui.itemglintrelight.rules.nbt_value"), ruleNbtValue, RuleInputFocus.NBT_VALUE, false, mouseX, mouseY);
        } else {
            renderRuleTextField(graphics, x + 18, contentY, width - 36, tr("ui.itemglintrelight.rules.item_or_tag"), ruleItemId, RuleInputFocus.ITEM, true, mouseX, mouseY);
        }
        ruleModeDropdown.render(graphics, font, mouseX, mouseY, ruleDialogAnimation);
        graphics.disableScissor();
        renderRuleDialogScrollBar(graphics, x, contentViewportY, contentViewportBottom - contentViewportY);
        renderRuleAddButton(graphics, x, y, width, height, mouseX, mouseY);
        graphics.pose().popMatrix();
    }

    private void renderRuleTextField(GuiGraphics graphics, int x, int y, int width, String label, String value, RuleInputFocus focus, boolean selectable, int mouseX, int mouseY) {
        drawRuleDialogText(graphics, label, x, y, 0.68F, UiPalette.MUTED_TEXT);
        int inputWidth = selectable ? width - 62 : width;
        boolean focused = ruleInputFocus == focus;
        boolean hovered = UiMath.contains(x, y + 12, inputWidth, 22, mouseX, mouseY);
        graphics.fill(x, y + 12, x + inputWidth, y + 34, ruleDialogColor(focused || hovered ? UiPalette.BRIGHT_BLUE : UiPalette.DIVIDER));
        graphics.fill(x + 1, y + 13, x + inputWidth - 1, y + 33, ruleDialogColor(UiPalette.SURFACE));
        String visible = value.isEmpty() ? tr("ui.itemglintrelight.rules.optional") : value;
        drawRuleDialogText(graphics, truncate(visible, inputWidth - 18, 0.68F), x + 8, y + 18, 0.68F, value.isEmpty() ? UiPalette.MUTED_TEXT : UiPalette.TEXT);
        if (focused && (System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 8 + SmoothTextRenderer.width(value, 0.68F, UiPalette.TEXT);
            graphics.fill(cursorX, y + 17, cursorX + 1, y + 29, ruleDialogColor(UiPalette.PALE_BLUE));
        }
        if (selectable) {
            int buttonX = x + inputWidth + 6;
            boolean buttonHovered = UiMath.contains(buttonX, y + 12, 56, 22, mouseX, mouseY);
            graphics.fill(buttonX, y + 12, buttonX + 56, y + 34, ruleDialogColor(buttonHovered ? UiPalette.BRIGHT_BLUE : UiPalette.DIVIDER));
            graphics.fill(buttonX + 1, y + 13, buttonX + 55, y + 33, ruleDialogColor(UiPalette.SURFACE_HOVER));
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
        graphics.fill(x, y + 12, x + width, y + 34, ruleDialogColor(hovered ? UiPalette.BRIGHT_BLUE : UiPalette.DIVIDER));
        graphics.fill(x + 1, y + 13, x + width - 1, y + 33, ruleDialogColor(UiPalette.SURFACE));
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
        graphics.fill(buttonX, buttonY, buttonX + 88, buttonY + 24, ruleDialogColor(hovered ? UiPalette.LIGHT_GREEN : UiPalette.BRIGHT_BLUE));
        graphics.fill(buttonX + 1, buttonY + 1, buttonX + 87, buttonY + 23, ruleDialogColor(hovered ? UiPalette.SURFACE_HOVER : UiPalette.SURFACE));
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

    private void updateAddRuleAnimation(int mouseX, int mouseY) {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRuleFrame) / 1_000_000_000.0F);
        lastRuleFrame = now;
        addRuleHover = UiMath.approach(addRuleHover, isAddRuleButton(mouseX, mouseY) ? 1.0F : 0.0F, delta, 13.0F);
    }

    private void openRuleDialog() {
        ruleDialogOpen = true;
        ruleDialogAnimation = 0.0F;
        lastRuleDialogFrame = System.nanoTime();
        ruleDialogScroll = 0.0F;
        ruleDialogScrollTarget = 0.0F;
        ruleInputFocus = RuleInputFocus.NAME;
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
            ruleDialogOpen = false;
            ruleInputFocus = RuleInputFocus.NONE;
            return true;
        }
        int scroll = Math.round(ruleDialogScroll);
        ruleModeDropdown.setPosition(x + 18, y + 72 - scroll);
        boolean modeExpanded = ruleModeDropdown.isExpanded();
        if (ruleModeDropdown.mouseClicked(mouseX, mouseY, button) || modeExpanded) return true;
        if (UiMath.contains(x + 18, y + 43 - scroll, width - 36, 22, mouseX, mouseY)) {
            ruleInputFocus = RuleInputFocus.NAME;
            return true;
        }
        int contentY = y + 126 + ruleModeDropdown.expandedHeight() - scroll;
        if (usesRuleTargetPicker() && UiMath.contains(x + width - 74, contentY + 12, 56, 22, mouseX, mouseY)) {
            openRuleTargetPicker();
            return true;
        }
        if (ruleMatchMode != RuleMatchMode.NBT_MATCH && UiMath.contains(x + 18, contentY + 12, width - 36, 22, mouseX, mouseY)) {
            ruleInputFocus = RuleInputFocus.ITEM;
            return true;
        }
        if (ruleMatchMode == RuleMatchMode.NBT_MATCH) {
            int inputWidth = (width - 36 - 92) / 2;
            int keyX = x + 18;
            int modeX = keyX + inputWidth + 8;
            int valueX = modeX + 76 + 8;
            int optionsY = contentY + 35;
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
                ruleInputFocus = RuleInputFocus.NBT_PATH;
                return true;
            }
            if (UiMath.contains(valueX, contentY + 12, inputWidth, 22, mouseX, mouseY)) {
                ruleInputFocus = RuleInputFocus.NBT_VALUE;
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
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            deleteRuleInputCharacter();
            return true;
        }
        return true;
    }

    private void appendRuleInput(String value) {
        switch (ruleInputFocus) {
            case NAME -> ruleName = appendRuleInput(ruleName, value);
            case ITEM -> ruleItemId = appendRuleInput(ruleItemId, value);
            case NBT_PATH -> ruleNbtPath = appendRuleInput(ruleNbtPath, value);
            case NBT_VALUE -> ruleNbtValue = appendRuleInput(ruleNbtValue, value);
            case NONE -> { }
        }
    }

    private String appendRuleInput(String current, String value) {
        return current.length() >= 128 ? current : current + value;
    }

    private void deleteRuleInputCharacter() {
        switch (ruleInputFocus) {
            case NAME -> ruleName = deleteRuleInputCharacter(ruleName);
            case ITEM -> ruleItemId = deleteRuleInputCharacter(ruleItemId);
            case NBT_PATH -> ruleNbtPath = deleteRuleInputCharacter(ruleNbtPath);
            case NBT_VALUE -> ruleNbtValue = deleteRuleInputCharacter(ruleNbtValue);
            case NONE -> { }
        }
    }

    private String deleteRuleInputCharacter(String value) {
        return value.isEmpty() ? value : value.substring(0, value.offsetByCodePoints(value.length(), -1));
    }

    private int ruleDialogColor(int color) {
        int alpha = Math.round((color >>> 24) * ruleDialogAnimation);
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

    private boolean isPreviewItemLabel(double mouseX, double mouseY) {
        int labelWidth = Math.max(96, Math.round(SmoothTextRenderer.width(previewItem.getHoverName().getString(), 0.88F, UiPalette.BRIGHT_BLUE)));
        return mouseX >= PREVIEW_X + 10 && mouseX < PREVIEW_X + 16 + labelWidth
                && mouseY >= PREVIEW_Y + 8 && mouseY < PREVIEW_Y + 29;
    }

    private void openItemPicker() {
        itemPickerOpen = true;
        itemPickerAnimation = 0.0F;
        itemSearch = "";
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
        drawPickerText(graphics, searchText, x + 20, searchY + 6, 0.62F, searchColor);
        if ((System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 20 + Math.round(SmoothTextRenderer.width(itemSearch, 0.62F, UiPalette.TEXT));
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
        drawRuleTargetPickerText(graphics, searchText, x + 20, searchY + 6, 0.62F, ruleTargetSearch.isEmpty() ? UiPalette.MUTED_TEXT : UiPalette.TEXT);
        if ((System.currentTimeMillis() / 500L & 1L) == 0) {
            int cursorX = x + 20 + Math.round(SmoothTextRenderer.width(ruleTargetSearch, 0.62F, UiPalette.TEXT));
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
            ruleInputFocus = RuleInputFocus.ITEM;
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
            ruleTargetSearch = ruleTargetSearch.substring(0, ruleTargetSearch.offsetByCodePoints(ruleTargetSearch.length(), -1));
            filterRuleTargetChoices();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER && !filteredRuleTargetChoices.isEmpty()) {
            ruleItemId = filteredRuleTargetChoices.get(0).value();
            ruleInputFocus = RuleInputFocus.ITEM;
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
        if (font.width(value) * scale <= maxWidth) return value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) * scale > maxWidth) end--;
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
            outlineColorScrollControl.setPosition(contentX, y); y += outlineColorScrollControl.height() + 10;
        }
        if (usesTextureSampling()) {
            outlineSampleSizeSlider.setPosition(contentX, y); y += 42;
            outlineSampleColorCountSlider.setPosition(contentX, y); y += 42;
        }
        outlineQualityDropdown.setPosition(contentX, y); y += 46 + outlineQualityDropdown.expandedHeight();
        outlineGlowIntensitySlider.setPosition(contentX, y); y += 42;
        outlineBloomToggle.setPosition(contentX, y); y += 38;
        if (model.draft().outlineBloomEnabled()) {
            outlineBloomQualityDropdown.setPosition(contentX, y); y += 46 + outlineBloomQualityDropdown.expandedHeight();
            outlineBloomRadiusSlider.setPosition(contentX, y); y += 42;
            outlineBloomIntensitySlider.setPosition(contentX, y); y += 42;
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
        GREATER_THAN(">"),
        LESS_THAN("<"),
        GREATER_OR_EQUAL(">="),
        LESS_OR_EQUAL("<="),
        CONTAINS("⊃"),
        CONTAINED_BY("⊂");

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
        NBT_VALUE
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
