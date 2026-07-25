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
        mouseGlow = new UiMouseGlow();
        starParticles = new UiTrailStarParticles();
        rebuildItemChoices();
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
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = (event.x() - originX) / uiScale;
        double mouseY = (event.y() - originY) / uiScale;
        if (itemPickerOpen) {
            return handleItemPickerClick(mouseX, mouseY, event.button());
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

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        renderPreviewGrid(graphics);
        String itemName = previewItem.getHoverName().getString();
        boolean hovered = isPreviewItemLabel(mouseX, mouseY);
        previewItemNameHover = UiMath.approach(previewItemNameHover, hovered ? 1.0F : 0.0F, previewDeltaSeconds(), 12.0F);
        int itemNameColor = UiMath.mix(UiPalette.BRIGHT_BLUE, UiPalette.LIGHT_GREEN, previewItemNameHover);
        SmoothTextRenderer.draw(graphics, font, itemName, PREVIEW_X + 14, PREVIEW_Y + 12, 0.88F, itemNameColor);
        int itemNameWidth = SmoothTextRenderer.width(itemName, 0.88F, itemNameColor);
        graphics.fill(PREVIEW_X + 14, PREVIEW_Y + 29, PREVIEW_X + 14 + Math.round(itemNameWidth * previewItemNameHover), PREVIEW_Y + 30, itemNameColor);
        SmoothTextRenderer.draw(graphics, font, "点击更换物品", PREVIEW_X + 14, PREVIEW_Y + 31, 0.56F, UiPalette.MUTED_TEXT);

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
        drawPickerText(graphics, "选择物品", x + 12, y + 10, 0.76F, UiPalette.TEXT);
        String count = filteredItemChoices.size() + " 项";
        int countX = Math.max(x + 12, rightEdge - 12 - SmoothTextRenderer.width(count, 0.58F, UiPalette.MUTED_TEXT));
        drawPickerText(graphics, count, countX, y + 12, 0.58F, UiPalette.MUTED_TEXT);

        int searchY = y + 31;
        graphics.fill(x + 12, searchY, rightEdge - 12, searchY + 22, pickerColor(UiPalette.DIVIDER));
        graphics.fill(x + 13, searchY + 1, rightEdge - 13, searchY + 21, pickerColor(UiPalette.SURFACE));
        String searchText = itemSearch.isEmpty() ? "搜索名称、ID 或 @模组名称" : itemSearch;
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

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private record ItemChoice(ItemStack stack, String name, String id, String namespace, String modName) { }

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
