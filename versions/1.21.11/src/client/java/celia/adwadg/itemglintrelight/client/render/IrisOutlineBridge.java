package celia.adwadg.itemglintrelight.client.render;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class IrisOutlineBridge {
    private static final Method SHADOW_PASS_METHOD = resolveShadowPassMethod();
    private static final Method SHADER_PACK_METHOD = resolveShaderPackMethod();
    private IrisOutlineBridge() { }

    public static boolean isActive() {
        return FabricLoader.getInstance().isModLoaded("iris");
    }

    public static boolean isRenderingShadowPass() {
        if (!isActive() || SHADOW_PASS_METHOD == null) return false;
        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return Boolean.TRUE.equals(SHADOW_PASS_METHOD.invoke(irisApi.getMethod("getInstance").invoke(null)));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static boolean isShaderPackActive() {
        if (!isActive() || SHADER_PACK_METHOD == null) return false;
        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return Boolean.TRUE.equals(SHADER_PACK_METHOD.invoke(irisApi.getMethod("getInstance").invoke(null)));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static Method resolveShadowPassMethod() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            return Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("isRenderingShadowPass");
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Method resolveShaderPackMethod() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            return Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
