package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Punchy compatibility hooks.
 * <p>Punchy replaces the vanilla first-person pass with its own arm renderer, and it decides on its
 * own which stack is actually drawn: while an equip or use animation runs the off hand can show a
 * different stack than {@code player.getOffhandItem()}. The outline has to follow the stack that is
 * really on screen, otherwise the wrong item would be outlined.
 * <p>Every Punchy class is optional, so the hook is reflective and reports "not available" instead
 * of failing when the mod is missing or has changed shape. The rendering side lives in the
 * version-specific render package on purpose: only loader/reflection glue may sit in common.
 */
public final class HeldItemOutlinePunchyCompat {
    private static final String PUNCHY_ANIMATION_MANAGER_CLASS = "punchy.client.animation.PunchyAnimationManager";

    private static final boolean PUNCHY_LOADED = FabricLoader.getInstance().isModLoaded("punchy");
    private static final Method RESOLVE_RENDER_STACK;

    static {
        Method resolveRenderStack = null;
        if (PUNCHY_LOADED) {
            try {
                Class<?> animationManager = Class.forName(PUNCHY_ANIMATION_MANAGER_CLASS);
                resolveRenderStack = animationManager.getMethod("resolveRenderStack",
                        Player.class,
                        HumanoidArm.class,
                        ItemStack.class);
                ItemGlintRelight.LOGGER.info("[PunchyCompat] punchy detected, compatibility hooks resolved.");
            } catch (ReflectiveOperationException exception) {
                ItemGlintRelight.LOGGER.warn(
                        "Unable to resolve the Punchy render stack hook, falling back to the live hand stacks.", exception);
            }
        } else {
            ItemGlintRelight.LOGGER.info("[PunchyCompat] punchy not detected.");
        }
        RESOLVE_RENDER_STACK = resolveRenderStack;
    }

    private HeldItemOutlinePunchyCompat() { }

    public static boolean isLoaded() {
        return PUNCHY_LOADED;
    }

    public static boolean hooksAvailable() {
        return RESOLVE_RENDER_STACK != null;
    }

    /**
     * Resolves the stack Punchy is about to draw for this hand. Falls back to the live hand stack
     * when Punchy is absent or its hook changed shape, which is exactly the vanilla behaviour.
     */
    public static ItemStack resolveRenderedHandStack(LocalPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return ItemStack.EMPTY;
        }

        ItemStack liveStack = hand == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
        if (!PUNCHY_LOADED || RESOLVE_RENDER_STACK == null) {
            return liveStack;
        }

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : (player.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        try {
            Object resolved = RESOLVE_RENDER_STACK.invoke(null, player, arm, liveStack);
            if (resolved instanceof ItemStack itemStack) {
                return itemStack;
            }
        } catch (ReflectiveOperationException exception) {
            ItemGlintRelight.LOGGER.debug("Failed to invoke the Punchy render stack hook for hand {}", hand, exception);
        }
        return liveStack;
    }
}
