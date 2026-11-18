package celia.adwadg.itemglintrelight.mixin;

import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates the optional third-party bridges, mirroring the Fabric-side plugin so both loaders apply the
 * same mixins in the same order. A bridge targets a class that only exists while its mod is
 * installed, so it is skipped entirely instead of being resolved and reported as missing.
 */
public final class ItemGlintRelightMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("itemglintrelight");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("PunchyArmRendererMixin")) {
            return punchyLoaded();
        }
        return true;
    }

    /**
     * The mod list is normally available by the time a mixin config is applied. If it is not, the
     * bridge is kept and the {@code @Pseudo} annotation skips it on its own when the target class is
     * absent, which is the same net result.
     */
    private static boolean punchyLoaded() {
        try {
            ModList modList = ModList.get();
            return modList == null || modList.isLoaded("punchy");
        } catch (Throwable throwable) {
            LOGGER.debug("Unable to query the mod list for the Punchy bridge", throwable);
            return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}