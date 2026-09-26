package celia.adwadg.itemglintrelight.client.render;

import net.minecraft.world.item.ItemStack;

/**
 * Value key for an item stack: item id, count and the complete component set.
 *
 * <p>{@link ItemStack#toString()} is only {@code count + " " + registryName} and therefore loses
 * every component. Cache keys built from it made two stacks that share an item id and count but
 * differ in components (potion contents, dyed colour, charged crossbow, custom_model_data, bundle
 * contents, ...) collide, which produced outlines in the wrong colour and, for the GUI mask, in
 * the wrong shape.
 *
 * <p>The stack is snapshotted on construction so the key stays stable while it sits in a map, and
 * identity follows the engine's own rules: {@link ItemStack#hashItemAndComponents} for hashing and
 * {@link ItemStack#isSameItemSameComponents} for equality.
 */
public final class ItemStackKey {
    private final ItemStack stack;
    private final int hash;

    private ItemStackKey(ItemStack snapshot) {
        this.stack = snapshot;
        this.hash = 31 * ItemStack.hashItemAndComponents(snapshot) + snapshot.getCount();
    }

    public static ItemStackKey of(ItemStack stack) {
        return new ItemStackKey(stack.copy());
    }

    /** Registry name only; for diagnostic messages, never for caching. */
    public String describe() {
        return stack.getItem().toString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemStackKey key
                && stack.getCount() == key.stack.getCount()
                && ItemStack.isSameItemSameComponents(stack, key.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return describe() + " x" + stack.getCount();
    }
}