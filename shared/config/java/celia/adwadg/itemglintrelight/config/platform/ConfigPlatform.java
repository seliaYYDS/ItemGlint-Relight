package celia.adwadg.itemglintrelight.config.platform;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

/**
 * The version seam of the shared configuration code.
 *
 * <p>1.21.1 names the resource location {@code ResourceLocation} and looks registry entries up with
 * {@code get}; every later version renamed the type to {@code Identifier} and the lookup to
 * {@code getValue}. Those are the only differences the configuration data model has between the
 * supported versions, so they are the only thing a version has to supply itself.
 */
public interface ConfigPlatform {
    /** {@code stack.is(TagKey.create(Registries.ITEM, <id>))}, tolerant of a malformed id. */
    boolean isItemTag(ItemStack stack, String id);

    /** Registry lookup of a data component type, or {@code null} when the id does not resolve. */
    DataComponentType<?> componentType(String id);

    /** {@code ResourceKey} id as a string ({@code location()} in 1.21.1, {@code identifier()} later). */
    String registryKeyId(Object key);

    String registryKeyNamespace(Object key);

    void warn(String message, Object... arguments);

    void info(String message, Object... arguments);
}
