package celia.adwadg.itemglintrelight.client;

import celia.adwadg.itemglintrelight.client.ui.ConfigUiBackground;
import celia.adwadg.itemglintrelight.client.ui.SmoothTextRenderer;
import celia.adwadg.itemglintrelight.client.ui.UiButton;
import celia.adwadg.itemglintrelight.client.ui.UiDropdown;
import celia.adwadg.itemglintrelight.client.ui.UiPalette;
import celia.adwadg.itemglintrelight.client.ui.UiToggle;
import celia.adwadg.itemglintrelight.config.GlintColorMode;
import celia.adwadg.itemglintrelight.config.ui.ItemGlintRelightConfigScreenModel;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ItemGlintRelightConfigScreen extends Screen {
    private final Screen parent;
    private final ItemGlintRelightConfigScreenModel model = new ItemGlintRelightConfigScreenModel();
    private UiToggle enabledToggle;
    private UiToggle heldItemsToggle;
    private UiToggle guiItemsToggle;
    private UiToggle thirdPersonToggle;
    private UiToggle bloomToggle;
    private UiToggle ruleDelayToggle;
    private UiDropdown colorModeDropdown;
    private UiButton resetButton;
    private UiButton saveButton;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int sidebarRight;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private int controlLeft;
    private int scrollOffset;
    private int maxScroll;
    private float uiScale;
    private float originX;
    private float originY;
    private ConfigPage page = ConfigPage.GENERAL;

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
        sidebarRight = 168;
        contentLeft = sidebarRight + 24;
        contentRight = right - 24;
        contentTop = top + 86;
        contentBottom = bottom - 62;
        controlLeft = contentRight - 170;
        scrollOffset = 0;
        uiScale = Math.min(1.0F, Math.min((this.width - 28.0F) / (right - left + 4.0F), (this.height - 28.0F) / (bottom - top + 4.0F)));
        uiScale = Math.max(0.35F, uiScale);
        originX = (this.width - (right - left) * uiScale) / 2.0F;
        originY = (this.height - (bottom - top) * uiScale) / 2.0F;
        int contentWidth = contentRight - contentLeft;

        enabledToggle = new UiToggle(contentLeft, contentTop, contentWidth, tr("ui.itemglintrelight.enabled"), () -> model.draft().enabled(),
                value -> model.draft().setEnabled(value));
        heldItemsToggle = new UiToggle(contentLeft, contentTop, contentWidth, tr("ui.itemglintrelight.held_items"), () -> model.draft().renderHeldItems(),
                value -> model.draft().setRenderHeldItems(value));
        guiItemsToggle = new UiToggle(contentLeft, contentTop, contentWidth, tr("ui.itemglintrelight.gui_items"), () -> model.draft().renderGuiItems(),
                value -> model.draft().setRenderGuiItems(value));
        thirdPersonToggle = new UiToggle(contentLeft, contentTop, contentWidth, tr("ui.itemglintrelight.third_person"), () -> model.draft().renderThirdPerson(),
                value -> model.draft().setRenderThirdPerson(value));
        bloomToggle = new UiToggle(contentLeft, contentTop, contentWidth, tr("ui.itemglintrelight.bloom"), () -> model.draft().bloomEnabled(),
                value -> model.draft().setBloomEnabled(value));
        ruleDelayToggle = new UiToggle(contentLeft, contentTop, contentWidth, tr("ui.itemglintrelight.rule_delay"), () -> model.draft().ruleSwitchDelayEnabled(),
                value -> model.draft().setRuleSwitchDelayEnabled(value));
        colorModeDropdown = new UiDropdown(controlLeft, contentTop, 170, "", colorModeLabels(),
                model.draft().colorMode().ordinal(), index -> model.draft().setColorMode(GlintColorMode.values()[index]));
        resetButton = new UiButton(right - 196, bottom - 40, 84, 22, tr("ui.itemglintrelight.reset"), () -> {
            model.resetToDefaults();
            init();
        });
        saveButton = new UiButton(right - 104, bottom - 40, 80, 22, tr("ui.itemglintrelight.save"), () -> {
            model.save();
            onClose();
        });
        layoutContent();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ConfigUiBackground.renderBackdrop(graphics, width, height);
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(uiScale, uiScale);
        ConfigUiBackground.renderPanel(graphics, left, top, right, bottom, sidebarRight);
        renderStaticText(graphics);

        int logicalMouseX = Math.round((mouseX - originX) / uiScale);
        int logicalMouseY = Math.round((mouseY - originY) / uiScale);
        layoutContent();
        graphics.enableScissor(contentLeft, contentTop, contentRight, contentBottom);
        renderPage(graphics, logicalMouseX, logicalMouseY);
        graphics.disableScissor();

        resetButton.render(graphics, font, logicalMouseX, logicalMouseY);
        saveButton.render(graphics, font, logicalMouseX, logicalMouseY);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.save_hint"), contentLeft, bottom - 35, 0.66F, UiPalette.MUTED_TEXT);
        renderScrollBar(graphics);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = (event.x() - originX) / uiScale;
        double mouseY = (event.y() - originY) / uiScale;
        ConfigPage selectedPage = pageAt(mouseX, mouseY);
        if (selectedPage != null) {
            page = selectedPage;
            scrollOffset = 0;
            return true;
        }
        layoutContent();
        if (page == ConfigPage.COLOURS && colorModeDropdown.mouseClicked(mouseX, mouseY, event.button())) {
            return true;
        }
        if (activeToggleClicked(mouseX, mouseY, event.button())
                || resetButton.mouseClicked(mouseX, mouseY, event.button())
                || saveButton.mouseClicked(mouseX, mouseY, event.button())) {
            return true;
        }
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double logicalMouseX = (mouseX - originX) / uiScale;
        double logicalMouseY = (mouseY - originY) / uiScale;
        if (logicalMouseX < contentLeft || logicalMouseX >= contentRight || logicalMouseY < contentTop || logicalMouseY >= contentBottom) {
            return false;
        }
        layoutContent();
        int nextOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.signum(verticalAmount) * 18));
        if (nextOffset == scrollOffset) {
            return false;
        }
        scrollOffset = nextOffset;
        return true;
    }

    @Override
    public void onClose() {
        SmoothTextRenderer.clear();
        this.minecraft.setScreen(parent);
    }

    private void renderStaticText(GuiGraphics graphics) {
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.brand"), left + 26, top + 18, 0.92F, UiPalette.TEXT);
        SmoothTextRenderer.drawGradient(graphics, font, tr("ui.itemglintrelight.relight"), left + 26, top + 38, 0.60F);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.nav.general"), left + 30, top + 88, 0.76F, page == ConfigPage.GENERAL ? UiPalette.BRIGHT_BLUE : UiPalette.MUTED_TEXT);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.nav.colours"), left + 30, top + 118, 0.76F, page == ConfigPage.COLOURS ? UiPalette.BRIGHT_BLUE : UiPalette.MUTED_TEXT);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.nav.render"), left + 30, top + 148, 0.76F, page == ConfigPage.RENDER ? UiPalette.BRIGHT_BLUE : UiPalette.MUTED_TEXT);
        SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.nav.rules"), left + 30, top + 178, 0.76F, page == ConfigPage.RULES ? UiPalette.BRIGHT_BLUE : UiPalette.MUTED_TEXT);
        SmoothTextRenderer.draw(graphics, font, tr(page.titleKey), contentLeft, top + 27, 0.96F, UiPalette.TEXT);
        SmoothTextRenderer.draw(graphics, font, tr(page.descriptionKey), contentLeft, top + 44, 0.64F, UiPalette.MUTED_TEXT);
    }

    private void layoutContent() {
        int baseY = contentTop - scrollOffset;
        int dropdownY = baseY + 38;
        int flowingHeight = colorModeDropdown == null ? 0 : colorModeDropdown.expandedHeight();
        enabledToggle.setPosition(contentLeft, baseY);
        colorModeDropdown.setPosition(controlLeft, dropdownY);
        heldItemsToggle.setPosition(contentLeft, dropdownY + 48 + flowingHeight);
        guiItemsToggle.setPosition(contentLeft, dropdownY + 80 + flowingHeight);
        thirdPersonToggle.setPosition(contentLeft, baseY + 32);
        bloomToggle.setPosition(contentLeft, baseY + 64);
        ruleDelayToggle.setPosition(contentLeft, baseY);
        int contentEnd = switch (page) {
            case COLOURS -> contentTop + 136 + flowingHeight;
            case RENDER -> contentTop + 82;
            default -> contentTop + 24;
        };
        maxScroll = Math.max(0, contentEnd - contentBottom);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int colorModeY() {
        return contentTop - scrollOffset + 38;
    }

    private void renderPage(GuiGraphics graphics, int mouseX, int mouseY) {
        switch (page) {
            case GENERAL -> enabledToggle.render(graphics, font, mouseX, mouseY);
            case COLOURS -> {
                SmoothTextRenderer.draw(graphics, font, tr("ui.itemglintrelight.colour_mode"), contentLeft, colorModeY() + 18, 0.74F, UiPalette.MUTED_TEXT);
                colorModeDropdown.render(graphics, font, mouseX, mouseY);
            }
            case RENDER -> {
                heldItemsToggle.render(graphics, font, mouseX, mouseY);
                guiItemsToggle.render(graphics, font, mouseX, mouseY);
                thirdPersonToggle.render(graphics, font, mouseX, mouseY);
                bloomToggle.render(graphics, font, mouseX, mouseY);
            }
            case RULES -> ruleDelayToggle.render(graphics, font, mouseX, mouseY);
        }
        layoutContent();
    }

    private boolean activeToggleClicked(double mouseX, double mouseY, int button) {
        return switch (page) {
            case GENERAL -> enabledToggle.mouseClicked(mouseX, mouseY, button);
            case COLOURS -> false;
            case RENDER -> heldItemsToggle.mouseClicked(mouseX, mouseY, button)
                    || guiItemsToggle.mouseClicked(mouseX, mouseY, button)
                    || thirdPersonToggle.mouseClicked(mouseX, mouseY, button)
                    || bloomToggle.mouseClicked(mouseX, mouseY, button);
            case RULES -> ruleDelayToggle.mouseClicked(mouseX, mouseY, button);
        };
    }

    private ConfigPage pageAt(double mouseX, double mouseY) {
        if (mouseX < left + 20 || mouseX >= sidebarRight - 12) {
            return null;
        }
        if (mouseY >= top + 78 && mouseY < top + 108) return ConfigPage.GENERAL;
        if (mouseY >= top + 108 && mouseY < top + 138) return ConfigPage.COLOURS;
        if (mouseY >= top + 138 && mouseY < top + 168) return ConfigPage.RENDER;
        if (mouseY >= top + 168 && mouseY < top + 198) return ConfigPage.RULES;
        return null;
    }

    private void renderScrollBar(GuiGraphics graphics) {
        if (maxScroll <= 0) {
            return;
        }
        int viewportHeight = contentBottom - contentTop;
        int thumbHeight = Math.max(22, Math.round(viewportHeight * viewportHeight / (float) (viewportHeight + maxScroll)));
        int thumbY = contentTop + Math.round((viewportHeight - thumbHeight) * (scrollOffset / (float) maxScroll));
        int trackX = right - 12;
        graphics.fill(trackX, contentTop, trackX + 1, contentBottom, UiPalette.DIVIDER);
        graphics.fill(trackX - 1, thumbY, trackX + 2, thumbY + thumbHeight, UiPalette.BRIGHT_BLUE);
    }

    private static List<String> colorModeLabels() {
        return Arrays.stream(GlintColorMode.values()).map(mode -> tr("option.itemglintrelight.color_mode." + mode.name().toLowerCase())).toList();
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private enum ConfigPage {
        GENERAL("ui.itemglintrelight.general_settings", "ui.itemglintrelight.description"),
        COLOURS("ui.itemglintrelight.colours_settings", "ui.itemglintrelight.colours_description"),
        RENDER("ui.itemglintrelight.render_settings", "ui.itemglintrelight.render_description"),
        RULES("ui.itemglintrelight.rules_settings", "ui.itemglintrelight.rules_description");

        private final String titleKey;
        private final String descriptionKey;

        ConfigPage(String titleKey, String descriptionKey) {
            this.titleKey = titleKey;
            this.descriptionKey = descriptionKey;
        }
    }
}
