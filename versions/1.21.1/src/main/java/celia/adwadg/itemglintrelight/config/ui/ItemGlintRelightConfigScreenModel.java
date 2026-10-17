package celia.adwadg.itemglintrelight.config.ui;

import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;

public final class ItemGlintRelightConfigScreenModel {
    private final ItemGlintRelightConfig draft;

    public ItemGlintRelightConfigScreenModel() {
        draft = ItemGlintRelightConfigManager.get().copy();
    }

    public ItemGlintRelightConfig draft() {
        return draft;
    }

    public void resetToDefaults() {
        draft.copyFrom(new ItemGlintRelightConfig());
    }

    public void save() {
        ItemGlintRelightConfigManager.get().copyFrom(draft);
        ItemGlintRelightConfigManager.save();
    }
}
