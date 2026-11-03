package celia.adwadg.itemglintrelight.client.render;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.joml.Vector4f;
import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.client.ui.UiPainting;
import celia.adwadg.itemglintrelight.config.ColorScrollMode;
import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.OutlineRenderMode;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import celia.adwadg.itemglintrelight.mixin.client.ItemInHandRendererInvoker;
import celia.adwadg.itemglintrelight.mixin.client.ItemRendererAccessor;
import celia.adwadg.itemglintrelight.mixin.client.SpriteContentsAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First-person item outline for 1.21.1.
 *
 * <p>1.21.1 has no {@code SubmitNodeCollector}: the hand pass writes straight into a
 * {@link MultiBufferSource} and flushes at the end of {@code renderHandsWithItems}. This class
 * therefore records what the hand pass drew (item, map, arm, plus the pose and matrices they were
 * drawn with) and, once the hand pass has returned, re-renders exactly that into an off-screen
 * mask. Full-screen passes then turn the mask coverage into the bloom halo and the stroke on the
 * main target.
 *
 * <p>Re-rendering instead of mirroring the vertex stream is deliberate: the vanilla draw calls are
 * deterministic for a fixed pose/light, and the real buffer keeps full ownership of its
 * {@code BufferBuilder}s.
 */
public final class HeldItemOutlineRenderer {
    private static final float REFERENCE_RENDER_HEIGHT = 1080.0F;
    private static final String SHADER_NAME = "itemglintrelight_held_outline";

    private static final HandCapture MAIN_HAND = new HandCapture();
    private static final HandCapture OFF_HAND = new HandCapture();
    private static final HandCapture PREVIEW = new HandCapture();

    private static InteractionHand recordingHand;
    private static boolean recordingEnabled;
    private static boolean implicitWindowLogged;
    // Vertex mirror of the hand pass. It is independent of which renderer draws the hand, so it also
    // covers mods (Punchy) that replace ItemInHandRenderer#renderArmWithItem/renderItem entirely.
    private static boolean handGeometryActive;
    /** Hand whose item is being drawn right now; routes mirrored vertices to that hand's capture. */
    private static InteractionHand currentMirrorHand;
    // Only the local player's first-person pass may feed the mask: level rendering draws every
    // entity's held item through the same renderers and must never be mirrored.
    private static boolean handPassActive;
    private static boolean armGeometryActive;
    private static Matrix4f handGeometryProjection;
    private static Matrix4f handGeometryModelView;

    private static TextureTarget maskTarget;
    private static TextureTarget armTarget;
    private static TextureTarget sceneDepthTarget;
    private static TextureTarget bloomFirst;
    private static TextureTarget bloomSecond;
    private static TextureTarget previewArmTarget;
    private static TextureTarget previewBloomFirst;
    private static TextureTarget previewBloomSecond;

    private static MultiBufferSource.BufferSource maskSource;

    private static final Program OUTLINE = new Program(SHADER_NAME);
    private static final Program BLOOM = new Program("itemglintrelight_bloom_composite");
    private static final Program BLUR = new Program("itemglintrelight_bloom_blur");
    private static final Program GUI_COMPOSITE = new Program("itemglintrelight_gui_composite");
    private static final Program THIRD_PERSON_BLOOM_SEED = new Program("itemglintrelight_third_person_bloom_seed");
    private static final Program THIRD_PERSON_BLOOM_COMPOSITE = new Program("itemglintrelight_third_person_bloom_composite");

    private HeldItemOutlineRenderer() { }

    /**
     * Starts mirroring the hand pass and returns the wrapping buffer source.
     *
     * <p>Called from {@code ItemInHandRenderer#renderHandsWithItems} and from Punchy's
     * {@code renderFirstPerson}, whichever runs first; the second call returns the already wrapped
     * source untouched. Arm geometry is excluded by the arm flags so the mask stays the item only.
     */
    public static MultiBufferSource wrapHandGeometry(MultiBufferSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        HandCapture hand = currentMirrorHand == null ? null : captureFor(currentMirrorHand);
        if (source == null || hand == null || !handPassActive || !shouldRender(minecraft)) {
            return source;
        }
        hand.geometryProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        hand.geometryModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        if (handGeometryProjection == null || handGeometryModelView == null) {
            handGeometryProjection = new Matrix4f(hand.geometryProjection);
            handGeometryModelView = new Matrix4f(hand.geometryModelView);
        }
        return new ThirdPersonBufferSource(source, () -> hand.geometry);
    }

    /**
     * Wraps exactly one arm draw's buffer source.
     *
     * <p>There is deliberately no global "arm is being drawn" flag: a flag left behind by a missing
     * or renamed arm entry point would route the item into the arm batch and empty the mask, which is
     * how the previous attempt made the whole stroke disappear. The hand comes from the arm argument
     * of the very call being wrapped.
     */
    public static MultiBufferSource wrapArmGeometry(MultiBufferSource source, HumanoidArm arm) {
        Minecraft minecraft = Minecraft.getInstance();
        if (source == null || arm == null || minecraft.player == null
                || !handPassActive || !shouldRender(minecraft)) {
            return source;
        }
        InteractionHand hand = minecraft.player.getMainArm() == arm
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        HandCapture capture = captureFor(hand);
        if (capture.armProjection == null || capture.armModelView == null) {
            capture.armProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            capture.armModelView = new Matrix4f(RenderSystem.getModelViewMatrix());
        }
        return new ThirdPersonBufferSource(source, () -> capture.armGeometry);
    }

    public static void beginHandPass() {
        handPassActive = true;
    }

    public static void endHandPass() {
        handPassActive = false;
        endHandGeometry();
    }

    public static void endHandGeometry() {
        handGeometryActive = false;
        armGeometryActive = false;
    }

    public static void beginArmGeometry() {
        armGeometryActive = true;
    }

    public static void endArmGeometry() {
        armGeometryActive = false;
    }

    /** Called at the head of every frame, before any hand is recorded. */
    public static void beginFrame() {
        handGeometryProjection = null;
        handGeometryModelView = null;
        currentMirrorHand = null;
        MAIN_HAND.reset();
        OFF_HAND.reset();
        recordingHand = null;
        recordingEnabled = false;
    }

    /** Called at the head of {@code ItemInHandRenderer#renderArmWithItem}. */
    public static void beginHand(InteractionHand hand, ItemStack stack) {
        recordingHand = null;
        recordingEnabled = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldRender(minecraft) || !isEnabled(hand, stack)) {
            return;
        }
        // A display rule may override every outline parameter for this item, or switch the outline
        // off for it. The global toggles above only decide whether the hand is considered at all.
        ItemGlintRelightConfig resolved = DisplayRuleManager.resolve(stack, ItemGlintRelightConfigManager.get());
        if (!resolved.outlineEnabled()) {
            return;
        }
        recordingHand = hand;
        recordingEnabled = true;
        currentMirrorHand = hand;
        HandCapture capture = captureFor(hand);
        capture.config = resolved;
        // The stack that is actually being drawn: during an equip animation this is still the old
        // item, which is the one the outline and its palette must follow.
        capture.item = stack;
        capture.projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        capture.modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
    }

    /** Called at the return of {@code ItemInHandRenderer#renderArmWithItem}. */
    public static void endHand() {
        recordingHand = null;
        recordingEnabled = false;
        currentMirrorHand = null;
    }

    /** Called at the head of {@code ItemInHandRenderer#renderItem}. */
    public static void captureItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, int light) {
        if (stack == null || stack.isEmpty() || !isFirstPerson(context)) {
            return;
        }
        InteractionHand hand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        HandCapture capture = recordingHand == hand && recordingEnabled ? captureFor(hand) : null;
        if (capture == null) {
            // Mods such as Punchy draw the first-person hand through their own renderer and never call
            // renderArmWithItem, so no window is open. The item draw itself is enough to start one.
            beginHand(hand, stack);
            capture = recordingHand == hand && recordingEnabled ? captureFor(hand) : null;
            if (capture == null) {
                return;
            }
            if (!implicitWindowLogged) {
                implicitWindowLogged = true;
                ItemGlintRelight.LOGGER.info("[HeldOutline] implicit hand window started from the item draw (hand={})", hand);
            }
        }
        capture.item = stack;
        capture.itemContext = context;
        capture.itemLeftHand = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        capture.itemLight = light;
        capture.itemPose = snapshot(poseStack);
        if (capture.config != null && capture.config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
            sampleItemColors(capture, stack, context);
        }
    }

    /** Called at the head of {@code ItemInHandRenderer#renderPlayerArm}. */
    public static void captureArm(PoseStack poseStack, int light, float equipProgress, float swingProgress, HumanoidArm arm) {
        HandCapture capture = recording();
        if (capture == null) {
            return;
        }
        capture.arms.add(ArmCapture.armPose(snapshot(poseStack), arm, light, equipProgress, swingProgress));
    }

    /** Called at the head of {@code ItemInHandRenderer#renderMapHand} (both arms of a held map). */
    public static void captureMapHand(PoseStack poseStack, int light, HumanoidArm arm) {
        HandCapture capture = recording();
        if (capture == null) {
            return;
        }
        capture.arms.add(ArmCapture.mapHand(snapshot(poseStack), arm, light));
    }

    /** Called at the head of {@code ItemInHandRenderer#renderMap}. */
    public static void captureMap(PoseStack poseStack, int light, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        HandCapture capture = recording();
        if (capture == null) {
            // Same fallback as captureItem: resolve the hand from the live stacks when a mod owns the
            // hand pass and never opens the window.
            Minecraft minecraft = Minecraft.getInstance();
            InteractionHand hand = minecraft.player == null ? null
                    : stack == minecraft.player.getMainHandItem() ? InteractionHand.MAIN_HAND
                    : stack == minecraft.player.getOffhandItem() ? InteractionHand.OFF_HAND : null;
            if (hand == null) {
                return;
            }
            beginHand(hand, stack);
            capture = recordingHand == hand && recordingEnabled ? captureFor(hand) : null;
            if (capture == null) {
                return;
            }
        }
        capture.map = stack;
        capture.mapLight = light;
        capture.mapPose = snapshot(poseStack);
    }

    /**
     * Runs once the hand pass has returned. Re-renders the recorded hands into the mask targets and
     * draws the stroke onto the main target.
     */
    public static void composite(Minecraft minecraft) {
        try {
            if (!shouldRender(minecraft) || (!MAIN_HAND.hasGeometry() && !OFF_HAND.hasGeometry())) {
                return;
            }
            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            ensureTargets(mainTarget);
            ensureSource();
            if (mainTarget.useDepth) {
                sceneDepthTarget.copyDepthFrom(mainTarget);
            }
            // The scene depth is copied from the main target after the level has fully rendered, so
            // it already contains the hand and the arm; the per-pixel scene test can stay enabled and
            // no longer depends on the arm mask, which Iris' cancelled hand pass never fills.
            float occlusion = 0.0F;
            renderArmMask(minecraft);
            // One composite per hand: each hand owns its palette and its rule-resolved config, and
            // the off hand has to paint over the main hand exactly like the items themselves do.
            compositeHand(minecraft, mainTarget, occlusion, MAIN_HAND);
            compositeHand(minecraft, mainTarget, occlusion, OFF_HAND);
            debugOcclusion(minecraft, mainTarget, occlusion);
        } catch (RuntimeException exception) {
            ItemGlintRelight.LOGGER.error("[HeldOutline] composite failed", exception);
        } finally {
            MAIN_HAND.reset();
            OFF_HAND.reset();
            recordingHand = null;
            recordingEnabled = false;
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static long nextOcclusionDebugMillis;

    /**
     * One line per second that pins down the first-person occlusion path: what the shader is told,
     * how much each mask actually covers, and whether the arm batch projects sanely.
     */
    private static void debugOcclusion(Minecraft minecraft, RenderTarget mainTarget, float occlusion) {
        long now = System.currentTimeMillis();
        if (now < nextOcclusionDebugMillis) {
            return;
        }
        nextOcclusionDebugMillis = now + 1000L;
        ItemGlintRelight.LOGGER.info(
                "[IGR-OCC] iris={} mode={} maskPx={} armPx={} armDepth={}..{} sceneMin={} armArea={} geoMain={} geoOff={}",
                HeldItemOutlineCompat.isIrisShaderPackRendering(), occlusion, coverage(maskTarget),
                coverage(armTarget), depthMin(armTarget), depthMax(armTarget), depthMin(sceneDepthTarget),
                armArea(), !MAIN_HAND.geometry.isEmpty() || !MAIN_HAND.armGeometry.isEmpty(),
                !OFF_HAND.geometry.isEmpty() || !OFF_HAND.armGeometry.isEmpty());
    }

    private static int coverage(TextureTarget target) {
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        ByteBuffer buffer = BufferUtils.createByteBuffer(target.width * target.height * 4);
        GL11.glReadPixels(0, 0, target.width, target.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        int count = 0;
        for (int index = 3; index < buffer.capacity(); index += 4) {
            if ((buffer.get(index) & 0xFF) > 8) {
                count++;
            }
        }
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        return count;
    }

    private static String depthMin(TextureTarget target) {
        float value = extremeDepth(target, true);
        return Float.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.4f", value) : "n/a";
    }

    private static String depthMax(TextureTarget target) {
        float value = extremeDepth(target, false);
        return Float.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.4f", value) : "n/a";
    }

    private static float extremeDepth(TextureTarget target, boolean min) {
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(target.width * target.height);
        GL11.glReadPixels(0, 0, target.width, target.height, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buffer);
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        float value = min ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        for (int index = 0; index < buffer.capacity(); index++) {
            float sample = buffer.get(index);
            value = min ? Math.min(value, sample) : Math.max(value, sample);
        }
        return value;
    }

    /** Projected area (0..1 of the screen) of the mirrored arm batch, 0 when nothing was recorded. */
    private static String armArea() {
        return String.format(java.util.Locale.ROOT, "%.3f", armArea(MAIN_HAND)) + "/"
                + String.format(java.util.Locale.ROOT, "%.3f", armArea(OFF_HAND));
    }

    private static float armArea(HandCapture capture) {
        float[] bounds = capture.armGeometry.bounds();
        if (bounds == null) {
            return 0.0F;
        }
        Matrix4f projection = capture.armProjection != null ? capture.armProjection
                : capture.projection != null ? capture.projection : handGeometryProjection;
        Matrix4f modelView = capture.armModelView != null ? capture.armModelView
                : capture.modelView != null ? capture.modelView : handGeometryModelView;
        if (projection == null || modelView == null) {
            return -1.0F;
        }
        Matrix4f transform = new Matrix4f(projection).mul(modelView);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        Vector4f clip = new Vector4f();
        for (int index = 0; index < 8; index++) {
            clip.set((index & 1) == 0 ? bounds[0] : bounds[3],
                    (index & 2) == 0 ? bounds[1] : bounds[4],
                    (index & 4) == 0 ? bounds[2] : bounds[5], 1.0F).mul(transform);
            if (!Float.isFinite(clip.w) || clip.w == 0.0F) {
                return -1.0F;
            }
            float x = clip.x / clip.w;
            float y = clip.y / clip.w;
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                return -1.0F;
            }
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        float width = Math.min(Math.max(maxX, -1.0F), 1.0F) - Math.min(Math.max(minX, -1.0F), 1.0F);
        float height = Math.min(Math.max(maxY, -1.0F), 1.0F) - Math.min(Math.max(minY, -1.0F), 1.0F);
        return Math.max(0.0F, width / 2.0F) * Math.max(0.0F, height / 2.0F);
    }

    private static void compositeHand(Minecraft minecraft, RenderTarget mainTarget, float occlusion, HandCapture capture) {
        // The pose replay is the normal path. When no pose was recorded (a mod owns the hand pass),
        // fall back to the mirrored vertices, which carry the item exactly as it was drawn.
        boolean mirrored = !capture.geometry.isEmpty();
        if (!mirrored && capture.itemPose == null && capture.mapPose == null) {
            return;
        }
        ItemGlintRelightConfig config = capture.config;
        if (config == null && mirrored) {
            InteractionHand hand = capture == OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack live = hand == InteractionHand.MAIN_HAND
                    ? minecraft.player.getMainHandItem() : minecraft.player.getOffhandItem();
            if (live.isEmpty() || !isEnabled(hand, live)) {
                return;
            }
            config = DisplayRuleManager.resolve(live, ItemGlintRelightConfigManager.get());
            if (config == null || !config.outlineEnabled()) {
                return;
            }
            capture.item = live;
            capture.itemContext = hand == InteractionHand.MAIN_HAND
                    ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        }
        if (config == null) {
            config = ItemGlintRelightConfigManager.get();
        }
        if (config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE && capture.materialColors.isEmpty()) {
            ItemStack sample = capture.item != null && !capture.item.isEmpty()
                    ? capture.item : liveStackFor(minecraft, capture);
            if (sample != null && !sample.isEmpty()) {
                capture.item = sample;
                if (capture.itemContext == null) {
                    capture.itemContext = capture == OFF_HAND
                            ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                            : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
                }
                sampleItemColors(capture, sample, capture.itemContext);
            }
        }
        maskTarget.bindWrite(true);
        clear();
        if (mirrored) {
            replayMirroredGeometry(minecraft, capture);
        } else {
            replayCapture(minecraft, capture, () -> {
                if (capture.itemPose != null) {
                    minecraft.gameRenderer.itemInHandRenderer.renderItem(
                            minecraft.player, capture.item, capture.itemContext, capture.itemLeftHand,
                            poseOf(capture.itemPose), maskSource, capture.itemLight);
                }
                if (capture.mapPose != null) {
                    invoke(minecraft).itemglintrelight$renderMap(poseOf(capture.mapPose), maskSource, capture.mapLight, capture.map);
                }
            });
        }
        float[][] palette = resolvePalette(config, capture);
        float radius = resolveOutlineRadius(mainTarget, config);
        if (config.outlineBloomEnabled()) {
            drawBloom(minecraft, mainTarget, config, palette, occlusion, radius);
        }
        drawOutline(minecraft, mainTarget, config, palette, occlusion, radius);
    }

    /**
     * Draws the stroke into an offscreen preview texture that already contains the rendered item.
     * The caller owns the target binding and leaves the GUI scissor disabled, exactly like the
     * offscreen model pass itself.
     */
    public static void drawPreviewOutline(TextureTarget target, TextureTarget mask, ItemStack stack,
                                          ItemGlintRelightConfig config, float outlineScale) {
        Minecraft minecraft = Minecraft.getInstance();
        if (config == null || stack == null || stack.isEmpty() || !target.useDepth || mask == null) {
            return;
        }
        // Display rules resolve against the screen's draft, so rule edits and parameter edits both
        // show up in the preview without saving first.
        ItemGlintRelightConfig resolved = DisplayRuleManager.resolve(stack, config);
        if (resolved == null || !resolved.outlineEnabled()) {
            return;
        }
        ShaderInstance shader = OUTLINE.get(minecraft);
        if (shader == null) {
            return;
        }
        ensurePreviewArmTarget();
        // Same scaling as the reference preview (writePreviewUniforms): the stroke and the bloom are
        // scaled by 0.4 * outlineScale against the canvas height, where outlineScale is the preview's
        // zoom relative to 72. The preview is therefore a soft halo with a hairline stroke, not a
        // screen-proportioned band.
        float scale = 0.4F * Math.max(0.01F, outlineScale);
        float[][] palette = previewPalette(resolved, stack);
        float radius = resolveOutlineRadius(target, resolved) * scale;
        if (resolved.outlineBloomEnabled()) {
            drawPreviewBloom(target, mask, resolved, palette, radius, scale);
        }

        target.bindWrite(true);
        RenderSystem.setShader(() -> shader);
        shader.setSampler("MaskSampler", mask.getColorTextureId());
        shader.setSampler("ItemDepthSampler", mask.getDepthTextureId());
        // A 1x1 cleared depth keeps every sampler valid and the scene test off without sampling the
        // target that is currently being written.
        shader.setSampler("SceneDepthSampler", previewArmTarget.getDepthTextureId());
        shader.setSampler("ArmOccluderSampler", previewArmTarget.getColorTextureId());
        shader.setSampler("ArmOccluderDepthSampler", previewArmTarget.getDepthTextureId());
        writeUniforms(shader, target, resolved, palette, 1.0F, radius);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void drawPreviewBloom(TextureTarget target, TextureTarget mask, ItemGlintRelightConfig config,
                                         float[][] palette, float radius, float scale) {
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance blur = BLUR.get(minecraft);
        ShaderInstance composite = BLOOM.get(minecraft);
        if (blur == null || composite == null) {
            return;
        }
        ensurePreviewBloomTargets(target);
        int passes = Math.max(1, config.outlineBloomBlurPasses());
        float bloomRadius = resolveBloomRadius(target, config) * scale / (float) Math.sqrt(passes);
        int source = mask.getColorTextureId();
        for (int pass = 0; pass < passes; pass++) {
            source = blurPass(target, blur, previewBloomFirst, source, bloomRadius, 1.0F, 0.0F, config.outlineBloomQuality());
            source = blurPass(target, blur, previewBloomSecond, source, bloomRadius, 0.0F, 1.0F, config.outlineBloomQuality());
        }

        target.bindWrite(true);
        RenderSystem.setShader(() -> composite);
        composite.setSampler("BloomSampler", source);
        composite.setSampler("MaskSampler", mask.getColorTextureId());
        composite.setSampler("ItemDepthSampler", mask.getDepthTextureId());
        composite.setSampler("SceneDepthSampler", previewArmTarget.getDepthTextureId());
        composite.setSampler("ArmOccluderSampler", previewArmTarget.getColorTextureId());
        composite.setSampler("ArmOccluderDepthSampler", previewArmTarget.getDepthTextureId());
        writeUniforms(composite, target, config, palette, 1.0F, radius);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void ensurePreviewBloomTargets(TextureTarget target) {
        if (previewBloomFirst == null) {
            previewBloomFirst = new TextureTarget(target.width, target.height, false, false);
            previewBloomSecond = new TextureTarget(target.width, target.height, false, false);
            previewBloomFirst.setFilterMode(GL11.GL_LINEAR);
            previewBloomSecond.setFilterMode(GL11.GL_LINEAR);
        } else if (previewBloomFirst.width != target.width || previewBloomFirst.height != target.height) {
            previewBloomFirst.resize(target.width, target.height, false);
            previewBloomSecond.resize(target.width, target.height, false);
        }
    }

    private static void ensurePreviewArmTarget() {
        if (previewArmTarget == null) {
            previewArmTarget = new TextureTarget(1, 1, true, false);
            previewArmTarget.bindWrite(true);
            clear();
        }
    }

    private static float[][] previewPalette(ItemGlintRelightConfig config, ItemStack stack) {
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return new float[][]{rgb(config.outlinePrimaryColor())};
        }
        PREVIEW.reset();
        PREVIEW.config = config;
        sampleItemColors(PREVIEW, stack, ItemDisplayContext.GUI);
        float[][] palette = resolvePalette(config, PREVIEW);
        PREVIEW.reset();
        return palette;
    }

    private static void renderArmMask(Minecraft minecraft) {
        armTarget.bindWrite(true);
        clear();
        replayArmGeometry(MAIN_HAND);
        replayArmGeometry(OFF_HAND);
        replayArms(minecraft, MAIN_HAND);
        replayArms(minecraft, OFF_HAND);
    }

    /** Replays the arm as it was actually drawn (Punchy owns its transforms). */
    private static void replayArmGeometry(HandCapture capture) {
        if (capture.armGeometry.isEmpty()) {
            return;
        }
        Matrix4f projection = capture.armProjection != null ? capture.armProjection : handGeometryProjection;
        Matrix4f modelView = capture.armModelView != null ? capture.armModelView : handGeometryModelView;
        if (projection == null || modelView == null) {
            return;
        }
        replayWith(projection, modelView, capture.armGeometry::draw);
    }

    private static void replayArms(Minecraft minecraft, HandCapture capture) {
        if (capture.arms.isEmpty()) {
            return;
        }
        replayCapture(minecraft, capture, () -> {
            for (ArmCapture arm : capture.arms) {
                if (arm.mapHand) {
                    invoke(minecraft).itemglintrelight$renderMapHand(poseOf(arm.pose), maskSource, arm.light, arm.hand);
                } else {
                    invoke(minecraft).itemglintrelight$renderPlayerArm(
                            poseOf(arm.pose), maskSource, arm.light, arm.equip, arm.swing, arm.hand);
                }
            }
        });
    }

    /**
     * Replays one captured draw with the matrices it was recorded with. The projection is saved by
     * hand because {@code RenderSystem} keeps a single backup slot that the draw calls themselves
     * may use.
     */
    /** Replays the mirrored hand geometry with the matrices the pass was drawn with. */
    private static ItemStack liveStackFor(Minecraft minecraft, HandCapture capture) {
        if (minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        return capture == OFF_HAND ? minecraft.player.getOffhandItem() : minecraft.player.getMainHandItem();
    }

    private static void replayMirroredGeometry(Minecraft minecraft, HandCapture capture) {
        Matrix4f projection = capture.geometryProjection != null ? capture.geometryProjection
                : capture.projection != null ? capture.projection : handGeometryProjection;
        Matrix4f modelView = capture.geometryModelView != null ? capture.geometryModelView
                : capture.modelView != null ? capture.modelView : handGeometryModelView;
        if (projection == null || modelView == null) {
            return;
        }
        replayWith(projection, modelView, capture.geometry::draw);
    }

    private static void replayCapture(Minecraft minecraft, HandCapture capture, Runnable body) {
        if (capture.projection == null || capture.modelView == null) {
            return;
        }
        replayWith(capture.projection, capture.modelView, body);
    }

    private static void replayWith(Matrix4f projection, Matrix4f modelView, Runnable body) {
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        RenderSystem.setProjectionMatrix(new Matrix4f(projection), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().set(modelView);
        RenderSystem.applyModelViewMatrix();
        try {
            body.run();
        } finally {
            maskSource.endBatch();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        }
    }

    private static void drawOutline(Minecraft minecraft, RenderTarget mainTarget, ItemGlintRelightConfig config,
                                    float[][] palette, float occlusion, float radius) {
        ShaderInstance shader = OUTLINE.get(minecraft);
        if (shader == null) {
            return;
        }
        mainTarget.bindWrite(true);
        RenderSystem.setShader(() -> shader);
        shader.setSampler("MaskSampler", maskTarget.getColorTextureId());
        shader.setSampler("ItemDepthSampler", maskTarget.getDepthTextureId());
        shader.setSampler("SceneDepthSampler", sceneDepthTarget.getDepthTextureId());
        shader.setSampler("ArmOccluderSampler", armTarget.getColorTextureId());
        shader.setSampler("ArmOccluderDepthSampler", armTarget.getDepthTextureId());
        writeUniforms(shader, mainTarget, config, palette, occlusion, radius);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * Blurs the mask into {@code bloomFirst}/{@code bloomSecond} and blends the halo onto the main
     * target before the stroke is drawn, mirroring the two passes 1.21.11 submits for first-person
     * bloom.
     */
    private static void drawBloom(Minecraft minecraft, RenderTarget mainTarget, ItemGlintRelightConfig config,
                                  float[][] palette, float occlusion, float radius) {
        ShaderInstance blur = BLUR.get(minecraft);
        ShaderInstance composite = BLOOM.get(minecraft);
        if (blur == null || composite == null) {
            return;
        }
        int passes = Math.max(1, config.outlineBloomBlurPasses());
        float bloomRadius = resolveBloomRadius(mainTarget, config) / (float) Math.sqrt(passes);
        int source = maskTarget.getColorTextureId();
        for (int pass = 0; pass < passes; pass++) {
            source = blurPass(mainTarget, blur, bloomFirst, source, bloomRadius, 1.0F, 0.0F, config.outlineBloomQuality());
            source = blurPass(mainTarget, blur, bloomSecond, source, bloomRadius, 0.0F, 1.0F, config.outlineBloomQuality());
        }

        mainTarget.bindWrite(true);
        RenderSystem.setShader(() -> composite);
        composite.setSampler("BloomSampler", source);
        composite.setSampler("MaskSampler", maskTarget.getColorTextureId());
        composite.setSampler("ItemDepthSampler", maskTarget.getDepthTextureId());
        composite.setSampler("SceneDepthSampler", sceneDepthTarget.getDepthTextureId());
        composite.setSampler("ArmOccluderSampler", armTarget.getColorTextureId());
        composite.setSampler("ArmOccluderDepthSampler", armTarget.getDepthTextureId());
        writeUniforms(composite, mainTarget, config, palette, occlusion, radius);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /** One directional pass of the separable blur; returns the destination colour texture. */
    private static int blurPass(RenderTarget mainTarget, ShaderInstance blur, TextureTarget destination, int source,
                                float radius, float directionX, float directionY, RenderQuality quality) {
        destination.bindWrite(true);
        RenderSystem.setShader(() -> blur);
        blur.setSampler("InputSampler", source);
        put(blur, "blur", directionX / mainTarget.width, directionY / mainTarget.height, radius, bloomSamples(quality));
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        return destination.getColorTextureId();
    }

    static void drawFullscreenQuad() {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(0.0F, 0.0F, 0.0F);
        buffer.addVertex(1.0F, 0.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(0.0F, 1.0F, 0.0F);
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void writeUniforms(ShaderInstance shader, RenderTarget target, ItemGlintRelightConfig config,
                                      float[][] palette, float occlusionMode, float radius) {
        putColor(shader, "primaryColor", config.outlinePrimaryColor(), config.outlineOpacity());
        putColor(shader, "secondaryColor", config.outlineSecondaryColor(), config.outlineOpacity());
        put(shader, "geometry", target.width, target.height, radius, config.outlineAlphaThreshold());
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000.0F;
        put(shader, "animation", colorMode(config.outlineColorMode()), time,
                config.outlineColorScrollSpeed() * 9.0F, config.outlineSoftness());
        put(shader, "effect", sampleCount(config.outlineQuality()), config.outlineGlowIntensity(), palette.length, config.outlineBloomIntensity());
        float directionRadians = (float) Math.toRadians(config.outlineColorScrollDirection());
        put(shader, "colorScroll", (float) Math.cos(directionRadians), -(float) Math.sin(directionRadians),
                config.outlineColorScrollInterval(), renderMode(config.outlineRenderMode()));
        float halfWidth = Math.max(1.0F, target.width * 0.25F);
        float halfHeight = Math.max(1.0F, target.height * 0.25F);
        put(shader, "scrollMode", scrollMode(config.outlineColorScrollMode()),
                target.width * 0.5F, target.height * 0.5F, Math.max(ellipsePathRadius(halfWidth, halfHeight), 1.0F));
        // scrollBounds.z: 0 = soft scene-depth test, 1 = no scene test. Passed in because the preview
        // has no scene depth, and Iris frames do not share the replay's depth space.
        put(shader, "scrollBounds", halfWidth, halfHeight, occlusionMode, 0.0F);
        // Only the bloom composite declares this one; the outline shader drops it.
        put(shader, "bloomParameters", config.outlineBloomSoftness(), 0.0F, 0.0F, 0.0F);
        put(shader, "clipRect", 0.0F, 0.0F, 0.0F, 0.0F);
        for (int index = 0; index < 8; index++) {
            float[] color = index < palette.length ? palette[index] : palette[0];
            put(shader, "palette" + index, color[0], color[1], color[2], 1.0F);
        }
    }

    private static void ensureTargets(RenderTarget mainTarget) {
        maskTarget = ensureTarget(maskTarget, mainTarget, true);
        armTarget = ensureTarget(armTarget, mainTarget, true);
        sceneDepthTarget = ensureTarget(sceneDepthTarget, mainTarget, true);
        bloomFirst = ensureTarget(bloomFirst, mainTarget, false);
        bloomSecond = ensureTarget(bloomSecond, mainTarget, false);
        maskTarget.setFilterMode(GL11.GL_LINEAR);
        armTarget.setFilterMode(GL11.GL_LINEAR);
        bloomFirst.setFilterMode(GL11.GL_LINEAR);
        bloomSecond.setFilterMode(GL11.GL_LINEAR);
    }

    private static TextureTarget ensureTarget(TextureTarget target, RenderTarget mainTarget, boolean depth) {
        if (target == null) {
            return new TextureTarget(mainTarget.width, mainTarget.height, depth, false);
        }
        if (target.width != mainTarget.width || target.height != mainTarget.height) {
            target.resize(mainTarget.width, mainTarget.height, false);
        }
        return target;
    }

    private static void ensureSource() {
        if (maskSource == null) {
            maskSource = MultiBufferSource.immediate(new ByteBufferBuilder(786432));
        }
    }

    private static void clear() {
        // glClear honours the write masks, and the passing render types leave them anywhere.
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clearDepth(1.0D);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static boolean shouldRender(Minecraft minecraft) {
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && minecraft.options.getCameraType().isFirstPerson()
                && config.outlineEnabled();
    }

    private static boolean isEnabled(InteractionHand hand, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemGlintRelightConfig config = ItemGlintRelightConfigManager.get();
        return hand == InteractionHand.MAIN_HAND ? config.outlineMainHand() : config.outlineOffHand();
    }

    private static boolean isFirstPerson(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private static HandCapture captureFor(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN_HAND : OFF_HAND;
    }

    private static HandCapture recording() {
        return recordingEnabled ? captureFor(recordingHand) : null;
    }

    private static PoseStack.Pose snapshot(PoseStack poseStack) {
        return poseStack.last().copy();
    }

    private static PoseStack poseOf(PoseStack.Pose pose) {
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(pose.pose());
        poseStack.last().normal().set(pose.normal());
        return poseStack;
    }

    private static ItemInHandRendererInvoker invoke(Minecraft minecraft) {
        return (ItemInHandRendererInvoker) (Object) minecraft.gameRenderer.itemInHandRenderer;
    }

    /**
     * Texture-sample mode needs colours from the item's own sprites. 1.21.1 has no submitted
     * {@code ItemStackRenderState}, so the palette is read from the same model and seed the item
     * renderer uses.
     */
    private static void sampleItemColors(HandCapture capture, ItemStack stack, ItemDisplayContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        int seed = minecraft.player == null ? 0 : minecraft.player.getId() + context.ordinal();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, seed);
        sampleQuads(capture, model.getQuads(null, null, RandomSource.create(42L)), stack);
        if (!capture.materialColors.isEmpty()) {
            return;
        }
        // builtin/entity models (trident, chest, bed, banner, shield, ...) bake no quads at all --
        // that is exactly how vanilla routes them to BlockEntityWithoutLevelRenderer. Fall back to
        // the inventory model (trident's flat icon) and finally to the model's particle sprite,
        // which is the only texture such a model declaration exposes.
        BakedModel icon = minecraft.getItemRenderer().getItemModelShaper().getItemModel(stack);
        if (icon != null) {
            sampleQuads(capture, icon.getQuads(null, null, RandomSource.create(42L)), stack);
        }
        if (capture.materialColors.isEmpty()) {
            int step = Math.max(1, capture.config.outlineSampleSize());
            sampleSprite(capture, model.getParticleIcon(), -1, step);
        }
    }

    private static void sampleQuads(HandCapture capture, List<BakedQuad> quads, ItemStack stack) {
        if (quads == null || quads.isEmpty()) {
            return;
        }
        ItemRendererAccessor renderer = (ItemRendererAccessor) Minecraft.getInstance().getItemRenderer();
        int step = Math.max(1, capture.config.outlineSampleSize());
        for (BakedQuad quad : quads) {
            if (quad == null) {
                continue;
            }
            int tint = quad.isTinted() ? renderer.itemglintrelight$getItemColors().getColor(stack, quad.getTintIndex()) : -1;
            sampleSprite(capture, quad.getSprite(), tint, step);
        }
    }

    private static void sampleSprite(HandCapture capture, TextureAtlasSprite sprite, int tint, int step) {
        if (sprite == null) {
            return;
        }
        SpriteContents contents = sprite.contents();
        if (contents == null) {
            return;
        }
        NativeImage[] mipLevels = ((SpriteContentsAccessor) (Object) contents).itemglintrelight$getByMipLevel();
        NativeImage image = mipLevels == null || mipLevels.length == 0 ? null : mipLevels[0];
        if (image == null) {
            return;
        }
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int argb = UiPainting.getPixel(image, x, y);
                if ((argb >>> 24) < 24) {
                    continue;
                }
                capture.materialColors.merge(quantize(applyTint(argb, tint)), 1, Integer::sum);
            }
        }
    }

    /** Up to eight dominant sprite colours, ordered by hue then saturation like the reference. */
    private static float[][] resolvePalette(ItemGlintRelightConfig config, HandCapture source) {
        float[] primary = rgb(config.outlinePrimaryColor());
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE
                || source == null || source.materialColors.isEmpty()) {
            return new float[][]{primary};
        }
        int limit = Math.min(8, Math.max(1, config.outlineSampleColorCount()));
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
        return palette;
    }

    private static int applyTint(int argb, int tint) {
        if (tint == -1 || (tint >>> 24) == 0) {
            return argb;
        }
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

    static ShaderInstance guiCompositeShader(Minecraft minecraft) {
        return GUI_COMPOSITE.get(minecraft);
    }

    static ShaderInstance bloomBlurShader(Minecraft minecraft) {
        return BLUR.get(minecraft);
    }

    static ShaderInstance outlineShader(Minecraft minecraft) {
        return OUTLINE.get(minecraft);
    }

    static ShaderInstance thirdPersonBloomSeedShader(Minecraft minecraft) {
        return THIRD_PERSON_BLOOM_SEED.get(minecraft);
    }

    static ShaderInstance thirdPersonBloomCompositeShader(Minecraft minecraft) {
        return THIRD_PERSON_BLOOM_COMPOSITE.get(minecraft);
    }

    /**
     * Uniform packing for the GUI-only composite shader.
     *
     * <p>Same layout as the reference {@code writePreviewUniforms}, with two GUI-specific values:
     * {@code scrollBounds.z} is pinned to 1.0 (scene occlusion off), and the colour-scroll scale moves
     * with the mask precision so a higher precision does not change the scroll speed in GUI units.
     */
    static void writeGuiUniforms(ShaderInstance shader, int width, int height, ItemGlintRelightConfig config,
                                 float[][] palette, float outlineScale, float colorScrollScale, boolean bloom) {
        putColor(shader, "primaryColor", config.outlinePrimaryColor(), config.outlineOpacity());
        putColor(shader, "secondaryColor", config.outlineSecondaryColor(), config.outlineOpacity());
        float radius = config.outlineWidth() * 0.4F * height / REFERENCE_RENDER_HEIGHT * Math.max(0.01F, outlineScale)
                * (config.outlineRenderMode() == OutlineRenderMode.CUBIC ? 1.2F : 1.0F);
        put(shader, "geometry", width, height, radius, config.outlineAlphaThreshold());
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000.0F;
        put(shader, "animation", colorMode(config.outlineColorMode()), time,
                config.outlineColorScrollSpeed() * 9.0F, config.outlineSoftness());
        float[][] colors = palette == null || palette.length == 0 ? new float[][]{rgb(config.outlinePrimaryColor())} : palette;
        put(shader, "effect", sampleCount(config.outlineQuality()), config.outlineGlowIntensity(), colors.length,
                config.outlineBloomIntensity());
        float directionRadians = (float) Math.toRadians(config.outlineColorScrollDirection());
        put(shader, "colorScroll", (float) Math.cos(directionRadians), -(float) Math.sin(directionRadians),
                config.outlineColorScrollInterval(), renderMode(config.outlineRenderMode()));
        float halfWidth = Math.max(1.0F, width * 0.25F);
        float halfHeight = Math.max(1.0F, height * 0.25F);
        put(shader, "scrollMode", scrollMode(config.outlineColorScrollMode()), width * 0.5F, height * 0.5F,
                Math.max(ellipsePathRadius(halfWidth, halfHeight), 1.0F));
        put(shader, "scrollBounds", halfWidth, halfHeight, 1.0F, Math.max(1.0F, colorScrollScale));
        for (int index = 0; index < 8; index++) {
            float[] color = config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE
                    ? colors[index % colors.length]
                    : ((index & 1) == 0 ? rgb(config.outlinePrimaryColor()) : rgb(config.outlineSecondaryColor()));
            put(shader, "palette" + index, color[0], color[1], color[2], 1.0F);
        }
        put(shader, "bloomParameters", config.outlineBloomSoftness(), bloom ? 1.0F : 0.0F, 0.0F, 0.0F);
        put(shader, "clipRect", 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /**
     * Texture-sample palette for a GUI slot. 1.21.1 has no submitted {@code ItemStackRenderState}, so
     * the colours are read from the same model (and the same seed) the GUI item itself is drawn with.
     */
    static float[][] resolveGuiMaterialPalette(ItemStack stack, Level level, LivingEntity entity, int seed,
                                               ItemGlintRelightConfig config) {
        if (config == null || stack == null || stack.isEmpty()
                || config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getItemRenderer().getModel(stack, level, entity, seed);
        HandCapture capture = new HandCapture();
        capture.config = config;
        sampleQuads(capture, model.getQuads(null, null, RandomSource.create(42L)), stack);
        if (capture.materialColors.isEmpty()) {
            // Builtin/entity models (trident, chest, bed, banner, shield, ...) bake no quads; fall back
            // to the inventory model and finally to the particle sprite, exactly like the hand path.
            BakedModel icon = minecraft.getItemRenderer().getItemModelShaper().getItemModel(stack);
            if (icon != null) {
                sampleQuads(capture, icon.getQuads(null, null, RandomSource.create(42L)), stack);
            }
        }
        if (capture.materialColors.isEmpty()) {
            sampleSprite(capture, model.getParticleIcon(), -1, Math.max(1, config.outlineSampleSize()));
        }
        return resolvePalette(config, capture);
    }

    private static float resolveOutlineRadius(RenderTarget target, ItemGlintRelightConfig config) {
        float width = config.outlineWidth();
        if (config.outlineRenderMode() == OutlineRenderMode.CUBIC) {
            width *= 1.2F;
        }
        return Math.max(1.0F, width * Math.max(1, target.height) / REFERENCE_RENDER_HEIGHT);
    }

    private static float resolveBloomRadius(RenderTarget target, ItemGlintRelightConfig config) {
        return config.outlineBloomRadius() * 3.0F * Math.max(1, target.height) / REFERENCE_RENDER_HEIGHT;
    }

    static float colorMode(OutlineColorMode mode) {
        return switch (mode) {
            case SINGLE -> 0.0F;
            case DUAL -> 1.0F;
            case RAINBOW -> 2.0F;
            case TEXTURE_SAMPLE -> 3.0F;
        };
    }

    static int sampleCount(RenderQuality quality) {
        return switch (quality) {
            case LOW -> 12;
            case MEDIUM -> 24;
            case HIGH -> 48;
        };
    }

    static int bloomSamples(RenderQuality quality) {
        return switch (quality) {
            case LOW -> 2;
            case MEDIUM -> 4;
            case HIGH -> 6;
        };
    }

    static float renderMode(OutlineRenderMode mode) {
        return mode == OutlineRenderMode.CUBIC ? 1.0F : 0.0F;
    }

    static float scrollMode(ColorScrollMode mode) {
        return mode == ColorScrollMode.OUTLINE ? 1.0F : 0.0F;
    }

    static float ellipsePathRadius(float halfWidth, float halfHeight) {
        float sum = halfWidth + halfHeight;
        return (3.0F * sum - (float) Math.sqrt(Math.max(0.0F, (3.0F * halfWidth + halfHeight) * (halfWidth + 3.0F * halfHeight)))) / 2.0F;
    }

    static float[] rgb(int color) {
        return new float[]{((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F, (color & 255) / 255.0F};
    }

    static void putColor(ShaderInstance shader, String name, int color, float opacity) {
        put(shader, name, ((color >>> 16) & 255) / 255.0F, ((color >>> 8) & 255) / 255.0F,
                (color & 255) / 255.0F, ((color >>> 24) & 255) / 255.0F * opacity);
    }

    static void put(ShaderInstance shader, String name, float x, float y, float z, float w) {
        Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z, w);
        }
    }

    /** Lazily compiled shader program; a load failure is reported once and disables that pass. */
    private static final class Program {
        private final String name;
        private ShaderInstance shader;
        private boolean failed;

        private Program(String name) {
            this.name = name;
        }

        private ShaderInstance get(Minecraft minecraft) {
            if (this.shader == null && !this.failed) {
                try {
                    this.shader = new ShaderInstance(minecraft.getResourceManager(), this.name, DefaultVertexFormat.POSITION);
                } catch (Exception exception) {
                    this.failed = true;
                    ItemGlintRelight.LOGGER.error("[HeldOutline] shader {} failed to load", this.name, exception);
                }
            }
            return this.shader;
        }
    }

    private static final class HandCapture {
        private final Map<Integer, Integer> materialColors = new LinkedHashMap<>();
        // Vertices of whatever was actually drawn for this hand this frame. Kept per hand so the two
        // hands never share a silhouette or a palette.
        private final ThirdPersonBufferSource.MaskSource geometry = new ThirdPersonBufferSource.MaskSource();
        private final ThirdPersonBufferSource.MaskSource armGeometry = new ThirdPersonBufferSource.MaskSource();
        private Matrix4f armProjection;
        private Matrix4f armModelView;
        // Matrices of the pass that produced the mirrored item geometry. With a shader pack Iris draws
        // some hands after renderItemInHand with its own projection, so these are captured per draw.
        private Matrix4f geometryProjection;
        private Matrix4f geometryModelView;
        private ItemGlintRelightConfig config;
        private Matrix4f projection;
        private Matrix4f modelView;

        private ItemStack item;
        private ItemDisplayContext itemContext;
        private boolean itemLeftHand;
        private int itemLight;
        private PoseStack.Pose itemPose;

        private ItemStack map;
        private int mapLight;
        private PoseStack.Pose mapPose;

        private final List<ArmCapture> arms = new ArrayList<>(2);

        private boolean hasGeometry() {
            return this.itemPose != null || this.mapPose != null || !this.arms.isEmpty()
                    || !this.geometry.isEmpty();
        }

        private void reset() {
            this.materialColors.clear();
            this.config = null;
            this.projection = null;
            this.modelView = null;
            this.item = null;
            this.itemContext = null;
            this.itemLight = 0;
            this.itemPose = null;
            this.map = null;
            this.mapLight = 0;
            this.mapPose = null;
            this.arms.clear();
            this.geometry.clear();
            this.armGeometry.clear();
            this.armProjection = null;
            this.armModelView = null;
            this.geometryProjection = null;
            this.geometryModelView = null;
        }
    }

    /** One recorded arm draw; {@code mapHand} selects which vanilla call replays it. */
    private static final class ArmCapture {
        private PoseStack.Pose pose;
        private HumanoidArm hand;
        private int light;
        private float equip;
        private float swing;
        private boolean mapHand;

        private static ArmCapture armPose(PoseStack.Pose pose, HumanoidArm hand, int light, float equip, float swing) {
            ArmCapture capture = new ArmCapture();
            capture.pose = pose;
            capture.hand = hand;
            capture.light = light;
            capture.equip = equip;
            capture.swing = swing;
            return capture;
        }

        private static ArmCapture mapHand(PoseStack.Pose pose, HumanoidArm hand, int light) {
            ArmCapture capture = new ArmCapture();
            capture.pose = pose;
            capture.hand = hand;
            capture.light = light;
            capture.mapHand = true;
            return capture;
        }
    }
}
