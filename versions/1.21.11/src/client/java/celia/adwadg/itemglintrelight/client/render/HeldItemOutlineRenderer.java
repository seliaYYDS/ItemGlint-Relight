package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.mixin.client.GameRendererAccessor;
import celia.adwadg.itemglintrelight.mixin.client.GlDeviceAccessor;
import celia.adwadg.itemglintrelight.mixin.client.GlTextureViewAccessor;
import celia.adwadg.itemglintrelight.mixin.client.GlTextureAccessor;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.OutlineRenderMode;
import celia.adwadg.itemglintrelight.config.ColorScrollMode;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import celia.adwadg.itemglintrelight.mixin.client.FeatureRenderDispatcherAccessor;
import celia.adwadg.itemglintrelight.mixin.client.ItemStackLayerRenderStateAccessor;
import celia.adwadg.itemglintrelight.mixin.client.ItemStackRenderStateAccessor;
import celia.adwadg.itemglintrelight.mixin.client.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public final class HeldItemOutlineRenderer {
    private static final int OUTLINE_UNIFORM_BYTES = 256;
    private static final float REFERENCE_RENDER_HEIGHT = 1080.0F;
    private static final CaptureState MAIN_HAND = new CaptureState();
    private static final CaptureState OFF_HAND = new CaptureState();
    private static final CaptureState ARM_OCCLUDER = new CaptureState();
    private static final CaptureState PREVIEW = new CaptureState();
    private static final CaptureState THIRD_PERSON_MAIN_HAND = new CaptureState();
    private static final CaptureState THIRD_PERSON_OFF_HAND = new CaptureState();
    private static final Deque<CaptureState> THIRD_PERSON_CAPTURES = new ArrayDeque<>();
    private static PreviewRequest queuedPreview;
    private static final Map<String, float[][]> MATERIAL_PALETTE_CACHE = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, float[][]> eldest) {
            return size() > 128;
        }
    };
    private static InteractionHand recordingHand;
    private static InteractionHand submittingHand;
    private static CaptureState recordingThirdPerson;
    private static int itemSubmissionDepth;
    private static int externalSubmissionDepth;
    private static TextureTarget sceneDepth;
    private static TextureTarget thirdPersonSceneDepth;
    private static TextureTarget bloomFirst;
    private static TextureTarget bloomSecond;
    private static TextureTarget previewBloomFirst;
    private static TextureTarget previewBloomSecond;
    private static TextureTarget armOccluder;
    private static boolean capturingArmOccluder;
    private static UniformRing uniforms = new UniformRing("itemglintrelight_outline", OUTLINE_UNIFORM_BYTES, 1024);
    // GUI blits are consumed after the current render build. Unlike the hand pass, their
    // per-item material palettes must remain valid across several rendered frames.
    private static UniformRing guiUniforms = new UniformRing("itemglintrelight_gui_outline", OUTLINE_UNIFORM_BYTES, 32768);
    private static UniformRing blurUniforms = new UniformRing("itemglintrelight_bloom_blur", OUTLINE_UNIFORM_BYTES, 1024);
    private static long frameNumber;
    private static long nextDiagnosticMillis;
    private static long nextPreviewDiagnosticMillis;
    private static long nextThirdPersonDepthDiagnosticMillis;
    private static long nextIrisDepthProbeMillis;
    private static long thirdPersonCompositeSequence;
    private static String framebufferLookupFailure = "none";
    private static Object irisNoHandDepthTexture;
    private static int irisNoHandDepthFbo;
    private static boolean irisNoHandDepthReady;
    private static String irisNoHandDepthStatus = "not_attempted";
    private static boolean storageWrapped;
    private static int handPasses;
    private static Matrix4f handProjectionMatrix;

    private HeldItemOutlineRenderer() { }

    public static void beginFrame() {
        frameNumber++;
        recordingHand = null;
        submittingHand = null;
        itemSubmissionDepth = 0;
        externalSubmissionDepth = 0;
        storageWrapped = false;
        handPasses = 0;
        MAIN_HAND.reset();
        OFF_HAND.reset();
        ARM_OCCLUDER.reset();
        THIRD_PERSON_MAIN_HAND.reset();
        THIRD_PERSON_OFF_HAND.reset();
        recordingThirdPerson = null;
        THIRD_PERSON_CAPTURES.clear();
        capturingArmOccluder = false;
        uniforms.beginFrame();
        blurUniforms.beginFrame();
    }

    public static void beginHandPass(Matrix4f projection) {
        // Iris renders the real hands earlier from LevelRenderer, then invokes this vanilla
        // wrapper after restoring its hand matrices. Preserve the captured hand pose and stack.
        if (MAIN_HAND.captured || OFF_HAND.captured) {
            return;
        }
        handProjectionMatrix = projection == null ? null : new Matrix4f(projection);
        Minecraft minecraft = Minecraft.getInstance();
        if (shouldRender(minecraft) && minecraft.player != null) {
            prepareHand(InteractionHand.MAIN_HAND, minecraft.player.getMainHandItem());
            prepareHand(InteractionHand.OFF_HAND, minecraft.player.getOffhandItem());
        }
    }

    /**
     * Establishes the main-world-pass boundary used by shader-pack renderers.
     * Captures from pre-passes, including shadow rendering, must not reach the hand composite.
     */
    public static void beginMainWorldPass() {
        recordingHand = null;
        submittingHand = null;
        itemSubmissionDepth = 0;
        externalSubmissionDepth = 0;
        storageWrapped = false;
        MAIN_HAND.reset();
        OFF_HAND.reset();
        ARM_OCCLUDER.reset();
        capturingArmOccluder = false;
    }

    public static void endHandPass() {
        handProjectionMatrix = null;
    }

    public static void queuePreview(ItemStack item, float centerX, float centerY, float scale, float pitch, float yaw,
                                    ItemGlintRelightConfig config) {
        queuedPreview = new PreviewRequest(item.copy(), centerX, centerY, scale, pitch, yaw, config.copy());
    }

    public static void renderQueuedPreview(Minecraft minecraft) {
        PreviewRequest request = queuedPreview;
        previewDiagnostic("callback request=" + (request != null) + " player=" + (minecraft != null && minecraft.player != null)
                + " mainTarget=" + (minecraft == null ? "null" : minecraft.getMainRenderTarget().width + "x" + minecraft.getMainRenderTarget().height));
        if (request != null) {
            if (renderPreview(minecraft, request.item(), request.centerX(), request.centerY(), request.scale(),
                    request.pitch(), request.yaw(), request.config())) {
                queuedPreview = null;
            }
        }
    }

    public static void clearQueuedPreview() {
        queuedPreview = null;
    }

    public static void compositePreviewToTexture(Minecraft minecraft, ItemGlintRelightConfig config, TextureTarget mask,
                                                 GpuTextureView colorTarget, GpuTextureView depthTarget, float[][] materialPalette,
                                                 float outlineScale) {
        compositePreviewToTexture(minecraft, config, mask, colorTarget, depthTarget, materialPalette, outlineScale, 1.0F);
    }

    public static void compositePreviewToTexture(Minecraft minecraft, ItemGlintRelightConfig config, TextureTarget mask,
                                                 GpuTextureView colorTarget, GpuTextureView depthTarget, float[][] materialPalette,
                                                 float outlineScale, float colorScrollScale) {
        compositePreviewToTexture(minecraft, config, mask, colorTarget, depthTarget, materialPalette, outlineScale, colorScrollScale, outlineScale);
    }

    public static void compositePreviewToTexture(Minecraft minecraft, ItemGlintRelightConfig config, TextureTarget mask,
                                                 GpuTextureView colorTarget, GpuTextureView depthTarget, float[][] materialPalette,
                                                 float outlineScale, float colorScrollScale, float bloomScale) {
        compositePreviewToTexture(minecraft, config, mask, colorTarget, depthTarget, materialPalette,
                outlineScale, colorScrollScale, bloomScale, uniforms);
    }

    /** GUI items use a non-resetting uniform ring so in-flight GUI blits keep their palette. */
    public static void compositeGuiItemToTexture(Minecraft minecraft, ItemGlintRelightConfig config, TextureTarget mask,
                                                 GpuTextureView colorTarget, GpuTextureView depthTarget, float[][] materialPalette,
                                                 float outlineScale, float colorScrollScale, float bloomScale) {
        compositePreviewToTexture(minecraft, config, mask, colorTarget, depthTarget, materialPalette,
                outlineScale, colorScrollScale, bloomScale, guiUniforms);
    }

    private static void compositePreviewToTexture(Minecraft minecraft, ItemGlintRelightConfig config, TextureTarget mask,
                                                  GpuTextureView colorTarget, GpuTextureView depthTarget, float[][] materialPalette,
                                                  float outlineScale, float colorScrollScale, float bloomScale, UniformRing uniformRing) {
        if (minecraft == null || config == null || mask == null || colorTarget == null || depthTarget == null || !config.outlineEnabled()) {
            return;
        }
        int width = colorTarget.getWidth(0);
        int height = colorTarget.getHeight(0);
        GpuBufferSlice info = uniformRing.write(buffer -> writePreviewUniforms(buffer, width, height, config, materialPalette,
                outlineScale, colorScrollScale));
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        if (config.outlineBloomEnabled()) {
            GpuTextureView bloom = renderPreviewBloom(width, height, mask.getColorTextureView(), config, linear, bloomScale);
            submitPreviewBloomComposite(colorTarget, bloom, mask, depthTarget, info, linear);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "itemglintrelight_preview_outline", colorTarget, OptionalInt.empty())) {
            pass.setPipeline(HeldItemOutlinePipelines.outline());
            pass.setUniform("OutlineInfo", info);
            pass.bindTexture("MaskSampler", mask.getColorTextureView(), linear);
            pass.bindTexture("ItemDepthSampler", mask.getDepthTextureView(), nearest);
            pass.bindTexture("SceneDepthSampler", depthTarget, nearest);
            pass.bindTexture("ArmOccluderSampler", mask.getColorTextureView(), linear);
            pass.bindTexture("ArmOccluderDepthSampler", mask.getDepthTextureView(), nearest);
            pass.draw(0, 3);
        }
    }

    private static GpuTextureView renderPreviewBloom(int width, int height, GpuTextureView mask, ItemGlintRelightConfig config,
                                                      GpuSampler sampler, float outlineScale) {
        if (previewBloomFirst == null) {
            previewBloomFirst = new TextureTarget("itemglintrelight_preview_bloom_first", width, height, false);
            previewBloomSecond = new TextureTarget("itemglintrelight_preview_bloom_second", width, height, false);
        } else if (previewBloomFirst.width != width || previewBloomFirst.height != height) {
            previewBloomFirst.resize(width, height);
            previewBloomSecond.resize(width, height);
        }
        int passes = config.outlineBloomBlurPasses();
        float radius = config.outlineBloomRadius() * 3.0F * 0.4F * height / REFERENCE_RENDER_HEIGHT
                * Math.max(0.01F, outlineScale) / (float) Math.sqrt(passes);
        GpuTextureView source = mask;
        for (int pass = 0; pass < passes; pass++) {
            renderPreviewBloomBlur(width, height, previewBloomFirst, source, radius, 1.0F, 0.0F, config.outlineBloomQuality(), sampler, "horizontal");
            renderPreviewBloomBlur(width, height, previewBloomSecond, previewBloomFirst.getColorTextureView(), radius, 0.0F, 1.0F,
                    config.outlineBloomQuality(), sampler, "vertical");
            source = previewBloomSecond.getColorTextureView();
        }
        return source;
    }

    private static void renderPreviewBloomBlur(int width, int height, TextureTarget destination, GpuTextureView source, float radius,
                                               float directionX, float directionY, RenderQuality quality, GpuSampler sampler, String direction) {
        GpuBufferSlice blurInfo = blurUniforms.write(buffer -> put(buffer, directionX / width, directionY / height, radius, bloomSamples(quality)));
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "itemglintrelight_preview_bloom_blur_" + direction, destination.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(HeldItemOutlinePipelines.bloomBlur());
            pass.setUniform("BlurInfo", blurInfo);
            pass.bindTexture("InputSampler", source, sampler);
            pass.draw(0, 3);
        }
    }

    private static void submitPreviewBloomComposite(GpuTextureView colorTarget, GpuTextureView bloom, TextureTarget mask,
                                                    GpuTextureView sceneDepth, GpuBufferSlice info, GpuSampler sampler) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "itemglintrelight_preview_bloom_composite", colorTarget, OptionalInt.empty())) {
            pass.setPipeline(HeldItemOutlinePipelines.bloomComposite());
            pass.setUniform("OutlineInfo", info);
            pass.bindTexture("BloomSampler", bloom, sampler);
            pass.bindTexture("MaskSampler", mask.getColorTextureView(), sampler);
            pass.bindTexture("ItemDepthSampler", mask.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.bindTexture("SceneDepthSampler", sceneDepth, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }

    public static boolean renderPreview(Minecraft minecraft, ItemStack item, float centerX, float centerY, float scale,
                                     float pitch, float yaw, ItemGlintRelightConfig config) {
        if (minecraft == null || item == null || item.isEmpty() || config == null) {
            return false;
        }
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureTarget(mainTarget);
        ensureDispatcher(minecraft, PREVIEW);
        PREVIEW.reset();
        PREVIEW.requested = true;
        PREVIEW.captured = true;
        PREVIEW.item = item;
        snapshotRenderContext(PREVIEW);

        ItemStackRenderState itemState = new ItemStackRenderState();
        try {
            minecraft.getItemModelResolver().updateForTopItem(itemState, item, ItemDisplayContext.GUI, minecraft.level, null, 0);
        } catch (Throwable throwable) {
            previewDiagnostic("model resolver failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return false;
        }
        previewDiagnostic("resolved empty=" + itemState.isEmpty() + " blockLight=" + itemState.usesBlockLight()
                + " bounds=" + itemState.getModelBoundingBox());
        PoseStack pose = new PoseStack();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(new org.joml.Quaternionf().rotateX((float) Math.toRadians(pitch)));
        pose.mulPose(new org.joml.Quaternionf().rotateY((float) Math.toRadians(yaw)));
        itemState.submit(pose, PREVIEW.storage, 15728880, OverlayTexture.NO_OVERLAY, 0);
        previewDiagnostic("submitted orders=" + PREVIEW.storage.getSubmitsPerOrder().size()
                + " nodes=" + PREVIEW.storage.getSubmitsPerOrder().values().stream().mapToInt(collection -> 1).sum());

        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainTarget.getDepthTexture(), 1.0D);
        minecraft.gameRenderer.getLighting().setupFor(itemState.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        PREVIEW.dispatcher.renderAllFeatures();
        PREVIEW.buffers.bufferSource().endBatch();
        PREVIEW.buffers.outlineBufferSource().endOutlineBatch();
        PREVIEW.buffers.crumblingBufferSource().endBatch();
        previewDiagnostic("screen replay complete captured=" + PREVIEW.captured + " replayed=" + PREVIEW.replayed);

        clear(sceneDepth);
        sceneDepth.copyDepthFrom(mainTarget);
        clear(armOccluder);
        renderCapture(minecraft, PREVIEW);
        previewDiagnostic("mask replay complete target=" + sceneDepth.width + "x" + sceneDepth.height
                + " projection=" + (PREVIEW.projectionType == null ? "null" : PREVIEW.projectionType));
        if (config.outlineEnabled()) {
            if (config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
                capturePreviewTextureColors(itemState, config);
            }
            submitComposite(minecraft, mainTarget, config, resolveMaterialPalette(PREVIEW, config), "preview", null);
            previewDiagnostic("composite submitted outline=" + config.outlineEnabled() + " bloom=" + config.outlineBloomEnabled());
        }
        PREVIEW.reset();
        return true;
    }

    public static void beginHand(InteractionHand hand, ItemStack stack) {
        handPasses++;
        if (!prepareHand(hand, stack)) {
            recordingHand = null;
            return;
        }
        recordingHand = hand;
    }

    public static void endHand() {
        recordingHand = null;
    }

    public static void beginExternalHandSubmission(InteractionHand hand, ItemStack stack, PoseStack pose) {
        beginHand(hand, stack);
        if (recordingHand == null) return;
        CaptureState state = stateFor(hand);
        state.externalSubmission = true;
        snapshotRenderContext(state);
        if (externalSubmissionDepth == 0 && pose != null) {
            state.itemPoseMatrix = new Matrix4f(pose.last().pose());
        }
        externalSubmissionDepth++;
    }

    public static void endExternalHandSubmission() {
        if (externalSubmissionDepth > 0) externalSubmissionDepth--;
        endHand();
    }

    public static void beginArmOccluderCapture(PoseStack pose) {
        snapshotRenderContext(ARM_OCCLUDER);
        if (pose != null) {
            ARM_OCCLUDER.itemPoseMatrix = new Matrix4f(pose.last().pose());
        }
        capturingArmOccluder = true;
    }

    public static void endArmOccluderCapture() { capturingArmOccluder = false; }

    public static void beginItemSubmission(ItemDisplayContext context, PoseStack pose) {
        InteractionHand hand = handForContext(context);
        if (hand != null && stateFor(hand).requested) {
            snapshotRenderContext(stateFor(hand));
            if (itemSubmissionDepth == 0 && pose != null) {
                stateFor(hand).itemPoseMatrix = new Matrix4f(pose.last().pose());
            }
            submittingHand = hand;
            itemSubmissionDepth++;
        }
    }

    public static void endItemSubmission(ItemDisplayContext context) {
        if (handForContext(context) != null && itemSubmissionDepth > 0) {
            itemSubmissionDepth--;
            if (itemSubmissionDepth == 0) {
                submittingHand = null;
            }
        }
    }

    public static void beginThirdPersonItem(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
        if (!isThirdPersonContext(context) || recordingThirdPerson != null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || stack == null || stack.isEmpty()) return;

        ItemGlintRelightConfig baseConfig = ItemGlintRelightConfigManager.get();
        if (!baseConfig.outlineEnabled() || !baseConfig.renderThirdPerson() || !baseConfig.outlineThirdPerson()) {
            return;
        }
        ItemGlintRelightConfig config = DisplayRuleManager.resolve(stack, baseConfig);
        if (!config.outlineEnabled() || !config.renderThirdPerson() || !config.outlineThirdPerson()) {
            return;
        }

        CaptureState capture = thirdPersonStateFor(context);
        capture.reset();
        capture.item = stack.copy();
        capture.stack = stack.toString();
        capture.config = config;
        capture.itemContext = context;
        capture.requested = true;
        capture.externalSubmission = true;
        if (config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
            capture.materialPaletteKey = materialPaletteKey(stack);
            capture.materialPalette = MATERIAL_PALETTE_CACHE.get(capture.materialPaletteKey);
        }
        ensureDispatcher(minecraft, capture);
        capture.storage.clear();
        snapshotRenderContext(capture);
        if (pose != null) capture.itemPoseMatrix = new Matrix4f(pose.last().pose());
        recordingThirdPerson = capture;
    }

    public static void endThirdPersonItem(ItemDisplayContext context) {
        if (!isThirdPersonContext(context) || recordingThirdPerson == null) return;
        CaptureState capture = recordingThirdPerson;
        recordingThirdPerson = null;
        if (capture.captured) {
            THIRD_PERSON_CAPTURES.addLast(capture);
        } else {
            capture.reset();
        }
    }

    public static void captureThirdPersonItemDirect(Minecraft minecraft, InteractionHand hand, ItemStack stack,
                                                    ItemStackRenderState itemState, PoseStack pose, int light,
                                                    int overlay, int color) {
        if (minecraft == null || minecraft.level == null || stack == null || stack.isEmpty()
                || itemState == null || itemState.isEmpty() || !shouldRenderThirdPerson(minecraft)) {
            return;
        }
        ItemGlintRelightConfig base = ItemGlintRelightConfigManager.get();
        ItemGlintRelightConfig config = DisplayRuleManager.resolve(stack, base);
        if (!config.outlineEnabled() || !config.renderThirdPerson() || !config.outlineThirdPerson()) return;
        CaptureState capture = hand == InteractionHand.MAIN_HAND ? THIRD_PERSON_MAIN_HAND : THIRD_PERSON_OFF_HAND;
        capture.reset();
        capture.item = stack.copy();
        capture.stack = stack.toString();
        capture.config = config;
        capture.itemContext = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        capture.requested = true;
        capture.externalSubmission = true;
        if (config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
            capture.materialPaletteKey = materialPaletteKey(stack);
            capture.materialPalette = MATERIAL_PALETTE_CACHE.get(capture.materialPaletteKey);
        }
        ensureDispatcher(minecraft, capture);
        snapshotRenderContext(capture);
        if (pose != null) capture.itemPoseMatrix = new Matrix4f(pose.last().pose());
        itemState.submit(pose, capture.storage, light, overlay, color);
        capture.captured = true;
        capture.submittedItems++;
        THIRD_PERSON_CAPTURES.addLast(capture);
    }

    private static boolean shouldRenderThirdPerson(Minecraft minecraft) {
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && !minecraft.options.getCameraType().isFirstPerson()
                && minecraft.screen == null
                && config.outlineEnabled() && config.renderThirdPerson() && config.outlineThirdPerson();
    }

    public static SubmitNodeCollector wrapThirdPersonCollector(Minecraft minecraft, SubmitNodeCollector original) {
        if (!shouldRenderThirdPerson(minecraft) || original instanceof MirroringCollector || original instanceof MirroringStorage) {
            return original;
        }
        return original instanceof SubmitNodeStorage storage ? new MirroringStorage(storage) : new MirroringCollector(original);
    }

    /**
     * Runs after entity batches flush. World and opaque entity depth therefore hide the outline;
     * later block entities and particles keep their native order and can still cover it.
     */
    private static void compositeThirdPerson(Minecraft minecraft, String source) {
        if (minecraft == null || THIRD_PERSON_CAPTURES.isEmpty()) return;
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureTarget(mainTarget);
        clear(armOccluder);
        while (!THIRD_PERSON_CAPTURES.isEmpty()) {
            CaptureState capture = THIRD_PERSON_CAPTURES.removeFirst();
            if (!capture.captured || capture.config == null || capture.dispatcher == null || capture.buffers == null) {
                capture.reset();
                continue;
            }
            try {
                // Preserve the completed world depth for the screen-space pass, then seed the
                // replay target with the same depth.  The replay is now hardware depth-tested
                // before its mask can reach the outline shader.  This is essential with Iris:
                // its final pass owns the main target depth, whereas a blank replay target lets
                // hidden item fragments create a silhouette unconditionally.
                boolean irisDepthReady = copyIrisNoHandDepth(thirdPersonSceneDepth, mainTarget);
                if (!irisDepthReady && HeldItemOutlineCompat.isIrisShaderPackRendering()) {
                    thirdPersonDepthDiagnostic(source, "iris_depth_unavailable", mainTarget, capture);
                    continue;
                }
                if (!irisDepthReady) {
                    thirdPersonSceneDepth.copyDepthFrom(mainTarget);
                }
                if (irisDepthReady) {
                    // depthtex2 already contains the original third-person item. Replaying into
                    // that depth makes the item compete with itself and produces frame-to-frame
                    // z-fighting. Capture a complete private mask instead; the outline and bloom
                    // shaders below perform the authoritative per-pixel depthtex2 rejection.
                    clear(sceneDepth);
                } else {
                    sceneDepth.copyDepthFrom(thirdPersonSceneDepth);
                    clearColor(sceneDepth);
                }
                thirdPersonCompositeSequence++;
                thirdPersonDepthDiagnostic(source, "preload", mainTarget, capture);
                renderCapture(minecraft, capture, sceneDepth);
                irisDepthProbe(mainTarget);
                thirdPersonDepthDiagnostic(source, "replay", mainTarget, capture);
                ItemGlintRelightConfig config = capture.config.copy();
                config.setOutlineQuality(config.thirdPersonOutlineQuality());
                config.setOutlineWidth(config.outlineWidth() * 0.5F);
                config.setOutlineBloomRadius(config.outlineBloomRadius() * 0.5F);
                if (config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE && capture.materialPalette == null
                        && capture.item != null && !capture.item.isEmpty()) {
                    captureFallbackTextureColors(minecraft, capture, null);
                }
                submitComposite(minecraft, mainTarget, config, resolveMaterialPalette(capture, config), "third_person", null,
                        thirdPersonSceneDepth.getDepthTextureView());
                thirdPersonDepthDiagnostic(source, "composite", mainTarget, capture);
            } finally {
                capture.reset();
            }
        }
    }

    /**
     * Entity layers submit their nodes during {@code renderLevel}; they are only available once
     * the game's primary BufferSource is flushed.  Compositing earlier observes the previous
     * frame's capture and cannot use the current world depth.
     */
    public static void compositeThirdPersonAfterMainBatch(Minecraft minecraft, Object bufferSource) {
        if (minecraft == null || minecraft.gameRenderer == null || bufferSource == null
                || THIRD_PERSON_CAPTURES.isEmpty() || !shouldRenderThirdPerson(minecraft)) {
            return;
        }
        HeldItemOutlineCompat.logDiagnosticStateOnce();
        if (HeldItemOutlineCompat.isIrisShaderPackRendering()) {
            return;
        }
        RenderBuffers renderBuffers = ((GameRendererAccessor) minecraft.gameRenderer).itemglintrelight$getRenderBuffers();
        if (renderBuffers == null || bufferSource != renderBuffers.bufferSource()) {
            return;
        }
        compositeThirdPerson(minecraft, "main_batch");
    }

    public static void compositeThirdPersonAfterIrisFrame(Minecraft minecraft) {
        if (minecraft == null || THIRD_PERSON_CAPTURES.isEmpty()
                || !shouldRenderThirdPerson(minecraft) || !HeldItemOutlineCompat.isIrisShaderPackRendering()) {
            return;
        }
        HeldItemOutlineCompat.logDiagnosticStateOnce();
        compositeThirdPerson(minecraft, "iris_render_tail");
    }

    public static SubmitNodeStorage wrapStorage(Minecraft minecraft, SubmitNodeStorage original) {
        if (!shouldRender(minecraft) || original instanceof MirroringStorage) {
            return original;
        }
        ensureDispatcher(minecraft, MAIN_HAND);
        ensureDispatcher(minecraft, OFF_HAND);
        ensureDispatcher(minecraft, ARM_OCCLUDER);
        MAIN_HAND.storage.clear();
        OFF_HAND.storage.clear();
        storageWrapped = true;
        return new MirroringStorage(original);
    }

    public static SubmitNodeCollector wrapCollector(Minecraft minecraft, SubmitNodeCollector original) {
        if (original instanceof SubmitNodeStorage storage) {
            return wrapStorage(minecraft, storage);
        }
        if (!shouldRender(minecraft) || original instanceof MirroringCollector) {
            return original;
        }
        ensureDispatcher(minecraft, MAIN_HAND);
        ensureDispatcher(minecraft, OFF_HAND);
        ensureDispatcher(minecraft, ARM_OCCLUDER);
        MAIN_HAND.storage.clear();
        OFF_HAND.storage.clear();
        storageWrapped = true;
        return new MirroringCollector(original);
    }

    public static void composite(Minecraft minecraft) {
        if (!shouldRender(minecraft)) {
            diagnostic("skip: render policy is inactive " + renderPolicy(minecraft));
            return;
        }
        if (!MAIN_HAND.captured && !OFF_HAND.captured) {
            diagnostic("skip: no item nodes captured");
            return;
        }
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ensureTarget(mainTarget);
        try {
            clear(armOccluder);
            renderCapture(minecraft, ARM_OCCLUDER, armOccluder);
            if (ItemGlintRelightConfigManager.get().outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
                captureFallbackTextureColors(minecraft, MAIN_HAND, InteractionHand.MAIN_HAND);
                captureFallbackTextureColors(minecraft, OFF_HAND, InteractionHand.OFF_HAND);
            }
            compositeCapture(minecraft, mainTarget, MAIN_HAND, "main");
            compositeCapture(minecraft, mainTarget, OFF_HAND, "off");
        } finally {
            MAIN_HAND.reset();
            OFF_HAND.reset();
        }
    }

    private static void compositeCapture(Minecraft minecraft, RenderTarget mainTarget, CaptureState state, String hand) {
        if (!state.captured) {
            return;
        }
        clear(sceneDepth);
        sceneDepth.copyDepthFrom(mainTarget);
        renderCapture(minecraft, state);
        ItemGlintRelightConfig config = state.config == null ? ItemGlintRelightConfigManager.get() : state.config;
        submitComposite(minecraft, mainTarget, config, resolveMaterialPalette(state, config), hand, resolveCompositeScissor(mainTarget, state));
    }

    private static void compositeCombined(Minecraft minecraft, RenderTarget mainTarget) {
        clear(sceneDepth);
        sceneDepth.copyDepthFrom(mainTarget);
        renderCapture(minecraft, MAIN_HAND);
        renderCapture(minecraft, OFF_HAND);
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        submitComposite(minecraft, mainTarget, config, resolveMaterialPalette(MAIN_HAND, config), "combined",
                ScissorRect.union(resolveCompositeScissor(mainTarget, MAIN_HAND), resolveCompositeScissor(mainTarget, OFF_HAND)));
    }

    private static void submitComposite(Minecraft minecraft, RenderTarget mainTarget, ItemGlintRelightConfig config, float[][] palette, String hand, ScissorRect scissor) {
        submitComposite(minecraft, mainTarget, config, palette, hand, scissor, mainTarget.getDepthTextureView());
    }

    private static void submitComposite(Minecraft minecraft, RenderTarget mainTarget, ItemGlintRelightConfig config, float[][] palette,
                                        String hand, ScissorRect scissor, GpuTextureView sceneDepthView) {
        TextureTarget maskTarget = sceneDepth;
        boolean thirdPerson = "third_person".equals(hand);
        GpuBufferSlice info = uniforms.write(buffer -> writeUniforms(buffer, mainTarget, config, minecraft, palette, scissor, thirdPerson));
        GpuSampler maskSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        if (config.outlineBloomEnabled()) {
            GpuTextureView bloom = renderBloom(mainTarget, config, scissor, maskSampler, maskTarget.getColorTextureView());
            submitBloomComposite(mainTarget, bloom, info, scissor, maskSampler, depthSampler, hand, maskTarget, sceneDepthView);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "itemglintrelight_held_outline_" + hand, mainTarget.getColorTextureView(), OptionalInt.empty())) {
            if (scissor != null) {
                pass.enableScissor(scissor.x, scissor.y, scissor.width, scissor.height);
            }
            pass.setPipeline(HeldItemOutlinePipelines.outline());
            pass.setUniform("OutlineInfo", info);
            pass.bindTexture("MaskSampler", maskTarget.getColorTextureView(), maskSampler);
            pass.bindTexture("ItemDepthSampler", maskTarget.getDepthTextureView(), depthSampler);
            pass.bindTexture("SceneDepthSampler", sceneDepthView, depthSampler);
            pass.bindTexture("ArmOccluderSampler", armOccluder.getColorTextureView(), maskSampler);
            pass.bindTexture("ArmOccluderDepthSampler", armOccluder.getDepthTextureView(), depthSampler);
            pass.draw(0, 3);
            diagnostic("composite " + hand + " submitted target=" + mainTarget.width + "x" + mainTarget.height
                    + " radius=" + resolveOutlineRadius(mainTarget, config));
        }
    }

    private static boolean shouldRender(Minecraft minecraft) {
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && minecraft.options.getCameraType().isFirstPerson()
                && config.outlineEnabled();
    }

    private static boolean isEnabled(InteractionHand hand, ItemStack stack) {
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        return stack != null && !stack.isEmpty()
                && (hand == InteractionHand.MAIN_HAND ? config.outlineMainHand() : config.outlineOffHand());
    }

    private static boolean prepareHand(InteractionHand hand, ItemStack stack) {
        CaptureState state = stateFor(hand);
        state.requested = false;
        state.config = null;
        if (!isEnabled(hand, stack)) {
            state.disabledStack = stack == null ? "null" : stack.toString();
            return false;
        }
        state.item = stack;
        state.config = DisplayRuleManager.resolve(stack, ItemGlintRelightConfigManager.get());
        if (!state.config.outlineEnabled()) {
            state.disabledStack = stack.toString();
            return false;
        }
        state.requested = true;
        state.stack = stack.toString();
        if (state.config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
            state.materialPaletteKey = materialPaletteKey(stack);
            state.materialPalette = MATERIAL_PALETTE_CACHE.get(state.materialPaletteKey);
        }
        snapshotRenderContext(state);
        return true;
    }

    private static void snapshotRenderContext(CaptureState state) {
        state.modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        state.projectionMatrix = RenderSystem.getProjectionMatrixBuffer();
        state.projectionType = RenderSystem.getProjectionType();
    }

    private static CaptureState stateFor(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN_HAND : OFF_HAND;
    }

    private static boolean isFirstPersonContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private static boolean isThirdPersonContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static CaptureState thirdPersonStateFor(ItemDisplayContext context) {
        return context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? THIRD_PERSON_OFF_HAND : THIRD_PERSON_MAIN_HAND;
    }

    private static InteractionHand handForContext(ItemDisplayContext context) {
        boolean mainHandOnRight = Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.getMainArm() == HumanoidArm.RIGHT;
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return mainHandOnRight ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        }
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return mainHandOnRight ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static ItemDisplayContext contextForHand(InteractionHand hand) {
        boolean mainHandOnRight = Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.getMainArm() == HumanoidArm.RIGHT;
        boolean renderOnRight = (hand == InteractionHand.MAIN_HAND) == mainHandOnRight;
        return renderOnRight ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    private static SubmitNodeCollection captureCollection(int order) {
        return captureCollection(order, null);
    }

    private static SubmitNodeCollection captureCollection(int order, InteractionHand directHand) {
        if (capturingArmOccluder) {
            if (ARM_OCCLUDER.storage == null) return null;
            ARM_OCCLUDER.captured = true;
            return ARM_OCCLUDER.storage.order(order);
        }
        if (recordingThirdPerson != null && recordingThirdPerson.requested && recordingThirdPerson.storage != null) {
            recordingThirdPerson.captured = true;
            recordingThirdPerson.submittedItems++;
            return recordingThirdPerson.storage.order(order);
        }
        InteractionHand hand = submittingHand != null ? submittingHand : recordingHand;
        if (hand == null) hand = directHand;
        if (hand == null || (directHand == null && itemSubmissionDepth <= 0 && externalSubmissionDepth <= 0)) {
            return null;
        }
        CaptureState capture = stateFor(hand);
        if (!capture.requested || capture.storage == null) {
            return null;
        }
        capture.captured = true;
        capture.submittedItems++;
        return capture.storage.order(order);
    }

    private static void ensureDispatcher(Minecraft minecraft, CaptureState state) {
        if (state.dispatcher != null) {
            return;
        }
        state.buffers = new RenderBuffers(1);
        state.storage = new SubmitNodeStorage();
        FeatureRenderDispatcher source = minecraft.gameRenderer.getFeatureRenderDispatcher();
        state.dispatcher = new FeatureRenderDispatcher(
                state.storage,
                minecraft.getBlockRenderer(),
                state.buffers.bufferSource(),
                ((FeatureRenderDispatcherAccessor) source).itemglintrelight$getAtlasManager(),
                state.buffers.outlineBufferSource(),
                state.buffers.crumblingBufferSource(),
                minecraft.font
        );
    }

    private static void ensureTarget(RenderTarget mainTarget) {
        if (sceneDepth == null) {
            sceneDepth = new TextureTarget("itemglintrelight_hand_mask", mainTarget.width, mainTarget.height, true);
            bloomFirst = new TextureTarget("itemglintrelight_hand_bloom_first", mainTarget.width, mainTarget.height, false);
            bloomSecond = new TextureTarget("itemglintrelight_hand_bloom_second", mainTarget.width, mainTarget.height, false);
            armOccluder = new TextureTarget("itemglintrelight_hand_arm_occluder", mainTarget.width, mainTarget.height, true);
            thirdPersonSceneDepth = new TextureTarget("itemglintrelight_third_person_scene_depth", mainTarget.width, mainTarget.height, true);
        } else if (sceneDepth.width != mainTarget.width || sceneDepth.height != mainTarget.height) {
            sceneDepth.resize(mainTarget.width, mainTarget.height);
            bloomFirst.resize(mainTarget.width, mainTarget.height);
            bloomSecond.resize(mainTarget.width, mainTarget.height);
            armOccluder.resize(mainTarget.width, mainTarget.height);
            thirdPersonSceneDepth.resize(mainTarget.width, mainTarget.height);
        }
    }


    private static GpuTextureView renderBloom(RenderTarget target, ItemGlintRelightConfig config, ScissorRect scissor, GpuSampler sampler,
                                             GpuTextureView mask) {
        clearColor(bloomFirst);
        clearColor(bloomSecond);
        int passes = config.outlineBloomBlurPasses();
        float radius = resolveBloomRadius(target, config) / (float) Math.sqrt(passes);
        GpuTextureView source = mask;
        for (int pass = 0; pass < passes; pass++) {
            renderBloomBlur(target, bloomFirst, source, radius, 1.0F, 0.0F, config.outlineBloomQuality(), scissor, sampler, "horizontal");
            renderBloomBlur(target, bloomSecond, bloomFirst.getColorTextureView(), radius, 0.0F, 1.0F, config.outlineBloomQuality(), scissor, sampler, "vertical");
            source = bloomSecond.getColorTextureView();
        }
        return source;
    }

    private static void renderBloomBlur(RenderTarget mainTarget, TextureTarget destination, GpuTextureView source, float radius, float directionX,
                                        float directionY, RenderQuality quality, ScissorRect scissor, GpuSampler sampler, String direction) {
        GpuBufferSlice blurInfo = blurUniforms.write(buffer -> put(buffer, directionX / mainTarget.width, directionY / mainTarget.height,
                radius, bloomSamples(quality)));
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "itemglintrelight_held_bloom_blur_" + direction, destination.getColorTextureView(), OptionalInt.empty())) {
            if (scissor != null) {
                pass.enableScissor(scissor.x, scissor.y, scissor.width, scissor.height);
            }
            pass.setPipeline(HeldItemOutlinePipelines.bloomBlur());
            pass.setUniform("BlurInfo", blurInfo);
            pass.bindTexture("InputSampler", source, sampler);
            pass.draw(0, 3);
        }
    }

    private static void submitBloomComposite(RenderTarget mainTarget, GpuTextureView bloom, GpuBufferSlice info, ScissorRect scissor,
                                             GpuSampler sampler, GpuSampler depthSampler, String hand, TextureTarget maskTarget, GpuTextureView sceneDepthView) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "itemglintrelight_held_bloom_composite_" + hand, mainTarget.getColorTextureView(), OptionalInt.empty())) {
            if (scissor != null) {
                pass.enableScissor(scissor.x, scissor.y, scissor.width, scissor.height);
            }
            pass.setPipeline(HeldItemOutlinePipelines.bloomComposite());
            pass.setUniform("OutlineInfo", info);
            pass.bindTexture("BloomSampler", bloom, sampler);
            pass.bindTexture("MaskSampler", maskTarget.getColorTextureView(), sampler);
            pass.bindTexture("ItemDepthSampler", maskTarget.getDepthTextureView(), depthSampler);
            pass.bindTexture("SceneDepthSampler", sceneDepthView, depthSampler);
            pass.draw(0, 3);
        }
    }

    private static void renderCapture(Minecraft minecraft, CaptureState state) {
        renderCapture(minecraft, state, sceneDepth);
    }

    private static void renderCapture(Minecraft minecraft, CaptureState state, TextureTarget target) {
        if (!state.captured || state.dispatcher == null || state.buffers == null) {
            return;
        }
        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        boolean restoreProjection = state.projectionMatrix != null && state.projectionType != null;
        if (restoreProjection) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(state.projectionMatrix, state.projectionType);
        }
        boolean restoreModelView = state.modelViewMatrix != null;
        if (restoreModelView) {
            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().set(state.modelViewMatrix);
        }
        try {
            state.dispatcher.renderAllFeatures();
            state.buffers.bufferSource().endBatch();
            state.buffers.outlineBufferSource().endOutlineBatch();
            state.buffers.crumblingBufferSource().endBatch();
            state.replayed = true;
        } finally {
            if (restoreModelView) {
                RenderSystem.getModelViewStack().popMatrix();
            }
            if (restoreProjection) {
                RenderSystem.restoreProjectionMatrix();
            }
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }
    }

    private static void clear(TextureTarget target) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), 0, target.getDepthTexture(), 1.0D);
    }

    private static void clearColor(TextureTarget target) {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
    }

    private static void writeUniforms(ByteBuffer buffer, RenderTarget target, ItemGlintRelightConfig config, Minecraft minecraft, float[][] materialPalette,
                                      ScissorRect scissor, boolean thirdPerson) {
        putColor(buffer, config.outlinePrimaryColor(), config.outlineOpacity());
        putColor(buffer, config.outlineSecondaryColor(), config.outlineOpacity());
        put(buffer, target.width, target.height, resolveOutlineRadius(target, config), config.outlineAlphaThreshold());
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000.0F;
        put(buffer, colorMode(config.outlineColorMode()), time, config.outlineColorScrollSpeed() * 9.0F, config.outlineSoftness());
        put(buffer, sampleCount(config.outlineQuality()), config.outlineGlowIntensity(), materialPalette.length, config.outlineBloomIntensity());
        float directionRadians = (float) Math.toRadians(config.outlineColorScrollDirection());
        put(buffer, (float) Math.cos(directionRadians), -(float) Math.sin(directionRadians), config.outlineColorScrollInterval(), renderMode(config.outlineRenderMode()));
        float centerX = scissor == null ? target.width * 0.5F : scissor.x + scissor.width * 0.5F;
        float centerY = scissor == null ? target.height * 0.5F : scissor.y + scissor.height * 0.5F;
        float padding = resolveCompositePadding(target, config);
        float halfWidth = scissor == null ? target.width * 0.25F : Math.max(1.0F, scissor.width * 0.5F - padding);
        float halfHeight = scissor == null ? target.height * 0.25F : Math.max(1.0F, scissor.height * 0.5F - padding);
        float pathRadius = ellipsePathRadius(halfWidth, halfHeight);
        put(buffer, scrollMode(config.outlineColorScrollMode()), centerX, centerY, Math.max(pathRadius, 1.0F));
        // z=2 marks the third-person path. It retains scene occlusion but uses hard visibility
        // in the composite shaders, avoiding sub-pixel depth-transition flicker with Iris.
        put(buffer, halfWidth, halfHeight, thirdPerson ? 2.0F : 0.0F, 0.0F);
        for (int index = 0; index < 8; index++) {
            float[] color = index < materialPalette.length ? materialPalette[index] : materialPalette[0];
            put(buffer, color[0], color[1], color[2], 1.0F);
        }
    }

    private static void writePreviewUniforms(ByteBuffer buffer, int width, int height, ItemGlintRelightConfig config, float[][] materialPalette,
                                             float outlineScale, float colorScrollScale) {
        putColor(buffer, config.outlinePrimaryColor(), config.outlineOpacity());
        putColor(buffer, config.outlineSecondaryColor(), config.outlineOpacity());
        float radius = config.outlineWidth() * 0.4F * height / REFERENCE_RENDER_HEIGHT * Math.max(0.01F, outlineScale)
                * (config.outlineRenderMode() == OutlineRenderMode.CUBIC ? 1.2F : 1.0F);
        put(buffer, width, height, radius, config.outlineAlphaThreshold());
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000.0F;
        put(buffer, colorMode(config.outlineColorMode()), time, config.outlineColorScrollSpeed() * 9.0F, config.outlineSoftness());
        float[][] palette = materialPalette == null || materialPalette.length == 0
                ? new float[][]{rgb(config.outlinePrimaryColor())} : materialPalette;
        put(buffer, sampleCount(config.outlineQuality()), config.outlineGlowIntensity(), palette.length, config.outlineBloomIntensity());
        float directionRadians = (float) Math.toRadians(config.outlineColorScrollDirection());
        put(buffer, (float) Math.cos(directionRadians), -(float) Math.sin(directionRadians), config.outlineColorScrollInterval(), renderMode(config.outlineRenderMode()));
        float halfWidth = Math.max(1.0F, width * 0.25F);
        float halfHeight = Math.max(1.0F, height * 0.25F);
        put(buffer, scrollMode(config.outlineColorScrollMode()), width * 0.5F, height * 0.5F, Math.max(ellipsePathRadius(halfWidth, halfHeight), 1.0F));
        put(buffer, halfWidth, halfHeight, 1.0F, Math.max(1.0F, colorScrollScale));
        for (int index = 0; index < 8; index++) {
            float[] color = config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE
                    ? palette[index % palette.length]
                    : ((index & 1) == 0 ? rgb(config.outlinePrimaryColor()) : rgb(config.outlineSecondaryColor()));
            put(buffer, color[0], color[1], color[2], 1.0F);
        }
    }

    public static float[][] resolvePreviewMaterialPalette(ItemStackRenderState renderState, ItemGlintRelightConfig config) {
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return new float[][]{rgb(config.outlinePrimaryColor()), rgb(config.outlineSecondaryColor())};
        }
        CaptureState capture = new CaptureState();
        capturePreviewTextureColors(capture, renderState, config);
        return resolveMaterialPalette(capture, config);
    }

    /** Resolves GUI sampling from a private state, never from GuiRenderer's reusable tracking state. */
    public static float[][] resolveGuiMaterialPalette(Minecraft minecraft, ItemStack stack, ItemGlintRelightConfig config) {
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return resolvePreviewMaterialPalette(new ItemStackRenderState(), config);
        }
        if (minecraft == null || stack == null || stack.isEmpty()) {
            return new float[][]{rgb(config.outlinePrimaryColor())};
        }
        ItemStackRenderState state = new ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, minecraft.level, null, 0);
        return resolvePreviewMaterialPalette(state, config);
    }

    private static float[] rgb(int color) {
        return new float[]{((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F, (color & 255) / 255.0F};
    }

    private static float colorMode(OutlineColorMode mode) {
        return switch (mode) {
            case SINGLE -> 0.0F;
            case DUAL -> 1.0F;
            case RAINBOW -> 2.0F;
            case TEXTURE_SAMPLE -> 3.0F;
        };
    }

    private static int sampleCount(RenderQuality quality) {
        return switch (quality) {
            case LOW -> 12;
            case MEDIUM -> 24;
            case HIGH -> 48;
        };
    }

    private static float renderMode(OutlineRenderMode mode) {
        return mode == OutlineRenderMode.CUBIC ? 1.0F : 0.0F;
    }

    private static float scrollMode(ColorScrollMode mode) {
        return mode == ColorScrollMode.OUTLINE ? 1.0F : 0.0F;
    }

    private static float ellipsePathRadius(float halfWidth, float halfHeight) {
        float sum = halfWidth + halfHeight;
        float difference = halfWidth - halfHeight;
        return (3.0F * sum - (float) Math.sqrt(Math.max(0.0F, (3.0F * halfWidth + halfHeight) * (halfWidth + 3.0F * halfHeight)))) / 2.0F;
    }

    private static int bloomSamples(RenderQuality quality) {
        return switch (quality) {
            case LOW -> 2;
            case MEDIUM -> 4;
            case HIGH -> 6;
        };
    }

    private static void captureTextureColors(List<BakedQuad> quads, int[] tints) {
        InteractionHand hand = submittingHand != null ? submittingHand : recordingHand;
        if (hand == null || itemSubmissionDepth <= 0
                || ItemGlintRelightConfigManager.get().outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE
                || quads == null || quads.isEmpty()) {
            return;
        }

        CaptureState state = stateFor(hand);
        if (state.materialPalette != null) {
            return;
        }
        for (BakedQuad quad : quads) {
            if (quad != null) captureTextureColors(state, quad.sprite(), resolveTint(quad, tints));
        }
    }

    private static void captureTextureColors(CaptureState state, TextureAtlasSprite sprite, int tint) {
        if (state == null || state.materialPalette != null || sprite == null || sprite.contents() == null) return;
        NativeImage[] mipLevels = ((SpriteContentsAccessor) (Object) sprite.contents()).itemglintrelight$getByMipLevel();
        NativeImage image = mipLevels == null || mipLevels.length == 0 ? null : mipLevels[0];
        if (image == null) return;

        int sampleStep = ItemGlintRelightConfigManager.get().outlineSampleSize();
        for (int y = 0; y < image.getHeight(); y += sampleStep) {
            for (int x = 0; x < image.getWidth(); x += sampleStep) {
                int argb = image.getPixel(x, y);
                if ((argb >>> 24) < 24) continue;
                state.materialColors.merge(quantize(applyTint(argb, tint)), 1, Integer::sum);
            }
        }
    }

    private static void captureTextureColors(TextureAtlasSprite sprite, int tint) {
        InteractionHand hand = submittingHand != null ? submittingHand : recordingHand;
        if (hand == null || itemSubmissionDepth <= 0
                || ItemGlintRelightConfigManager.get().outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return;
        }
        captureTextureColors(stateFor(hand), sprite, tint);
    }

    private static void captureFallbackTextureColors(Minecraft minecraft, CaptureState capture, InteractionHand hand) {
        if (ItemGlintRelightConfigManager.get().outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE
                || capture == null || capture.materialPalette != null || !capture.materialColors.isEmpty() || capture.item == null || capture.item.isEmpty()) {
            return;
        }

        ItemStackRenderState renderState = new ItemStackRenderState();
        ItemDisplayContext context = hand == null ? capture.itemContext : contextForHand(hand);
        if (context == null) return;
        if (minecraft.player != null) {
            minecraft.getItemModelResolver().updateForLiving(renderState, capture.item, context, minecraft.player);
        } else {
            minecraft.getItemModelResolver().updateForNonLiving(renderState, capture.item, context, null);
        }

        ItemStackRenderStateAccessor stateAccessor = (ItemStackRenderStateAccessor) (Object) renderState;
        ItemStackRenderState.LayerRenderState[] layers = stateAccessor.itemglintrelight$getLayers();
        int layerCount = layers == null ? 0 : Math.min(stateAccessor.itemglintrelight$getActiveLayerCount(), layers.length);
        for (int index = 0; index < layerCount; index++) {
            ItemStackLayerRenderStateAccessor layer = (ItemStackLayerRenderStateAccessor) (Object) layers[index];
            List<BakedQuad> quads = layer.itemglintrelight$getQuads();
            int[] tints = layer.itemglintrelight$getTintLayers();
            if (quads != null && !quads.isEmpty()) {
                captureTextureColors(capture, quads, tints);
            }
            if (capture.materialColors.isEmpty()) {
                captureTextureColors(capture, layer.itemglintrelight$getParticleIcon(), -1);
            }
        }
    }

    private static void capturePreviewTextureColors(ItemStackRenderState renderState, ItemGlintRelightConfig config) {
        capturePreviewTextureColors(PREVIEW, renderState, config);
    }

    private static void capturePreviewTextureColors(CaptureState capture, ItemStackRenderState renderState, ItemGlintRelightConfig config) {
        ItemStackRenderStateAccessor stateAccessor = (ItemStackRenderStateAccessor) (Object) renderState;
        ItemStackRenderState.LayerRenderState[] layers = stateAccessor.itemglintrelight$getLayers();
        int layerCount = layers == null ? 0 : Math.min(stateAccessor.itemglintrelight$getActiveLayerCount(), layers.length);
        for (int index = 0; index < layerCount; index++) {
            ItemStackLayerRenderStateAccessor layer = (ItemStackLayerRenderStateAccessor) (Object) layers[index];
            List<BakedQuad> quads = layer.itemglintrelight$getQuads();
            int[] tints = layer.itemglintrelight$getTintLayers();
            if (quads != null && !quads.isEmpty()) {
                for (BakedQuad quad : quads) {
                    if (quad != null) capturePreviewTextureColors(capture, quad.sprite(), resolveTint(quad, tints), config);
                }
            }
            if (capture.materialColors.isEmpty()) {
                capturePreviewTextureColors(capture, layer.itemglintrelight$getParticleIcon(), -1, config);
            }
        }
    }

    private static void capturePreviewTextureColors(TextureAtlasSprite sprite, int tint, ItemGlintRelightConfig config) {
        capturePreviewTextureColors(PREVIEW, sprite, tint, config);
    }

    private static void capturePreviewTextureColors(CaptureState capture, TextureAtlasSprite sprite, int tint, ItemGlintRelightConfig config) {
        if (sprite == null || sprite.contents() == null) return;
        NativeImage[] mipLevels = ((SpriteContentsAccessor) (Object) sprite.contents()).itemglintrelight$getByMipLevel();
        NativeImage image = mipLevels == null || mipLevels.length == 0 ? null : mipLevels[0];
        if (image == null) return;
        int sampleStep = config.outlineSampleSize();
        for (int y = 0; y < image.getHeight(); y += sampleStep) {
            for (int x = 0; x < image.getWidth(); x += sampleStep) {
                int argb = image.getPixel(x, y);
                if ((argb >>> 24) >= 24) {
                    capture.materialColors.merge(quantize(applyTint(argb, tint)), 1, Integer::sum);
                }
            }
        }
    }

    private static void captureTextureColors(CaptureState state, List<BakedQuad> quads, int[] tints) {
        for (BakedQuad quad : quads) {
            if (quad != null) captureTextureColors(state, quad.sprite(), resolveTint(quad, tints));
        }
    }

    private static float[][] resolveMaterialPalette(CaptureState source, ItemGlintRelightConfig config) {
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return new float[][]{{1.0F, 1.0F, 1.0F}};
        }
        if (source.materialPalette != null) {
            return source.materialPalette;
        }
        if (source.materialColors.isEmpty()) {
            int fallback = config.outlinePrimaryColor();
            source.materialPalette = new float[][]{{((fallback >>> 16) & 255) / 255.0F, ((fallback >>> 8) & 255) / 255.0F, (fallback & 255) / 255.0F}};
            cacheMaterialPalette(source);
            return source.materialPalette;
        }
        int limit = Math.min(8, config.outlineSampleColorCount());
        List<Map.Entry<Integer, Integer>> colors = new ArrayList<>(source.materialColors.entrySet());
        colors.sort(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()));
        colors = new ArrayList<>(colors.subList(0, Math.min(limit, colors.size())));
        colors.sort(Comparator.<Map.Entry<Integer, Integer>>comparingDouble(entry -> colorHue(entry.getKey()))
                .thenComparingDouble(entry -> colorSaturation(entry.getKey()))
                .thenComparing(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder())));
        float[][] palette = new float[colors.size()][3];
        for (int index = 0; index < palette.length; index++) {
            int color = colors.get(index).getKey();
            palette[index] = new float[]{((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F, (color & 255) / 255.0F};
        }
        source.materialPalette = palette;
        cacheMaterialPalette(source);
        return palette;
    }

    private static String materialPaletteKey(ItemStack stack) {
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        return stack + "|" + config.outlineSampleSize() + "|" + config.outlineSampleColorCount();
    }

    private static void cacheMaterialPalette(CaptureState state) {
        if (state.materialPaletteKey != null && state.materialPalette != null) {
            MATERIAL_PALETTE_CACHE.put(state.materialPaletteKey, state.materialPalette);
        }
    }

    private static float colorHue(int color) {
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        float chroma = maximum - minimum;
        if (chroma < 0.0001F) return 0.0F;
        float hue = maximum == red ? (green - blue) / chroma : maximum == green ? 2.0F + (blue - red) / chroma : 4.0F + (red - green) / chroma;
        return (hue / 6.0F + 1.0F) % 1.0F;
    }

    private static float colorSaturation(int color) {
        float red = ((color >>> 16) & 255) / 255.0F;
        float green = ((color >>> 8) & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float maximum = Math.max(red, Math.max(green, blue));
        return maximum < 0.0001F ? 0.0F : (maximum - Math.min(red, Math.min(green, blue))) / maximum;
    }

    private static int resolveTint(BakedQuad quad, int[] tints) {
        if (!quad.isTinted() || tints == null || quad.tintIndex() < 0 || quad.tintIndex() >= tints.length) {
            return -1;
        }
        return tints[quad.tintIndex()] == -1 ? -1 : 0xFF000000 | tints[quad.tintIndex()];
    }

    private static int applyTint(int argb, int tint) {
        if (tint == -1 || (tint >>> 24) == 0) return argb;
        int alpha = argb >>> 24;
        int red = ((argb >>> 16) & 255) * ((tint >>> 16) & 255) / 255;
        int green = ((argb >>> 8) & 255) * ((tint >>> 8) & 255) / 255;
        int blue = (argb & 255) * (tint & 255) / 255;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int quantize(int argb) {
        return (argb & 0xFF000000)
                | (((argb >>> 16) & 0xE0) << 16)
                | (((argb >>> 8) & 0xE0) << 8)
                | (argb & 0xE0);
    }

    private static ScissorRect resolveCompositeScissor(RenderTarget target, CaptureState state) {
        if (!state.captured || state.externalSubmission || handProjectionMatrix == null || (state.itemPoseMatrix == null && state.modelViewMatrix == null)) {
            return null;
        }
        Matrix4f itemTransform = state.itemPoseMatrix == null ? state.modelViewMatrix : state.itemPoseMatrix;
        if (itemTransform == null) {
            return null;
        }
        Matrix4f transform = new Matrix4f(handProjectionMatrix).mul(itemTransform);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float[] bounds = new float[]{-1.1F, 0.5F, 2.1F};
        for (float x : bounds) {
            for (float y : bounds) {
                for (float z : bounds) {
                    Vector4f clip = transform.transform(new Vector4f(x, y, z, 1.0F));
                    if (!Float.isFinite(clip.w) || Math.abs(clip.w) < 0.0001F) {
                        continue;
                    }
                    float projectedX = clip.x / clip.w;
                    float projectedY = clip.y / clip.w;
                    if (!Float.isFinite(projectedX) || !Float.isFinite(projectedY)) {
                        continue;
                    }
                    minX = Math.min(minX, projectedX);
                    minY = Math.min(minY, projectedY);
                    maxX = Math.max(maxX, projectedX);
                    maxY = Math.max(maxY, projectedY);
                }
            }
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) {
            return null;
        }
        int x0 = (int) Math.floor((Math.max(-1.5F, Math.min(1.5F, minX)) * 0.5F + 0.5F) * target.width);
        int y0 = (int) Math.floor((Math.max(-1.5F, Math.min(1.5F, minY)) * 0.5F + 0.5F) * target.height);
        int x1 = (int) Math.ceil((Math.max(-1.5F, Math.min(1.5F, maxX)) * 0.5F + 0.5F) * target.width);
        int y1 = (int) Math.ceil((Math.max(-1.5F, Math.min(1.5F, maxY)) * 0.5F + 0.5F) * target.height);
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        int padding = (int) Math.ceil(resolveCompositePadding(target, config));
        return ScissorRect.fromCorners(x0 - padding, y0 - padding, x1 + padding, y1 + padding, target.width, target.height);
    }

    private static float resolveCompositePadding(RenderTarget target, ItemGlintRelightConfig config) {
        float paddingRadius = resolveOutlineRadius(target, config);
        if (config.outlineBloomEnabled()) {
            paddingRadius += resolveBloomRadius(target, config) * config.outlineBloomBlurPasses();
        }
        return paddingRadius + 8.0F;
    }

    private static float resolveOutlineRadius(RenderTarget target, ItemGlintRelightConfig config) {
        float width = config.outlineWidth();
        if (config.outlineRenderMode() == OutlineRenderMode.CUBIC) {
            width *= 1.2F;
        }
        return width * Math.max(1, target.height) / REFERENCE_RENDER_HEIGHT;
    }

    private static float resolveBloomRadius(RenderTarget target, ItemGlintRelightConfig config) {
        return config.outlineBloomRadius() * 3.0F * Math.max(1, target.height) / REFERENCE_RENDER_HEIGHT;
    }

    private record ScissorRect(int x, int y, int width, int height) {
        private static ScissorRect fromCorners(int x0, int y0, int x1, int y1, int maxWidth, int maxHeight) {
            int minimumX = Math.max(0, Math.min(maxWidth, Math.min(x0, x1)));
            int minimumY = Math.max(0, Math.min(maxHeight, Math.min(y0, y1)));
            int maximumX = Math.max(0, Math.min(maxWidth, Math.max(x0, x1)));
            int maximumY = Math.max(0, Math.min(maxHeight, Math.max(y0, y1)));
            return maximumX > minimumX && maximumY > minimumY
                    ? new ScissorRect(minimumX, minimumY, maximumX - minimumX, maximumY - minimumY) : null;
        }

        private static ScissorRect union(ScissorRect first, ScissorRect second) {
            if (first == null) return second;
            if (second == null) return first;
            int x0 = Math.min(first.x, second.x);
            int y0 = Math.min(first.y, second.y);
            int x1 = Math.max(first.x + first.width, second.x + second.width);
            int y1 = Math.max(first.y + first.height, second.y + second.height);
            return new ScissorRect(x0, y0, x1 - x0, y1 - y0);
        }
    }

    private static void putColor(ByteBuffer buffer, int color, float opacity) {
        put(buffer, ((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F,
                (color & 255) / 255.0F, ((color >>> 24) & 255) / 255.0F * opacity);
    }

    private static void put(ByteBuffer buffer, float x, float y, float z, float w) {
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    private static String renderPolicy(Minecraft minecraft) {
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        return "player=" + (minecraft != null && minecraft.player != null)
                + " level=" + (minecraft != null && minecraft.level != null)
                + " firstPerson=" + (minecraft != null && minecraft.options.getCameraType().isFirstPerson())
                + " outline=" + config.outlineEnabled();
    }

    private static void diagnostic(String outcome) {
        long now = System.currentTimeMillis();
        if (now < nextDiagnosticMillis) {
            return;
        }
        nextDiagnosticMillis = now + 1000L;
        ItemGlintRelight.LOGGER.info(
                "[HeldOutline] frame={} {} wrapped={} handPasses={} main={} off={}",
                frameNumber, outcome, storageWrapped, handPasses, MAIN_HAND.describe(), OFF_HAND.describe());
    }

    private static void thirdPersonDepthDiagnostic(String source, String stage, RenderTarget mainTarget, CaptureState capture) {
        long now = System.currentTimeMillis();
        if (now < nextThirdPersonDepthDiagnosticMillis) {
            return;
        }
        if ("preload".equals(stage)) {
            nextThirdPersonDepthDiagnosticMillis = now + 1000L;
        }
        ItemGlintRelight.LOGGER.info(
                "[ThirdPersonDepth] frame={} sequence={} source={} stage={} main={}x{} mainColor={} mainDepth={} mask={}x{} maskColor={} maskDepth={} "
                        + "depthTargetsSame={} copiedMainDepth=true overrideColor={} overrideDepth={} captured={} replayed={} nodes={} renderTypes={} ",
                frameNumber,
                thirdPersonCompositeSequence,
                source,
                stage,
                mainTarget.width,
                mainTarget.height,
                textureId(mainTarget.getColorTexture()),
                textureId(mainTarget.getDepthTexture()),
                sceneDepth.width,
                sceneDepth.height,
                textureId(sceneDepth.getColorTexture()),
                textureId(sceneDepth.getDepthTexture()),
                mainTarget.getDepthTexture() == sceneDepth.getDepthTexture(),
                textureId(RenderSystem.outputColorTextureOverride),
                textureId(RenderSystem.outputDepthTextureOverride),
                capture.captured,
                capture.replayed,
                capture.submittedItems,
                capture.renderTypes
        );
    }

    /**
     * Iris replaces the world render pipeline but still exposes the final scene depth through
     * the main target. Read all three depth surfaces after the replay once per second.
     * The resulting counts distinguish a bad depth copy, a replay that ignores its depth
     * attachment, and a later screen-space comparison error without changing render state.
     */
    private static void irisDepthProbe(RenderTarget mainTarget) {
        if (!HeldItemOutlineCompat.isIrisShaderPackRendering()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextIrisDepthProbeMillis) {
            return;
        }
        nextIrisDepthProbeMillis = now + 1000L;

        final float epsilon = 0.00015F;
        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        try {
            GL11C.glGetError(); // Clear an error left by unrelated render work before this probe.
            float[] mainDepth = readDepth(mainTarget);
            float[] snapshotDepth = readDepth(thirdPersonSceneDepth);
            float[] replayDepth = readDepth(sceneDepth);
            float[] replayMask = readAlpha(sceneDepth);
            if (mainDepth == null || snapshotDepth == null || replayDepth == null || replayMask == null) {
                ItemGlintRelight.LOGGER.warn(
                        "[IrisThirdPersonDepthProbe] frame={} seq={} readbackFailed main={} snapshot={} replayDepth={} replayMask={} irisNoHand={} irisNoHandStatus={} fboLookup={} glError=0x{}",
                        frameNumber, thirdPersonCompositeSequence, mainDepth != null, snapshotDepth != null,
                        replayDepth != null, replayMask != null, irisNoHandDepthReady, irisNoHandDepthStatus,
                        framebufferLookupFailure, Integer.toHexString(GL11C.glGetError()));
                return;
            }

            int sampled = mainDepth.length;
            int snapshotMismatch = 0;
            int maskPixels = 0;
            int visibleMaskPixels = 0;
            int occludedMaskPixels = 0;
            int replayDepthMismatch = 0;
            float maxSnapshotDelta = 0.0F;
            float maxReplayDelta = 0.0F;
            float nearestVisibleItemDepth = 1.0F;
            float nearestOccludedItemDepth = 1.0F;
            for (int index = 0; index < sampled; index++) {
                float main = mainDepth[index];
                float snapshot = snapshotDepth[index];
                float replay = replayDepth[index];
                float mask = replayMask[index];
                float snapshotDelta = Math.abs(main - snapshot);
                maxSnapshotDelta = Math.max(maxSnapshotDelta, snapshotDelta);
                if (snapshotDelta > epsilon) {
                    snapshotMismatch++;
                }
                if (mask <= 0.01F) {
                    continue;
                }
                maskPixels++;
                float replayDelta = Math.abs(main - replay);
                maxReplayDelta = Math.max(maxReplayDelta, replayDelta);
                if (replayDelta > epsilon) {
                    replayDepthMismatch++;
                }
                if (main + epsilon < replay) {
                    occludedMaskPixels++;
                    nearestOccludedItemDepth = Math.min(nearestOccludedItemDepth, replay);
                } else {
                    visibleMaskPixels++;
                    nearestVisibleItemDepth = Math.min(nearestVisibleItemDepth, replay);
                }
            }

            ItemGlintRelight.LOGGER.info(
                    "[IrisThirdPersonDepthProbe] frame={} seq={} target={}x{} samples={} copyMismatch={}/{} copyMaxDelta={} "
                            + "mask={} visibleMask={} occludedMask={} replayVsSceneMismatch={} replayMaxDelta={} "
                            + "nearestVisibleItem={} nearestOccludedItem={} boundBefore={} fb(main/snapshot/replay)={}/{}/{} "
                            + "status(main/snapshot/replay)=0x{}/0x{}/0x{} depth(main/snapshot/replay)={}/{}/{} glError=0x{}",
                    frameNumber, thirdPersonCompositeSequence, mainTarget.width, mainTarget.height, sampled,
                    snapshotMismatch, sampled, maxSnapshotDelta,
                    maskPixels, visibleMaskPixels, occludedMaskPixels, replayDepthMismatch, maxReplayDelta,
                    nearestVisibleItemDepth, nearestOccludedItemDepth,
                    previousFramebuffer,
                    framebufferId(mainTarget), framebufferId(thirdPersonSceneDepth), framebufferId(sceneDepth),
                    framebufferStatus(mainTarget), framebufferStatus(thirdPersonSceneDepth), framebufferStatus(sceneDepth),
                    depthDescription(mainTarget), depthDescription(thirdPersonSceneDepth), depthDescription(sceneDepth),
                    Integer.toHexString(GL11C.glGetError()));
        } catch (RuntimeException exception) {
            ItemGlintRelight.LOGGER.warn("[IrisThirdPersonDepthProbe] frame={} seq={} probeFailed", frameNumber,
                    thirdPersonCompositeSequence, exception);
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private static float[] readDepth(RenderTarget target) {
        int framebuffer = framebufferId(target);
        if (framebuffer <= 0) {
            return null;
        }
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
        int pixels = target.width * target.height;
        FloatBuffer data = MemoryUtil.memAllocFloat(pixels);
        try {
            GL11C.glReadPixels(0, 0, target.width, target.height, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, data);
            float[] result = new float[pixels];
            data.get(result);
            return result;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static float[] readAlpha(TextureTarget target) {
        int framebuffer = framebufferId(target);
        if (framebuffer <= 0) {
            return null;
        }
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
        int pixels = target.width * target.height;
        FloatBuffer data = MemoryUtil.memAllocFloat(pixels * 4);
        try {
            GL11C.glReadPixels(0, 0, target.width, target.height, GL11C.GL_RGBA, GL11C.GL_FLOAT, data);
            float[] result = new float[pixels];
            for (int index = 0; index < result.length; index++) {
                result[index] = data.get(index * 4 + 3);
            }
            return result;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static int framebufferStatus(RenderTarget target) {
        int framebuffer = framebufferId(target);
        if (framebuffer <= 0) {
            return 0;
        }
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
        return GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
    }

    private static int framebufferId(RenderTarget target) {
        if (target == null) {
            return 0;
        }
        try {
            // 1.21.11's OpenGL backend moved FBO lookup behind DirectStateAccess:
            // GlTextureView#getFbo(DirectStateAccess, GpuTexture). The old boolean-based
            // signature is absent. Use remapped mixin invokers rather than reflection: method
            // names become obfuscated in the production game JAR.
            if (RenderSystem.getDevice() instanceof GlDevice device
                    && target.getColorTextureView() instanceof GlTextureView colorView) {
                return ((GlTextureViewAccessor) (Object) colorView).itemglintrelight$getFbo(
                        ((GlDeviceAccessor) (Object) device).itemglintrelight$getDirectStateAccess(),
                        target.getDepthTexture());
            }
            framebufferLookupFailure = "unsupported backend=" + RenderSystem.getDevice().getClass().getName()
                    + " colorView=" + target.getColorTextureView().getClass().getName();
        } catch (RuntimeException exception) {
            framebufferLookupFailure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        }
        return 0;
    }

    private static boolean copyIrisNoHandDepth(TextureTarget destination, RenderTarget mainTarget) {
        Object texture = HeldItemOutlineCompat.getIrisNoHandDepthTexture();
        if (!(texture instanceof com.mojang.blaze3d.textures.GpuTexture gpuTexture)
                || !(gpuTexture instanceof com.mojang.blaze3d.opengl.GlTexture glTexture)) {
            irisNoHandDepthReady = false;
            irisNoHandDepthStatus = texture == null ? "texture_unavailable" : "unsupported_texture=" + texture.getClass().getName();
            return false;
        }
        try {
            int sourceTextureId = ((GlTextureAccessor) (Object) glTexture).itemglintrelight$getGlId();
            if (sourceTextureId <= 0) {
                irisNoHandDepthReady = false;
                irisNoHandDepthStatus = "invalid_texture_id=" + sourceTextureId;
                return false;
            }
            if (texture != irisNoHandDepthTexture || irisNoHandDepthFbo <= 0) {
                if (irisNoHandDepthFbo <= 0) irisNoHandDepthFbo = GL30C.glGenFramebuffers();
                irisNoHandDepthTexture = texture;
            }
            int destinationFbo = framebufferId(destination);
            if (destinationFbo <= 0) {
                irisNoHandDepthReady = false;
                irisNoHandDepthStatus = "destination_fbo_unavailable";
                return false;
            }
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, irisNoHandDepthFbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
                    GL11C.GL_TEXTURE_2D, sourceTextureId, 0);
            GL30C.glReadBuffer(GL11C.GL_NONE);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, destinationFbo);
            GL30C.glBlitFramebuffer(0, 0, mainTarget.width, mainTarget.height,
                    0, 0, destination.width, destination.height, GL30C.GL_DEPTH_BUFFER_BIT, GL11C.GL_NEAREST);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
            int error = GL11C.glGetError();
            irisNoHandDepthReady = error == GL11C.GL_NO_ERROR;
            irisNoHandDepthStatus = irisNoHandDepthReady
                    ? "copied texture=" + sourceTextureId + " fbo=" + irisNoHandDepthFbo
                    : "gl_error=0x" + Integer.toHexString(error);
            return irisNoHandDepthReady;
        } catch (RuntimeException exception) {
            framebufferLookupFailure = "irisNoHandCopy=" + exception.getClass().getSimpleName() + ":" + exception.getMessage();
            irisNoHandDepthReady = false;
            irisNoHandDepthStatus = framebufferLookupFailure;
            return false;
        }
    }

    private static String depthDescription(RenderTarget target) {
        if (target == null || target.getDepthTexture() == null) {
            return "null";
        }
        return target.getDepthTexture().getLabel() + ":" + target.getDepthTexture().getFormat()
                + ":usage=" + target.getDepthTexture().usage();
    }

    private static String textureId(Object texture) {
        return texture == null ? "null" : texture.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(texture));
    }

    private static void previewDiagnostic(String outcome) {
        long now = System.currentTimeMillis();
        if (now < nextPreviewDiagnosticMillis) {
            return;
        }
        nextPreviewDiagnosticMillis = now + 250L;
        ItemGlintRelight.LOGGER.info("[PreviewOutline] frame={} {}", frameNumber, outcome);
    }


    private static final class CaptureState {
        private SubmitNodeStorage storage;
        private FeatureRenderDispatcher dispatcher;
        private RenderBuffers buffers;
        private boolean captured;
        private boolean requested;
        private boolean replayed;
        private int submittedItems;
        private final Set<String> renderTypes = new LinkedHashSet<>();
        private final Map<Integer, Integer> materialColors = new LinkedHashMap<>();
        private float[][] materialPalette;
        private String materialPaletteKey;
        private String stack = "-";
        private ItemStack item;
        private ItemDisplayContext itemContext;
        private ItemGlintRelightConfig config;
        private String disabledStack = "-";
        private Matrix4f modelViewMatrix;
        private Matrix4f itemPoseMatrix;
        private GpuBufferSlice projectionMatrix;
        private ProjectionType projectionType;
        private boolean externalSubmission;

        private void reset() {
            captured = false;
            requested = false;
            replayed = false;
            submittedItems = 0;
            renderTypes.clear();
            materialColors.clear();
            materialPalette = null;
            materialPaletteKey = null;
            item = null;
            itemContext = null;
            config = null;
            stack = "-";
            disabledStack = "-";
            modelViewMatrix = null;
            itemPoseMatrix = null;
            projectionMatrix = null;
            projectionType = null;
            externalSubmission = false;
            if (storage != null) storage.clear();
        }

        private String describe() {
            return "{requested=" + requested + ", captured=" + captured + ", nodes=" + submittedItems
                    + ", replayed=" + replayed + ", stack=" + stack + ", disabled=" + disabledStack + "}";
        }
    }

    private record PreviewRequest(ItemStack item, float centerX, float centerY, float scale, float pitch, float yaw,
                                  ItemGlintRelightConfig config) { }

    private static final class MirroringCollector implements SubmitNodeCollector {
        private final SubmitNodeCollector delegate;

        private MirroringCollector(SubmitNodeCollector delegate) {
            this.delegate = delegate;
        }

        @Override public OrderedSubmitNodeCollector order(int order) { return new MirroringOrderedCollector(delegate.order(order), order); }
        @Override public void submitShadow(PoseStack pose, float radius, java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece> pieces) { delegate.submitShadow(pose, radius, pieces); }
        @Override public void submitNameTag(PoseStack pose, net.minecraft.world.phys.Vec3 pos, int light, net.minecraft.network.chat.Component text, boolean seeThrough, int background, double scale, net.minecraft.client.renderer.state.CameraRenderState camera) { delegate.submitNameTag(pose, pos, light, text, seeThrough, background, scale, camera); }
        @Override public void submitText(PoseStack pose, float x, float y, net.minecraft.util.FormattedCharSequence text, boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode, int color, int background, int light, int overlay) { delegate.submitText(pose, x, y, text, shadow, mode, color, background, light, overlay); }
        @Override public void submitFlame(PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState state, org.joml.Quaternionf rotation) { delegate.submitFlame(pose, state, rotation); }
        @Override public void submitLeash(PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState state) { delegate.submitLeash(pose, state); }
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState) { delegate.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); SubmitNodeCollection capture = captureCollection(0); if (capture != null) capture.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); }
        @Override public void submitModelPart(net.minecraft.client.model.geom.ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite, boolean outline, boolean translucent, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState, int color) { delegate.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); SubmitNodeCollection capture = captureCollection(0); if (capture != null) capture.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); }
        @Override public void submitBlock(PoseStack pose, net.minecraft.world.level.block.state.BlockState state, int light, int overlay, int color) { delegate.submitBlock(pose, state, light, overlay, color); }
        @Override public void submitMovingBlock(PoseStack pose, net.minecraft.client.renderer.block.MovingBlockRenderState state) { delegate.submitMovingBlock(pose, state); }
        @Override public void submitBlockModel(PoseStack pose, RenderType type, net.minecraft.client.renderer.block.model.BlockStateModel model, float red, float green, float blue, int light, int overlay, int color) { delegate.submitBlockModel(pose, type, model, red, green, blue, light, overlay, color); }
        @Override public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int color, int[] tints, java.util.List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil) { delegate.submitItem(pose, context, light, overlay, color, tints, quads, type, foil); mirrorDirectItem(0, pose, context, light, overlay, color, tints, quads, type, foil); }
        @Override public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) { delegate.submitCustomGeometry(pose, type, renderer); SubmitNodeCollection capture = captureCollection(0); if (capture != null) capture.submitCustomGeometry(pose, type, renderer); }
        @Override public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) { delegate.submitParticleGroup(renderer); }
    }

    private static final class MirroringOrderedCollector implements OrderedSubmitNodeCollector {
        private final OrderedSubmitNodeCollector delegate;
        private final int order;

        private MirroringOrderedCollector(OrderedSubmitNodeCollector delegate, int order) {
            this.delegate = delegate;
            this.order = order;
        }

        @Override public void submitShadow(PoseStack pose, float radius, java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece> pieces) { delegate.submitShadow(pose, radius, pieces); }
        @Override public void submitNameTag(PoseStack pose, net.minecraft.world.phys.Vec3 pos, int light, net.minecraft.network.chat.Component text, boolean seeThrough, int background, double scale, net.minecraft.client.renderer.state.CameraRenderState camera) { delegate.submitNameTag(pose, pos, light, text, seeThrough, background, scale, camera); }
        @Override public void submitText(PoseStack pose, float x, float y, net.minecraft.util.FormattedCharSequence text, boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode, int color, int background, int light, int overlay) { delegate.submitText(pose, x, y, text, shadow, mode, color, background, light, overlay); }
        @Override public void submitFlame(PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState state, org.joml.Quaternionf rotation) { delegate.submitFlame(pose, state, rotation); }
        @Override public void submitLeash(PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState state) { delegate.submitLeash(pose, state); }
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState) { delegate.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); SubmitNodeCollection capture = captureCollection(order); if (capture != null) capture.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); }
        @Override public void submitModelPart(net.minecraft.client.model.geom.ModelPart part, PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite, boolean outline, boolean translucent, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState, int color) { delegate.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); SubmitNodeCollection capture = captureCollection(order); if (capture != null) capture.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); }
        @Override public void submitBlock(PoseStack pose, net.minecraft.world.level.block.state.BlockState state, int light, int overlay, int color) { delegate.submitBlock(pose, state, light, overlay, color); }
        @Override public void submitMovingBlock(PoseStack pose, net.minecraft.client.renderer.block.MovingBlockRenderState state) { delegate.submitMovingBlock(pose, state); }
        @Override public void submitBlockModel(PoseStack pose, RenderType type, net.minecraft.client.renderer.block.model.BlockStateModel model, float red, float green, float blue, int light, int overlay, int color) { delegate.submitBlockModel(pose, type, model, red, green, blue, light, overlay, color); }
        @Override public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int color, int[] tints, java.util.List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil) { delegate.submitItem(pose, context, light, overlay, color, tints, quads, type, foil); mirrorDirectItem(order, pose, context, light, overlay, color, tints, quads, type, foil); }
        @Override public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) { delegate.submitCustomGeometry(pose, type, renderer); SubmitNodeCollection capture = captureCollection(order); if (capture != null) capture.submitCustomGeometry(pose, type, renderer); }
        @Override public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) { delegate.submitParticleGroup(renderer); }
    }

    private static void mirrorDirectItem(int order, PoseStack pose, ItemDisplayContext context, int light, int overlay, int color, int[] tints,
                                         java.util.List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil) {
        if (isThirdPersonContext(context) && recordingThirdPerson != null) {
            recordingThirdPerson.renderTypes.add(String.valueOf(type));
            recordingThirdPerson.itemPoseMatrix = new Matrix4f(pose.last().pose());
            if (recordingThirdPerson.config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
                captureTextureColors(recordingThirdPerson, quads, tints);
            }
            SubmitNodeCollection capture = captureCollection(order, null);
            if (capture != null) capture.submitItem(pose, context, light, overlay, color, tints, quads, type, foil);
            return;
        }
        if (!isFirstPersonContext(context) && externalSubmissionDepth <= 0) return;
        InteractionHand hand = handForContext(context);
        if (hand == null) hand = recordingHand;
        if (hand != null) {
            CaptureState state = stateFor(hand);
            state.itemPoseMatrix = new Matrix4f(pose.last().pose());
        }
        if (ItemGlintRelightConfigManager.get().outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE && hand != null) {
            captureTextureColors(stateFor(hand), quads, tints);
        }
        SubmitNodeCollection capture = captureCollection(order, hand);
        if (capture != null) capture.submitItem(pose, context, light, overlay, color, tints, quads, type, foil);
    }

    private static final class MirroringStorage extends SubmitNodeStorage {
        private final SubmitNodeStorage delegate;

        private MirroringStorage(SubmitNodeStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public SubmitNodeCollection order(int order) {
            return new MirroringCollection(delegate.order(order), order);
        }

        @Override public void clear() { delegate.clear(); }
        @Override public void endFrame() { delegate.endFrame(); }
        @Override public it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap<SubmitNodeCollection> getSubmitsPerOrder() { return delegate.getSubmitsPerOrder(); }
        @Override public void submitShadow(com.mojang.blaze3d.vertex.PoseStack pose, float radius, java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece> pieces) { delegate.submitShadow(pose, radius, pieces); }
        @Override public void submitNameTag(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.phys.Vec3 pos, int light, net.minecraft.network.chat.Component text, boolean seeThrough, int background, double scale, net.minecraft.client.renderer.state.CameraRenderState camera) { delegate.submitNameTag(pose, pos, light, text, seeThrough, background, scale, camera); }
        @Override public void submitText(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, net.minecraft.util.FormattedCharSequence text, boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode, int color, int background, int light, int overlay) { delegate.submitText(pose, x, y, text, shadow, mode, color, background, light, overlay); }
        @Override public void submitFlame(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState state, org.joml.Quaternionf rotation) { delegate.submitFlame(pose, state, rotation); }
        @Override public void submitLeash(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState state) { delegate.submitLeash(pose, state); }
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState) { delegate.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); captureTextureColors(sprite, color); SubmitNodeCollection capture = captureCollection(0); if (capture != null) capture.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); }
        @Override public void submitModelPart(net.minecraft.client.model.geom.ModelPart part, com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite, boolean outline, boolean translucent, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState, int color) { delegate.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); captureTextureColors(sprite, color); SubmitNodeCollection capture = captureCollection(0); if (capture != null) capture.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); }
        @Override public void submitBlock(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.level.block.state.BlockState state, int light, int overlay, int color) { delegate.submitBlock(pose, state, light, overlay, color); }
        @Override public void submitMovingBlock(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.block.MovingBlockRenderState state) { delegate.submitMovingBlock(pose, state); }
        @Override public void submitBlockModel(com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, net.minecraft.client.renderer.block.model.BlockStateModel model, float red, float green, float blue, int light, int overlay, int color) { delegate.submitBlockModel(pose, type, model, red, green, blue, light, overlay, color); }
        @Override public void submitCustomGeometry(com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer renderer) { delegate.submitCustomGeometry(pose, type, renderer); SubmitNodeCollection capture = captureCollection(0); if (capture != null) capture.submitCustomGeometry(pose, type, renderer); }
        @Override public void submitParticleGroup(net.minecraft.client.renderer.SubmitNodeCollector.ParticleGroupRenderer renderer) { delegate.submitParticleGroup(renderer); }

        @Override
        public void submitItem(com.mojang.blaze3d.vertex.PoseStack pose, ItemDisplayContext context, int light, int overlay, int color, int[] tints,
                               java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads, RenderType type,
                               net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foil) {
            delegate.submitItem(pose, context, light, overlay, color, tints, quads, type, foil);
            MirroringCollection.mirrorItem(0, pose, context, light, overlay, color, tints, quads, type, foil);
        }
    }

    private static final class MirroringCollection extends SubmitNodeCollection {
        private final SubmitNodeCollection delegate;
        private final int order;

        private MirroringCollection(SubmitNodeCollection delegate, int order) {
            super(new SubmitNodeStorage());
            this.delegate = delegate;
            this.order = order;
        }

        @Override public void submitShadow(com.mojang.blaze3d.vertex.PoseStack pose, float radius, java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState.ShadowPiece> pieces) { delegate.submitShadow(pose, radius, pieces); }
        @Override public void submitNameTag(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.phys.Vec3 pos, int light, net.minecraft.network.chat.Component text, boolean seeThrough, int background, double scale, net.minecraft.client.renderer.state.CameraRenderState camera) { delegate.submitNameTag(pose, pos, light, text, seeThrough, background, scale, camera); }
        @Override public void submitText(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, net.minecraft.util.FormattedCharSequence text, boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode, int color, int background, int light, int overlay) { delegate.submitText(pose, x, y, text, shadow, mode, color, background, light, overlay); }
        @Override public void submitFlame(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState state, org.joml.Quaternionf rotation) { delegate.submitFlame(pose, state, rotation); }
        @Override public void submitLeash(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.entity.state.EntityRenderState.LeashState state) { delegate.submitLeash(pose, state); }
        @Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, int light, int overlay, int color, TextureAtlasSprite sprite, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState) { delegate.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); captureTextureColors(sprite, color); SubmitNodeCollection capture = captureCollection(order); if (capture != null) capture.submitModel(model, state, pose, type, light, overlay, color, sprite, crumbling, overlayState); }
        @Override public void submitModelPart(net.minecraft.client.model.geom.ModelPart part, com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, int light, int overlay, TextureAtlasSprite sprite, boolean outline, boolean translucent, int crumbling, ModelFeatureRenderer.CrumblingOverlay overlayState, int color) { delegate.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); captureTextureColors(sprite, color); SubmitNodeCollection capture = captureCollection(order); if (capture != null) capture.submitModelPart(part, pose, type, light, overlay, sprite, outline, translucent, crumbling, overlayState, color); }
        @Override public void submitBlock(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.level.block.state.BlockState state, int light, int overlay, int color) { delegate.submitBlock(pose, state, light, overlay, color); }
        @Override public void submitMovingBlock(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.block.MovingBlockRenderState state) { delegate.submitMovingBlock(pose, state); }
        @Override public void submitBlockModel(com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, net.minecraft.client.renderer.block.model.BlockStateModel model, float red, float green, float blue, int light, int overlay, int color) { delegate.submitBlockModel(pose, type, model, red, green, blue, light, overlay, color); }
        @Override public void submitCustomGeometry(com.mojang.blaze3d.vertex.PoseStack pose, RenderType type, net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer renderer) { delegate.submitCustomGeometry(pose, type, renderer); SubmitNodeCollection capture = captureCollection(order); if (capture != null) capture.submitCustomGeometry(pose, type, renderer); }
        @Override public void submitParticleGroup(net.minecraft.client.renderer.SubmitNodeCollector.ParticleGroupRenderer renderer) { delegate.submitParticleGroup(renderer); }

        @Override
        public void submitItem(com.mojang.blaze3d.vertex.PoseStack pose, ItemDisplayContext context, int light, int overlay, int color, int[] tints,
                               java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads, RenderType type,
                               net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foil) {
            delegate.submitItem(pose, context, light, overlay, color, tints, quads, type, foil);
            mirrorItem(order, pose, context, light, overlay, color, tints, quads, type, foil);
        }

        private static void mirrorItem(int order, com.mojang.blaze3d.vertex.PoseStack pose, ItemDisplayContext context, int light, int overlay, int color,
                                       int[] tints, java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads, RenderType type,
                                       net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foil) {
            mirrorDirectItem(order, pose, context, light, overlay, color, tints, quads, type, foil);
        }
    }

    private interface BufferWriter { void write(ByteBuffer buffer); }

    private static final class UniformRing {
        private final String label;
        private final int bytes;
        private final int blocks;
        private com.mojang.blaze3d.buffers.GpuBuffer buffer;
        private int cursor;

        private UniformRing(String label, int bytes, int blocks) { this.label = label; this.bytes = bytes; this.blocks = blocks; }
        private void beginFrame() { cursor = 0; }
        private GpuBufferSlice write(BufferWriter writer) {
            if (buffer == null) buffer = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, bytes * blocks);
            int offset = (cursor++ % blocks) * bytes;
            GpuBufferSlice slice = new GpuBufferSlice(buffer, offset, bytes);
            try (GpuBuffer.MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(slice, false, true)) {
                ByteBuffer data = mapped.data(); data.position(0); writer.write(data); data.position(0);
            }
            return slice;
        }
    }
}
