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
import celia.adwadg.itemglintrelight.config.RenderQuality;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.ui.ItemGlintRelightConfigScreenModel;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ItemGlintRelightConfigScreen extends Screen {
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
        outlineToggle = new UiToggle(contentX, top + 92, contentWidth, tr("ui.itemglintrelight.outline.enabled"),
                () -> model.draft().outlineEnabled(), value -> model.draft().setOutlineEnabled(value));
        mainHandToggle = new UiToggle(contentX, top + 126, contentWidth, tr("ui.itemglintrelight.outline.main_hand"),
                () -> model.draft().outlineMainHand(), value -> model.draft().setOutlineMainHand(value));
        offHandToggle = new UiToggle(contentX, top + 152, contentWidth, tr("ui.itemglintrelight.outline.off_hand"),
                () -> model.draft().outlineOffHand(), value -> model.draft().setOutlineOffHand(value));
        thirdPersonToggle = new UiToggle(contentX, top + 178, contentWidth, tr("ui.itemglintrelight.outline.third_person"),
                () -> model.draft().outlineThirdPerson(), value -> model.draft().setOutlineThirdPerson(value));
        guiItemsToggle = new UiToggle(contentX, top + 204, contentWidth, tr("ui.itemglintrelight.outline.gui_items"),
                () -> model.draft().outlineGuiItems(), value -> model.draft().setOutlineGuiItems(value));
        outlineWidthSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.outline_width"), 0.25D, 8.0D, 0.05D,
                () -> model.draft().outlineWidth(), value -> model.draft().setOutlineWidth(value.floatValue()));
        outlineSoftnessSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.softness"), 0.0D, 1.0D, 0.01D,
                () -> model.draft().outlineSoftness(), value -> model.draft().setOutlineSoftness(value.floatValue()));
        outlineThresholdSlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.alpha_threshold"), 0.0D, 0.95D, 0.01D,
                () -> model.draft().outlineAlphaThreshold(), value -> model.draft().setOutlineAlphaThreshold(value.floatValue()));
        outlineOpacitySlider = new UiSlider(contentX, top + 80, contentWidth, tr("ui.itemglintrelight.render.outline_opacity"), 0.0D, 100.0D, 1.0D,
                () -> model.draft().outlineOpacity() * 100.0D, value -> model.draft().setOutlineOpacity(value.floatValue() / 100.0F));
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
        navigationFill[page.ordinal()] = 1.0F;
        lastNavigationFrame = System.nanoTime();
        lastPageTransitionFrame = System.nanoTime();
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
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = (event.x() - originX) / uiScale;
        double mouseY = (event.y() - originY) / uiScale;
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
        if (page == Page.RENDER) {
            if (outlineColorModeDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
            if (outlineQualityDropdown.mouseClicked(mouseX, mouseY, event.button())) return true;
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
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
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
        if (page == Page.RENDER && localMouseX >= sidebarRight && localMouseX < right
                && localMouseY >= top + 72 && localMouseY < bottom - 64) {
            renderScrollTarget = Math.max(0.0F, Math.min(maxRenderScroll(), renderScrollTarget - (float) verticalAmount * 48.0F));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
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
        graphics.enableScissor(sidebarRight + 12, top + 72, right - 12, bottom - 64);
        graphics.pose().pushMatrix();
        graphics.pose().translate(Math.round((1.0F - pageTransition) * 24.0F), 0.0F);
        if (page != Page.GENERAL) {
            if (page == Page.RENDER) {
                layoutRenderControls();
                outlineWidthSlider.render(graphics, font, mouseX, mouseY);
                outlineSoftnessSlider.render(graphics, font, mouseX, mouseY);
                outlineThresholdSlider.render(graphics, font, mouseX, mouseY);
                outlineOpacitySlider.render(graphics, font, mouseX, mouseY);
                outlineColorModeDropdown.render(graphics, font, mouseX, mouseY);
                if (usesPrimaryColor()) outlinePrimaryColorPicker.render(graphics, font, mouseX, mouseY);
                if (usesSecondaryColor()) outlineSecondaryColorPicker.render(graphics, font, mouseX, mouseY);
                if (usesColorAnimation()) outlineColorScrollSpeedSlider.render(graphics, font, mouseX, mouseY);
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

    private void layoutRenderControls() {
        long now = System.nanoTime();
        float delta = Math.min(0.05F, (now - lastRenderScrollFrame) / 1_000_000_000.0F);
        lastRenderScrollFrame = now;
        renderScroll = UiMath.approach(renderScroll, renderScrollTarget, delta, 16.0F);
        int contentX = sidebarRight + 24;
        int contentWidth = right - contentX - 24;
        int startY = top + 80;
        int scroll = Math.round(renderScroll);
        int y = startY - scroll;
        outlineWidthSlider.setPosition(contentX, y); y += 42;
        outlineSoftnessSlider.setPosition(contentX, y); y += 42;
        outlineThresholdSlider.setPosition(contentX, y); y += 42;
        outlineOpacitySlider.setPosition(contentX, y); y += 42;
        outlineColorModeDropdown.setPosition(contentX, y); y += 46 + outlineColorModeDropdown.expandedHeight();
        if (usesPrimaryColor()) {
            outlinePrimaryColorPicker.setPosition(contentX, y); y += outlinePrimaryColorPicker.height() + 16;
        }
        if (usesSecondaryColor()) {
            outlineSecondaryColorPicker.setPosition(contentX, y); y += outlineSecondaryColorPicker.height() + 16;
        }
        if (usesColorAnimation()) {
            outlineColorScrollSpeedSlider.setPosition(contentX, y); y += 42;
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
        y += 24;
        renderContentHeight = y + scroll - startY;
        int maximum = maxRenderScroll();
        renderScrollTarget = Math.max(0.0F, Math.min(renderScrollTarget, maximum));
        renderScroll = Math.max(0.0F, Math.min(renderScroll, maximum));
    }

    private void renderScrollBar(GuiGraphics graphics) {
        int viewportHeight = bottom - 64 - (top + 72);
        int maximum = maxRenderScroll();
        if (maximum == 0) {
            return;
        }
        int trackX = right - 8;
        int trackY = top + 76;
        int trackHeight = viewportHeight - 8;
        int thumbHeight = Math.max(18, Math.round(trackHeight * viewportHeight / (float) renderContentHeight));
        int thumbY = trackY + Math.round((trackHeight - thumbHeight) * renderScroll / maximum);
        graphics.fill(trackX, trackY, trackX + 1, trackY + trackHeight, UiPalette.DIVIDER);
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, UiPalette.PALE_BLUE);
    }

    private int maxRenderScroll() {
        return Math.max(0, renderContentHeight - (bottom - 64 - (top + 72)));
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
