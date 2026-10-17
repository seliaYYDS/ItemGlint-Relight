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
    /**
     * Pipeline handles are resolved once instead of once per frame: the previous implementation
     * ran {@code Class.forName}, {@code getMethod} and a full {@code getDeclaredFields} hierarchy
     * walk on every third-person capture, inside the render loop.
     */
    private static final Method GET_PIPELINE_MANAGER;
    private static boolean pipelineHandlesResolved;
    private static Method getPipelineNullable;
    private static Field pipelineTargetsField;
    private static Method getDepthTextureNoHand;
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
        Method pipelineManager = null;
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
                pipelineManager = iris.getMethod("getPipelineManager");
            } catch (ReflectiveOperationException exception) {
                pipelineResolutionFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                ItemGlintRelight.LOGGER.warn("Unable to resolve Iris pipeline outline compatibility hook", exception);
            }
        }
        IRIS_INSTANCE = instance;
        SHADER_PACK_IN_USE = shaderPackInUse;
        SHADOW_PASS = shadowPass;
        IRIS_PACK_IN_USE_QUICK = packInUseQuick;
        GET_PIPELINE_MANAGER = pipelineManager;
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

    /** True while Iris renders its shadow pass, whose view and projection differ from the frame. */
    public static boolean isIrisShadowPass() {
        return IRIS_LOADED && invokeApi(SHADOW_PASS);
    }

    public static boolean isIrisShaderPackRendering() {
        // Iris itself uses isPackInUseQuick() for its GameRenderer hooks. Prefer the same
        // pipeline check here; on some Iris builds the public API can be observed before its
        // pipeline is attached, which incorrectly sends the third-person composite through
        // the vanilla BufferSource path.
        // This deliberately stays uncached: the result changes between the shadow pass and the
        // main pass within a single frame, and the cached reflection handles make it cheap.
        return (invokeApi(SHADER_PACK_IN_USE) || invokeStatic(IRIS_PACK_IN_USE_QUICK))
                && !invokeApi(SHADOW_PASS);
    }

    /** Returns Iris' depthtex2/no-hand depth texture, if its active pipeline exposes it. */
    public static Object getIrisNoHandDepthTexture() {
        if (!IRIS_LOADED || !isIrisShaderPackRendering()) return null;
        if (!pipelineHandlesResolved) {
            // Only reached while a shader pack is rendering, so the pipeline already exists.
            pipelineHandlesResolved = true;
            resolvePipelineHandles();
        }
        if (getPipelineNullable == null || pipelineTargetsField == null || getDepthTextureNoHand == null) {
            return null;
        }
        try {
            Object manager = GET_PIPELINE_MANAGER.invoke(null);
            if (manager == null) return null;
            Object pipeline = getPipelineNullable.invoke(manager);
            if (pipeline == null) return null;
            Object targets = pipelineTargetsField.get(pipeline);
            return targets == null ? null : getDepthTextureNoHand.invoke(targets);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            pipelineInvocationFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            return null;
        }
    }

    private static void resolvePipelineHandles() {
        if (GET_PIPELINE_MANAGER == null) return;
        try {
            Object manager = GET_PIPELINE_MANAGER.invoke(null);
            if (manager == null) return;
            Method nullable = manager.getClass().getMethod("getPipelineNullable");
            Object pipeline = nullable.invoke(manager);
            if (pipeline == null) return;
            Field targetsField = null;
            for (Class<?> type = pipeline.getClass(); type != null && targetsField == null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getType().getName().equals("net.irisshaders.iris.targets.RenderTargets")) {
                        targetsField = field;
                        break;
                    }
                }
            }
            if (targetsField == null) return;
            targetsField.trySetAccessible();
            getPipelineNullable = nullable;
            pipelineTargetsField = targetsField;
            getDepthTextureNoHand = targetsField.getType().getMethod("getDepthTextureNoHand");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            pipelineResolutionFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
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
