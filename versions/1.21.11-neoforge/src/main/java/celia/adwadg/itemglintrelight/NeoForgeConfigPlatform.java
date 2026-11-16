package celia.adwadg.itemglintrelight;

import celia.adwadg.itemglintrelight.config.platform.ConfigPlatform;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

public final class NeoForgeConfigPlatform implements ConfigPlatform {
    @Override
    public boolean isItemTag(ItemStack stack, String id) {
        return stack.is(TagKey.create(Registries.ITEM, Identifier.parse(id)));
    }

    @Override
    public DataComponentType<?> componentType(String id) {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(id));
    }

    @Override
    public String registryKeyId(Object key) {
        if (key instanceof ResourceKey<?> resourceKey) {
            return resourceKey.identifier().toString();
        }
        if (key instanceof TagKey<?> tagKey) {
            return tagKey.location().toString();
        }
        return String.valueOf(key);
    }

    @Override
    public String registryKeyNamespace(Object key) {
        if (key instanceof ResourceKey<?> resourceKey) {
            return resourceKey.identifier().getNamespace();
        }
        if (key instanceof TagKey<?> tagKey) {
            return tagKey.location().getNamespace();
        }
        return "";
    }

    @Override
    public void warn(String message, Object... arguments) {
        ItemGlintRelight.LOGGER.warn(message, arguments);
    }

    @Override
    public void info(String message, Object... arguments) {
        ItemGlintRelight.LOGGER.info(message, arguments);
    }

    @Override
    public String modDisplayName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName()).orElse(modId);
    }

    @Override
    public List<String> modIds() {
        return ModList.get().getMods().stream().map(IModInfo::getModId).toList();
    }
}