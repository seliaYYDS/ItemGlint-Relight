package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;

/** Optional Iris hooks. Reflection keeps Iris out of the normal runtime path. */
public final class HeldItemOutlineCompat {
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static final Method IRIS_INSTANCE;
    private static final Method SHADER_PACK_IN_USE;
    private static final Method SHADOW_PASS;
    private static final Method IRIS_PACK_IN_USE_QUICK;
    private static String apiResolutionFailure = "none";
    private static String pipelineResolutionFailure = "none";
    private static String apiInvocationFailure = "none";
    private static String pipelineInvocationFailure = "none";
    private static boolean diagnosticLogged;

    static {
        Method instance = null;
        Method shaderPackInUse = null;
        Method shadowPass = null;
        Method packInUseQuick = null;
        if (IRIS_LOADED) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                instance = api.getMethod("getInstance");
                shaderPackInUse = api.getMethod("isShaderPackInUse");
                shadowPass = api.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException exception) {
                apiResolutionFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                ItemGlintRelight.LOGGER.warn("Unable to resolve Iris API outline compatibility hooks", exception);
            }
            try {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                packInUseQuick = iris.getMethod("isPackInUseQuick");
            } catch (ReflectiveOperationException exception) {
                pipelineResolutionFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                ItemGlintRelight.LOGGER.warn("Unable to resolve Iris pipeline outline compatibility hook", exception);
            }
        }
        IRIS_INSTANCE = instance;
        SHADER_PACK_IN_USE = shaderPackInUse;
        SHADOW_PASS = shadowPass;
        IRIS_PACK_IN_USE_QUICK = packInUseQuick;
    }

    private HeldItemOutlineCompat() { }

    /**
     * Iris owns the render-level tail even when no pack is active. Third-person capture must be
     * consumed after that tail whenever Iris is present, otherwise BufferSource.endBatch can run
     * while Iris still owns the frame depth.
     */
    public static boolean isIrisLoaded() {
        return IRIS_LOADED;
    }

    public static boolean isIrisShaderPackRendering() {
        // Iris itself uses isPackInUseQuick() for its GameRenderer hooks. Prefer the same
        // pipeline check here; on some Iris builds the public API can be observed before its
        // pipeline is attached, which incorrectly sends the third-person composite through
        // the vanilla BufferSource path.
        return (invokeApi(SHADER_PACK_IN_USE) || invokeStatic(IRIS_PACK_IN_USE_QUICK))
                && !invokeApi(SHADOW_PASS);
    }

    /** Returns Iris' depthtex2/no-hand depth texture, if its active pipeline exposes it. */
    public static Object getIrisNoHandDepthTexture() {
        if (!IRIS_LOADED || !isIrisShaderPackRendering()) return null;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object manager = iris.getMethod("getPipelineManager").invoke(null);
            Object pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            if (pipeline == null) return null;
            Field targetsField = null;
            for (Class<?> type = pipeline.getClass(); type != null && targetsField == null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getType().getName().equals("net.irisshaders.iris.targets.RenderTargets")) {
                        targetsField = field;
                        break;
                    }
                }
            }
            if (targetsField == null) return null;
            targetsField.trySetAccessible();
            Object targets = targetsField.get(pipeline);
            return targets == null ? null : targets.getClass().getMethod("getDepthTextureNoHand").invoke(targets);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /** Logs the exact optional Iris state once, without changing any render state. */
    public static void logDiagnosticStateOnce() {
        if (diagnosticLogged) return;
        diagnosticLogged = true;
        boolean apiPack = invokeApi(SHADER_PACK_IN_USE);
        boolean quickPack = invokeStatic(IRIS_PACK_IN_USE_QUICK);
        boolean shadowPass = invokeApi(SHADOW_PASS);
        ItemGlintRelight.LOGGER.info(
                "[IrisThirdPersonRoute] irisLoaded={} apiInstance={} apiPackMethod={} shadowMethod={} quickPackMethod={} "
                        + "apiPack={} quickPack={} shadowPass={} shaderFrame={} apiResolve={} pipelineResolve={} "
                        + "apiInvoke={} pipelineInvoke={}",
                IRIS_LOADED, IRIS_INSTANCE != null, SHADER_PACK_IN_USE != null, SHADOW_PASS != null,
                IRIS_PACK_IN_USE_QUICK != null, apiPack, quickPack, shadowPass,
                (apiPack || quickPack) && !shadowPass, apiResolutionFailure, pipelineResolutionFailure,
                apiInvocationFailure, pipelineInvocationFailure);
    }

    private static boolean invokeApi(Method method) {
        if (method == null || IRIS_INSTANCE == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(IRIS_INSTANCE.invoke(null)));
        } catch (ReflectiveOperationException exception) {
            apiInvocationFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return false;
        }
    }

    private static boolean invokeStatic(Method method) {
        if (method == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException exception) {
            pipelineInvocationFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return false;
        }
    }
}
