package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.OutlineRenderMode;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;


import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Third-person item / armor / armor-stand outline for 1.21.1.
 *
 * <p>1.21.11 mirrors {@code SubmitNodeCollector} submissions into a private storage and replays them
 * after the level. 1.21.1 draws entities immediately, so the mirror happens at vertex level
 * ({@link ThirdPersonBufferSource}) and the captured batches are replayed into an off-screen mask
 * once {@code GameRenderer#renderLevel} returns, before the GUI.
 *
 * <p>The pipeline per capture: clear the mask region, copy the opaque scene depth into the mask
 * depth (so the replay is occluded by walls), replay the captured vertices with the captured
 * projection and camera matrix, grow the bloom from the mask, then composite the stroke onto the
 * main target inside the capture's projected screen rectangle.
 */
public final class ThirdPersonOutlineManager {
    private static final float REFERENCE_RENDER_HEIGHT = 1080.0F;
    private static final float BLOOM_REFERENCE_DISTANCE = 4.0F;
    private static final int MAX_CAPTURES = 16;
    private static final int MAX_POOL = 16;
    private static final int PALETTE_CACHE_LIMIT = 128;
    private static final float[][] UNIT_PALETTE = {{1.0F, 1.0F, 1.0F}};

    private static final Deque<Capture> QUEUE = new ArrayDeque<>();
    private static final Deque<Capture> POOL = new ArrayDeque<>();
    private static final Map<celia.adwadg.itemglintrelight.client.render.ItemStackKey, float[][]> PALETTES =
            new LinkedHashMap<>(32, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ItemStackKey, float[][]> eldest) {
                    return size() > PALETTE_CACHE_LIMIT;
                }
            };

    private static Capture recording;
    private static int captureDepth;
    private static boolean armorGroup;
    private static ItemGlintRelightConfig armorConfig;
    private static String armorGroupAsset;

    private static TextureTarget mask;
    private static TextureTarget sceneDepth;
    private static TextureTarget bloomSeed;
    private static TextureTarget bloomFirst;
    private static TextureTarget bloomSecond;
    private static TextureTarget armStandIn;
    private static boolean depthReady;
    private static int irisDepthFbo = -1;
    private static int failures;
    private static boolean announced;

    private ThirdPersonOutlineManager() {
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    public static void beginFrame() {
        depthReady = false;
        recording = null;
        captureDepth = 0;
        armorGroup = false;
        armorConfig = null;
        while (!QUEUE.isEmpty()) {
            release(QUEUE.poll());
        }
    }

    /** Wraps the buffer source handed to an entity layer so vertices can be mirrored. */
    public static MultiBufferSource route(MultiBufferSource original) {
        return original == null ? null : new ThirdPersonBufferSource(original, ThirdPersonOutlineManager::currentMaskSource);
    }

    static ThirdPersonBufferSource.MaskSource currentMaskSource() {
        Capture capture = recording;
        return capture == null ? null : capture.mask;
    }

    public static void beginItem(LivingEntity entity, ItemStack stack, PoseStack pose) {
        if (captureDepth++ > 0) {
            return;
        }
        if (recording != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!canCapture(minecraft) || entity == null || stack == null || stack.isEmpty() || pose == null) {
            return;
        }
        boolean armorStand = entity instanceof ArmorStand;
        ItemGlintRelightConfig config = resolve(entity, stack, armorStand, false);
        if (config == null) {
            return;
        }
        if (!levelProjectionIsPerspective()) {
            return;
        }
        if (armorStand && QUEUE.size() >= MAX_CAPTURES) {
            return;
        }
        Capture capture = acquire();
        capture.begin(entity, stack, config, pose, false, armorStand);
        recording = capture;
    }

    public static void endItem() {
        if (--captureDepth > 0) {
            return;
        }
        captureDepth = 0;
        closeCapture();
    }

    public static void beginArmorPiece(LivingEntity entity, ItemStack stack, EquipmentSlot slot, PoseStack pose) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!canCapture(minecraft) || entity == null || stack == null || stack.isEmpty() || pose == null) {
            return;
        }
        boolean armorStand = entity instanceof ArmorStand;
        ItemGlintRelightConfig config = resolve(entity, stack, armorStand, true);
        if (config == null) {
            return;
        }
        // Pieces that resolve to the same config share one capture, exactly like the reference. In
        // texture-sampling mode the palette comes from the piece's own armor texture, so a mixed set
        // has to split at the material boundary (the leggings use a different texture as well).
        String armorAsset = config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE
                ? armorAssetKey(stack, slot) : null;
        if (armorGroup && recording != null && recording.armor && recording.entity == entity
                && armorConfig == config && java.util.Objects.equals(armorAsset, armorGroupAsset)) {
            return;
        }
        if (armorGroup) {
            closeArmorGroup();
        }
        if (!levelProjectionIsPerspective()) {
            return;
        }
        if (armorStand && QUEUE.size() >= MAX_CAPTURES) {
            return;
        }
        Capture capture = acquire();
        capture.begin(entity, stack, config, pose, true, armorStand);
        recording = capture;
        armorGroup = true;
        armorConfig = config;
        armorGroupAsset = armorAsset;
    }

    public static void endArmorGroup() {
        closeArmorGroup();
    }

    private static void closeArmorGroup() {
        if (!armorGroup) {
            return;
        }
        armorGroup = false;
        armorConfig = null;
        armorGroupAsset = null;
        closeCapture();
    }

    private static void closeCapture() {
        Capture capture = recording;
        recording = null;
        if (capture == null) {
            return;
        }
        if (capture.mask.isEmpty()) {
            release(capture);
            return;
        }
        if (QUEUE.size() >= MAX_CAPTURES) {
            release(capture);
            return;
        }
        QUEUE.addLast(capture);
    }

    // ---------------------------------------------------------------------------------------------
    // Scene depth snapshot
    // ---------------------------------------------------------------------------------------------

    /**
     * Called after the level's main buffer flush. Everything the outline must be hidden behind is
     * already in the main target's depth, while translucent terrain, clouds and weather are not.
     */
    public static void afterMainBatch(MultiBufferSource.BufferSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || source == null || QUEUE.isEmpty() || depthReady) {
            return;
        }
        if (source != minecraft.renderBuffers().bufferSource()) {
            return;
        }
        // With a shader pack the main target's depth is not the frame depth; Iris' depthtex2 is
        // copied at composite time instead (it is only complete after Iris' pre-hand copy).
        if (HeldItemOutlineCompat.isIrisShaderPackRendering()) {
            return;
        }
        try {
            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            ensureTargets(mainTarget);
            sceneDepth.copyDepthFrom(mainTarget);
            // Sodium (and Iris' own frame setup) can flush the shared buffer source before the level
            // has written depth; accepting that snapshot would disable occlusion for the whole frame.
            depthReady = sceneDepthHasContent();
        } catch (RuntimeException exception) {
            fail("depth snapshot", exception);
        } finally {
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Composite
    // ---------------------------------------------------------------------------------------------

    /** Runs after {@code GameRenderer#renderLevel}, before the GUI. */
    public static void compositeAfterLevel() {
        if (QUEUE.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            QUEUE.clear();
            return;
        }
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        try {
            ensureTargets(mainTarget);
            // renderLevel clears the depth buffer before the hand pass, so the finished depth is
            // empty by now; the scene snapshot must come from the main batch flush instead.
            if (!depthReady && HeldItemOutlineCompat.isIrisShaderPackRendering()) {
                depthReady = copyIrisSceneDepth(mainTarget);
            }
            if (!depthReady) {
                snapshotCurrentDepth(mainTarget);
            }
            if (!announced) {
                announced = true;
                ItemGlintRelight.LOGGER.info("[ThirdPerson] outline active: captures={} depthReady={}",
                        QUEUE.size(), depthReady);
            }
            GuiGlState state = GuiGlState.capture();
            try {
                while (!QUEUE.isEmpty()) {
                    Capture capture = QUEUE.poll();
                    try {
                        compositeOne(minecraft, mainTarget, capture);
                    } finally {
                        release(capture);
                    }
                }
            } finally {
                mainTarget.bindWrite(true);
                state.restore();
            }
        } catch (RuntimeException exception) {
            fail("composite", exception);
            QUEUE.clear();
        }
    }

    /**
     * Copies Iris' {@code depthtex2} (no-hand depth) into the scene snapshot target.
     *
     * <p>Iris keeps the frame depth in its own targets, so the main target has no usable depth while
     * a shader pack is active. {@code depthtex2} is copied right before the hand pass, which makes it
     * this frame's scene depth at composite time.
     */
    /**
     * Last point in the frame where the main target still holds the level's depth.
     *
     * <p>Iris' batched-entity mixin replaces {@code RenderBuffers#bufferSource()} for the whole level
     * (with or without a shader pack) with a source that overrides {@code endBatch}, so the
     * {@code BufferSource} snapshot hook never runs there. The translucent layer boundary is a plain
     * {@code LevelRenderer} method that always runs, and by then opaque terrain and flushed entities
     * are already in the depth buffer.
     */
    public static void beforeTranslucentLayer() {
        if (QUEUE.isEmpty() || depthReady || HeldItemOutlineCompat.isIrisShaderPackRendering()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        try {
            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            ensureTargets(mainTarget);
            sceneDepth.copyDepthFrom(mainTarget);
            depthReady = true;
        } catch (RuntimeException exception) {
            fail("translucent snapshot", exception);
        }
    }

    private static boolean copyIrisSceneDepth(RenderTarget mainTarget) {
        int textureId = HeldItemOutlineCompat.getIrisNoHandDepthTextureId();
        if (textureId <= 0) {
            return false;
        }
        try {
            if (irisDepthFbo <= 0) {
                irisDepthFbo = GlStateManager.glGenFramebuffers();
            }
            GlStateManager._bindTexture(textureId);
            int sourceWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int sourceHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            GlStateManager._bindTexture(0);
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                return false;
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, irisDepthFbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, textureId, 0);
            GL30.glReadBuffer(GL11.GL_NONE);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneDepth.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, sourceWidth, sourceHeight,
                    0, 0, sceneDepth.width, sceneDepth.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainTarget.frameBufferId);
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } catch (RuntimeException exception) {
            fail("iris depth copy", exception);
            return false;
        }
    }

    /** @return true when the copied depth contains any geometry (a sky-only view counts as empty). */
    private static boolean sceneDepthHasContent() {
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sceneDepth.frameBufferId);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(sceneDepth.width);
            boolean found = false;
            for (int row = 1; row <= 3 && !found; row++) {
                int y = Math.max(0, Math.min(sceneDepth.height - 1, sceneDepth.height * row / 4));
                GL11.glReadPixels(0, y, sceneDepth.width, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, buffer);
                for (int index = 0; index < buffer.capacity(); index++) {
                    if (buffer.get(index) < 0.999F) {
                        found = true;
                        break;
                    }
                }
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER,
                    Minecraft.getInstance().getMainRenderTarget().frameBufferId);
            return found;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static void snapshotCurrentDepth(RenderTarget mainTarget) {
        // RenderTarget#bindRead() binds the colour texture, not GL_READ_FRAMEBUFFER, so the depth copy
        // has to go through the engine helper that owns both bindings.
        sceneDepth.copyDepthFrom(mainTarget);
        depthReady = true;
    }

    private static void compositeOne(Minecraft minecraft, RenderTarget mainTarget, Capture capture) {
        ItemGlintRelightConfig config = thirdPersonConfig(capture);
        ScissorRect region = scissorFor(mainTarget, capture, config);
        float[][] palette = paletteFor(capture, config);

        // The whole mask is cleared per capture, and the capture rectangle is enforced by the
        // shaders' clipRect, so the off-screen passes must not inherit a stale GUI scissor.
        RenderSystem.disableScissor();
        mask.bindWrite(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clearDepth(1.0D);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        // Seed the mask depth from the scene snapshot so the replay is occluded by walls. With a
        // shader pack the snapshot is Iris' depthtex2, which already contains the item itself:
        // seeding would let the replay compete with it and punch frame-to-frame holes into the
        // coverage, so the mask stays unseeded and the shaders perform the per-pixel rejection.
        if (!HeldItemOutlineCompat.isIrisShaderPackRendering()) {
            mask.copyDepthFrom(sceneDepth);
        }

        replay(capture);

        if (config.outlineBloomEnabled()) {
            drawBloom(minecraft, mainTarget, capture, config, region);
        }
        drawOutline(minecraft, mainTarget, capture, config, palette, region);
    }

    private static void replay(Capture capture) {
        mask.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(capture.projection), VertexSorting.DISTANCE_TO_ORIGIN);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(capture.modelView);
        RenderSystem.applyModelViewMatrix();
        try {
            capture.mask.draw();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void drawBloom(Minecraft minecraft, RenderTarget mainTarget, Capture capture,
                                  ItemGlintRelightConfig config, ScissorRect region) {
        ShaderInstance seedShader = HeldItemOutlineRenderer.thirdPersonBloomSeedShader(minecraft);
        ShaderInstance blurShader = HeldItemOutlineRenderer.bloomBlurShader(minecraft);
        ShaderInstance compositeShader = HeldItemOutlineRenderer.thirdPersonBloomCompositeShader(minecraft);
        if (seedShader == null || blurShader == null || compositeShader == null) {
            return;
        }
        int width = mainTarget.width;
        int height = mainTarget.height;

        bloomSeed.bindWrite(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setShader(() -> seedShader);
        seedShader.setSampler("MaskSampler", mask.getColorTextureId());
        seedShader.setSampler("ItemDepthSampler", mask.getDepthTextureId());
        seedShader.setSampler("SceneDepthSampler", sceneDepth.getDepthTextureId());
        HeldItemOutlineRenderer.drawFullscreenQuad();

        int passes = Math.max(1, config.outlineBloomBlurPasses());
        float radius = config.outlineBloomRadius() * 3.0F * height / REFERENCE_RENDER_HEIGHT
                / (float) Math.sqrt(passes);
        int source = bloomSeed.getColorTextureId();
        for (int pass = 0; pass < passes; pass++) {
            source = blurPass(width, height, bloomFirst, source, radius, 1.0F, 0.0F,
                    config.outlineBloomQuality(), blurShader);
            source = blurPass(width, height, bloomSecond, source, radius, 0.0F, 1.0F,
                    config.outlineBloomQuality(), blurShader);
        }

        mainTarget.bindWrite(true);
        RenderSystem.disableScissor();
        RenderSystem.setShader(() -> compositeShader);
        compositeShader.setSampler("BloomSampler", source);
        compositeShader.setSampler("SeedSampler", bloomSeed.getColorTextureId());
        compositeShader.setSampler("SceneDepthSampler", sceneDepth.getDepthTextureId());
        writeUniforms(compositeShader, mainTarget, config, UNIT_PALETTE, region);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        HeldItemOutlineRenderer.drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static int blurPass(int width, int height, TextureTarget destination, int source, float radius,
                                float directionX, float directionY, RenderQuality quality, ShaderInstance blur) {
        destination.bindWrite(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setShader(() -> blur);
        blur.setSampler("InputSampler", source);
        HeldItemOutlineRenderer.put(blur, "blur", directionX / width, directionY / height, radius,
                HeldItemOutlineRenderer.bloomSamples(quality));
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        HeldItemOutlineRenderer.drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        return destination.getColorTextureId();
    }

    private static void drawOutline(Minecraft minecraft, RenderTarget mainTarget, Capture capture,
                                    ItemGlintRelightConfig config, float[][] palette, ScissorRect region) {
        ShaderInstance shader = HeldItemOutlineRenderer.outlineShader(minecraft);
        if (shader == null) {
            return;
        }
        ensureArmStandIn();
        mainTarget.bindWrite(true);
        // The outline shader owns the capture rectangle (clipRect).
        RenderSystem.disableScissor();
        RenderSystem.setShader(() -> shader);
        shader.setSampler("MaskSampler", mask.getColorTextureId());
        shader.setSampler("ItemDepthSampler", mask.getDepthTextureId());
        shader.setSampler("SceneDepthSampler", sceneDepth.getDepthTextureId());
        shader.setSampler("ArmOccluderSampler", armStandIn.getColorTextureId());
        shader.setSampler("ArmOccluderDepthSampler", armStandIn.getDepthTextureId());
        writeUniforms(shader, mainTarget, config, palette, region);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        HeldItemOutlineRenderer.drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }







    // ---------------------------------------------------------------------------------------------
    // Uniforms
    // ---------------------------------------------------------------------------------------------

    private static void writeUniforms(ShaderInstance shader, RenderTarget target, ItemGlintRelightConfig config,
                                      float[][] palette, ScissorRect region) {
        HeldItemOutlineRenderer.putColor(shader, "primaryColor", config.outlinePrimaryColor(), config.outlineOpacity());
        HeldItemOutlineRenderer.putColor(shader, "secondaryColor", config.outlineSecondaryColor(), config.outlineOpacity());
        float radius = outlineRadius(target, config);
        HeldItemOutlineRenderer.put(shader, "geometry", target.width, target.height, radius, config.outlineAlphaThreshold());
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000.0F;
        HeldItemOutlineRenderer.put(shader, "animation", HeldItemOutlineRenderer.colorMode(config.outlineColorMode()),
                time, config.outlineColorScrollSpeed() * 9.0F, config.outlineSoftness());
        float[][] colors = palette == null || palette.length == 0 ? UNIT_PALETTE : palette;
        HeldItemOutlineRenderer.put(shader, "effect", HeldItemOutlineRenderer.sampleCount(config.outlineQuality()),
                config.outlineGlowIntensity(), colors.length, config.outlineBloomIntensity());
        float directionRadians = (float) Math.toRadians(config.outlineColorScrollDirection());
        HeldItemOutlineRenderer.put(shader, "colorScroll", (float) Math.cos(directionRadians),
                -(float) Math.sin(directionRadians), config.outlineColorScrollInterval(),
                HeldItemOutlineRenderer.renderMode(config.outlineRenderMode()));
        float padding = outlineRadius(target, config) + (config.outlineBloomEnabled()
                ? bloomRadius(target, config) * config.outlineBloomBlurPasses() : 0.0F) + 8.0F;
        float centerX = region == null ? target.width * 0.5F : region.x + region.width * 0.5F;
        float centerY = region == null ? target.height * 0.5F : region.y + region.height * 0.5F;
        float halfWidth = region == null ? target.width * 0.25F : Math.max(1.0F, region.width * 0.5F - padding);
        float halfHeight = region == null ? target.height * 0.25F : Math.max(1.0F, region.height * 0.5F - padding);
        float pathRadius = HeldItemOutlineRenderer.ellipsePathRadius(halfWidth, halfHeight);
        HeldItemOutlineRenderer.put(shader, "scrollMode", HeldItemOutlineRenderer.scrollMode(config.outlineColorScrollMode()),
                centerX, centerY, Math.max(pathRadius, 1.0F));
        // z = 2 marks the third-person path: scene occlusion on, binary visibility in the shaders.
        HeldItemOutlineRenderer.put(shader, "scrollBounds", halfWidth, halfHeight, 2.0F, 0.0F);
        for (int index = 0; index < 8; index++) {
            float[] color = config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE
                    ? colors[index % colors.length]
                    : ((index & 1) == 0 ? HeldItemOutlineRenderer.rgb(config.outlinePrimaryColor())
                        : HeldItemOutlineRenderer.rgb(config.outlineSecondaryColor()));
            HeldItemOutlineRenderer.put(shader, "palette" + index, color[0], color[1], color[2], 1.0F);
        }
        HeldItemOutlineRenderer.put(shader, "bloomParameters", config.outlineBloomSoftness(), 0.0F, 0.0F, 0.0F);
        HeldItemOutlineRenderer.put(shader, "clipRect",
                region == null ? 0.0F : region.x, region == null ? 0.0F : region.y,
                region == null ? 0.0F : region.width, region == null ? 0.0F : region.height);
    }

    private static float outlineRadius(RenderTarget target, ItemGlintRelightConfig config) {
        float width = config.outlineWidth();
        if (config.outlineRenderMode() == OutlineRenderMode.CUBIC) {
            width *= 1.2F;
        }
        return width * Math.max(1, target.height) / REFERENCE_RENDER_HEIGHT;
    }

    private static float bloomRadius(RenderTarget target, ItemGlintRelightConfig config) {
        return config.outlineBloomRadius() * 3.0F * Math.max(1, target.height) / REFERENCE_RENDER_HEIGHT;
    }

    /** Distance of the captured anchor in view space, or a non-positive value when unknown. */
    private static float viewDistance(Capture capture) {
        if (capture.modelView == null || capture.pose == null) {
            return -1.0F;
        }
        float distance = Math.abs(new Matrix4f(capture.modelView).mul(capture.pose).m32());
        return Float.isFinite(distance) ? distance : -1.0F;
    }

    private static float distanceScale(Capture capture) {
        float distance = viewDistance(capture);
        if (distance <= 0.001F) {
            return 1.0F;
        }
        if (capture.armorStand) {
            // Keep the stroke's world-space thickness constant for stands far from the camera.
            return BLOOM_REFERENCE_DISTANCE / distance;
        }
        return 1.0F;
    }

    private static float bloomDistanceScale(Capture capture) {
        float distance = viewDistance(capture);
        if (distance <= 0.001F) {
            return 1.0F;
        }
        return Math.max(0.35F, Math.min(3.0F, BLOOM_REFERENCE_DISTANCE / distance));
    }

    private static ItemGlintRelightConfig thirdPersonConfig(Capture capture) {
        ItemGlintRelightConfig config = capture.config.copy();
        config.setOutlineQuality(config.thirdPersonOutlineQuality());
        config.setOutlineWidth(config.outlineWidth() * 0.5F * distanceScale(capture));
        config.setOutlineBloomRadius(config.outlineBloomRadius() * 0.5F * bloomDistanceScale(capture));
        return config;
    }

    // ---------------------------------------------------------------------------------------------
    // Scissor
    // ---------------------------------------------------------------------------------------------

    /**
     * Composite region for one capture.
     *
     * <p>The mirrored vertices are camera-relative, so the region is their bounding box transformed by
     * the capture's projection and camera matrix, padded by the stroke and bloom radii.
     */
    private static ScissorRect scissorFor(RenderTarget target, Capture capture, ItemGlintRelightConfig config) {
        float[] bounds = capture.mask.bounds();
        if (bounds == null || capture.projection == null || capture.modelView == null) {
            return null;
        }
        // The replay installs exactly capture.projection and capture.modelView, so the same pair maps
        // the mirrored vertices to clip space; the composite region is that projection of the bounds.
        Matrix4f transform = new Matrix4f(capture.projection).mul(capture.modelView);
        float padding = outlineRadius(target, config) + (config.outlineBloomEnabled()
                ? bloomRadius(target, config) * config.outlineBloomBlurPasses() : 0.0F) + 8.0F;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        Vector4f clip = new Vector4f();
        for (int index = 0; index < 8; index++) {
            clip.set((index & 1) == 0 ? bounds[0] : bounds[3], (index & 2) == 0 ? bounds[1] : bounds[4],
                    (index & 4) == 0 ? bounds[2] : bounds[5], 1.0F).mul(transform);
            if (!Float.isFinite(clip.w) || clip.w <= 0.0001F) {
                return null;
            }
            float x = clip.x / clip.w;
            float y = clip.y / clip.w;
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                return null;
            }
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        int x0 = (int) Math.floor((Math.max(-1.5F, Math.min(1.5F, minX)) * 0.5F + 0.5F) * target.width);
        int y0 = (int) Math.floor((Math.max(-1.5F, Math.min(1.5F, minY)) * 0.5F + 0.5F) * target.height);
        int x1 = (int) Math.ceil((Math.max(-1.5F, Math.min(1.5F, maxX)) * 0.5F + 0.5F) * target.width);
        int y1 = (int) Math.ceil((Math.max(-1.5F, Math.min(1.5F, maxY)) * 0.5F + 0.5F) * target.height);
        int marginX = (int) ((x1 - x0) * 0.1F);
        int marginY = (int) ((y1 - y0) * 0.1F);
        int left = Math.max(0, x0 - (int) padding - marginX);
        int bottom = Math.max(0, y0 - (int) padding - marginY);
        int right = Math.min(target.width, x1 + (int) padding + marginX);
        int top = Math.min(target.height, y1 + (int) padding + marginY);
        if (right - left < 1 || top - bottom < 1) {
            return null;
        }
        return new ScissorRect(left, bottom, right - left, top - bottom);
    }


    private record ScissorRect(int x, int y, int width, int height) {
    }

    // ---------------------------------------------------------------------------------------------
    // Capture
    // ---------------------------------------------------------------------------------------------

    private static boolean canCapture(Minecraft minecraft) {
        return minecraft != null && minecraft.level != null && minecraft.player != null
                && !HeldItemOutlineCompat.isIrisShadowPass();
    }

    private static boolean thirdPersonCamera(Minecraft minecraft) {
        return minecraft.screen == null && !minecraft.options.getCameraType().isFirstPerson();
    }

    private static ItemGlintRelightConfig resolve(LivingEntity entity, ItemStack stack, boolean armorStand, boolean armor) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemGlintRelightConfig base = ItemGlintRelightConfigManager.get();
        if (!base.outlineEnabled() || !base.renderThirdPerson()) {
            return null;
        }
        if (!armorStand && entity != minecraft.player) {
            return null;
        }
        if (armorStand) {
            if (!base.outlineArmorStands()) {
                return null;
            }
            if (!(armor ? base.outlineArmor() : base.outlineThirdPerson())) {
                return null;
            }
        } else {
            if (!thirdPersonCamera(minecraft)) {
                return null;
            }
            if (!(armor ? base.outlineArmor() : base.outlineThirdPerson())) {
                return null;
            }
        }
        ItemGlintRelightConfig config = DisplayRuleManager.resolve(stack, base);
        if (config == null || !config.outlineEnabled() || !config.renderThirdPerson()) {
            return null;
        }
        return config;
    }

    /**
     * Key that names the armor textures a piece will actually be drawn with, matching what the 1.21.11
     * reference gets from the equipable asset id.
     *
     * <p>Pieces of one set share those textures and therefore share one capture, one silhouette and
     * one sampled palette; the leggings use the inner layer, so they legitimately split off.
     */
    private static String armorAssetKey(ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof ArmorItem armorItem)) {
            return null;
        }
        boolean inner = slot == EquipmentSlot.LEGS;
        StringBuilder key = new StringBuilder();
        for (ArmorMaterial.Layer layer : armorItem.getMaterial().value().layers()) {
            key.append(layer.texture(inner)).append('|');
        }
        if (key.length() == 0) {
            key.append(armorItem.getMaterial().unwrapKey()
                    .map(holder -> holder.location().toString())
                    .orElseGet(() -> armorItem.getMaterial().value().toString()));
        }
        return key.toString();
    }

    /**
     * Iris uploads its shadow pass orthographic matrix into the same projection state; a capture
     * taken with it would replay and clip against a rectangle unrelated to the item.
     */
    private static boolean levelProjectionIsPerspective() {
        return Math.abs(RenderSystem.getProjectionMatrix().m33()) < 0.5F;
    }

    private static Capture acquire() {
        Capture capture = POOL.pollFirst();
        return capture == null ? new Capture() : capture;
    }

    private static void release(Capture capture) {
        if (capture == null) {
            return;
        }
        capture.reset();
        if (POOL.size() < MAX_POOL) {
            POOL.addLast(capture);
        } else {
            capture.mask.close();
        }
    }

    private static float[][] paletteFor(Capture capture, ItemGlintRelightConfig config) {
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return UNIT_PALETTE;
        }
        ItemStackKey key = ItemStackKey.of(capture.stack);
        float[][] cached = PALETTES.get(key);
        if (cached != null) {
            return cached;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float[][] palette = HeldItemOutlineRenderer.resolveGuiMaterialPalette(capture.stack, minecraft.level,
                capture.entity, capture.entity.getId(), config);
        if (palette == null || palette.length == 0) {
            palette = UNIT_PALETTE;
        }
        PALETTES.put(key, palette);
        return palette;
    }

    private static void ensureTargets(RenderTarget mainTarget) {
        mask = ensure(mask, mainTarget, true);
        sceneDepth = ensure(sceneDepth, mainTarget, true);
        bloomSeed = ensure(bloomSeed, mainTarget, false);
        bloomFirst = ensure(bloomFirst, mainTarget, false);
        bloomSecond = ensure(bloomSecond, mainTarget, false);
    }

    private static TextureTarget ensure(TextureTarget target, RenderTarget mainTarget, boolean depth) {
        if (target == null) {
            target = new TextureTarget(mainTarget.width, mainTarget.height, depth, false);
            target.setFilterMode(GL11.GL_LINEAR);
            return target;
        }
        if (target.width != mainTarget.width || target.height != mainTarget.height) {
            target.resize(mainTarget.width, mainTarget.height, false);
        }
        return target;
    }

    private static void ensureArmStandIn() {
        if (armStandIn != null) {
            return;
        }
        armStandIn = new TextureTarget(1, 1, true, false);
        armStandIn.bindWrite(true);
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clearDepth(1.0D);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    private static void fail(String stage, RuntimeException exception) {
        if (failures++ < 3) {
            ItemGlintRelight.LOGGER.error("[ThirdPerson] " + stage + " failed", exception);
        }
    }

    /** One captured draw: the entity geometry, the matrices it was drawn with, and its screen box. */
    private static final class Capture {
        private LivingEntity entity;
        private ItemStack stack;
        private ItemGlintRelightConfig config;
        private boolean armor;
        private boolean armorStand;
        private Matrix4f projection;
        private Matrix4f modelView;
        private Matrix4f pose;
        private final ThirdPersonBufferSource.MaskSource mask = new ThirdPersonBufferSource.MaskSource();

        private void begin(LivingEntity entity, ItemStack stack, ItemGlintRelightConfig config, PoseStack pose,
                           boolean armor, boolean armorStand) {
            this.entity = entity;
            this.stack = stack;
            this.config = config;
            this.armor = armor;
            this.armorStand = armorStand;
            this.projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            this.modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            this.pose = new Matrix4f(pose.last().pose());
        }

        private void reset() {
            this.mask.clear();
            this.entity = null;
            this.stack = null;
            this.config = null;
            this.armor = false;
            this.armorStand = false;
            this.projection = null;
            this.modelView = null;
            this.pose = null;
        }

    }
}
