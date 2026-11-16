package celia.adwadg.itemglintrelight.config.platform;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

/** Default platform for every version whose resource location is named {@code Identifier}. */
public final class IdentifierConfigPlatform implements ConfigPlatform {
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
        if (key instanceof net.minecraft.resources.ResourceKey<?> resourceKey) {
            return resourceKey.identifier().toString();
        }
        if (key instanceof net.minecraft.tags.TagKey<?> tagKey) {
            return tagKey.location().toString();
        }
        return String.valueOf(key);
    }

    @Override
    public String registryKeyNamespace(Object key) {
        if (key instanceof net.minecraft.resources.ResourceKey<?> resourceKey) {
            return resourceKey.identifier().getNamespace();
        }
        if (key instanceof net.minecraft.tags.TagKey<?> tagKey) {
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
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getName()).orElse(modId);
    }

    @Override
    public List<String> modIds() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(container -> container.getMetadata().getId()).toList();
    }
}
