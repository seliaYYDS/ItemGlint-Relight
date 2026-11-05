package celia.adwadg.itemglintrelight.config.platform;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

/**
 * 1.21.1 variant of the version seam: the resource location is named {@code ResourceLocation} and
 * the registry exposes {@code get} instead of {@code getValue}.
 */
public final class ResourceLocationConfigPlatform implements ConfigPlatform {
    @Override
    public boolean isItemTag(ItemStack stack, String id) {
        return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
    }

    @Override
    public DataComponentType<?> componentType(String id) {
        return BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(id));
    }

    @Override
    public String registryKeyId(Object key) {
        return ((net.minecraft.resources.ResourceKey<?>) key).location().toString();
    }

    @Override
    public String registryKeyNamespace(Object key) {
        return ((net.minecraft.resources.ResourceKey<?>) key).location().getNamespace();
    }

    @Override
    public void warn(String message, Object... arguments) {
        ItemGlintRelight.LOGGER.warn(message, arguments);
    }

    @Override
    public void info(String message, Object... arguments) {
        ItemGlintRelight.LOGGER.info(message, arguments);
    }
}
