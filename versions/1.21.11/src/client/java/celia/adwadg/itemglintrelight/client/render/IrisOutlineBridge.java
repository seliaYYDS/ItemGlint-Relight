package celia.adwadg.itemglintrelight.client.render;

import net.fabricmc.loader.api.FabricLoader;

public final class IrisOutlineBridge {
    private IrisOutlineBridge() { }

    public static boolean isActive() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }
}
