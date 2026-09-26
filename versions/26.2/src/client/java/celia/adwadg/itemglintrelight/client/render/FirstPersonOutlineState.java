package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks the first-person hand render window so the held item can be submitted with a solid
 * outline colour.
 *
 * <p>26.2 note: this is the simple first version. Instead of the custom mask / composite / bloom
 * pipeline (tracked as P1 in {@code docs/26.2-port-todo.md}) it reuses vanilla's outline pass by
 * handing a non-zero {@code outlineColor} to {@code ItemStackRenderState#submit}. That produces a
 * flat single-colour outline and no bloom at all.
 */
public final class FirstPersonOutlineState {
    private static int depth;
    private static InteractionHand hand;
    private static ItemStack stack = ItemStack.EMPTY;

    private FirstPersonOutlineState() {
    }

    public static void enter(InteractionHand renderedHand, ItemStack renderedStack) {
        depth++;
        hand = renderedHand;
        stack = renderedStack;
    }

    public static void exit() {
        if (depth > 0 && --depth == 0) {
            hand = null;
            stack = ItemStack.EMPTY;
        }
    }

    /**
     * @return the ARGB outline colour to submit, or {@code 0} when the held item should not be
     *         outlined at all.
     */
    public static int outlineColor() {
        if (depth == 0 || hand == null || stack.isEmpty()) {
            return 0;
        }
        ItemGlintRelightConfig config = DisplayRuleManager.resolve(stack, ItemGlintRelightConfigManager.get());
        if (!config.outlineEnabled()) {
            return 0;
        }
        boolean enabled = hand == InteractionHand.MAIN_HAND ? config.outlineMainHand() : config.outlineOffHand();
        if (!enabled) {
            return 0;
        }
        int color = config.outlinePrimaryColor();
        return color == 0 ? 0xFFFFFFFF : color;
    }
}
