package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import celia.adwadg.itemglintrelight.mixin.client.FeatureRenderDispatcherAccessor;
import celia.adwadg.itemglintrelight.mixin.client.ItemStackLayerRenderStateAccessor;
import celia.adwadg.itemglintrelight.mixin.client.ItemStackRenderStateAccessor;
import celia.adwadg.itemglintrelight.mixin.client.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class HeldItemOutlineRenderer {
    private static final int OUTLINE_UNIFORM_BYTES = 256;
    private static final float REFERENCE_RENDER_HEIGHT = 1080.0F;
    private static final CaptureState MAIN_HAND = new CaptureState();
    private static final CaptureState OFF_HAND = new CaptureState();
    private static InteractionHand recordingHand;
    private static int itemSubmissionDepth;
    private static TextureTarget sceneDepth;
    private static UniformRing uniforms = new UniformRing("itemglintrelight_outline", OUTLINE_UNIFORM_BYTES, 8);
    private static long frameNumber;
    private static long nextDiagnosticMillis;
    private static boolean storageWrapped;
    private static int handPasses;

    private HeldItemOutlineRenderer() { }

    public static void beginFrame() {
        frameNumber++;
        recordingHand = null;
        itemSubmissionDepth = 0;
        storageWrapped = false;
        handPasses = 0;
        MAIN_HAND.reset();
        OFF_HAND.reset();
        uniforms.beginFrame();
    }

    public static void beginHandPass(Matrix4f projection) {
    }

    public static void endHandPass() {
    }

    public static void beginHand(InteractionHand hand, ItemStack stack) {
        handPasses++;
        if (!isEnabled(hand, stack)) {
            stateFor(hand).disabledStack = stack == null ? "null" : stack.toString();
            recordingHand = null;
            return;
        }
        CaptureState state = stateFor(hand);
        state.requested = true;
        state.item = stack;
        state.stack = stack.toString();
        state.modelViewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        state.projectionMatrix = RenderSystem.getProjectionMatrixBuffer();
        state.projectionType = RenderSystem.getProjectionType();
        recordingHand = hand;
    }

    public static void endHand() {
        recordingHand = null;
    }

    public static void beginItemSubmission(ItemDisplayContext context) {
        if (recordingHand != null && isFirstPersonContext(context)) {
            itemSubmissionDepth++;
        }
    }

    public static void endItemSubmission(ItemDisplayContext context) {
        if (isFirstPersonContext(context) && itemSubmissionDepth > 0) {
            itemSubmissionDepth--;
        }
    }

    public static SubmitNodeStorage wrapStorage(Minecraft minecraft, SubmitNodeStorage original) {
        if (!shouldRender(minecraft) || original instanceof MirroringStorage) {
            return original;
        }
        ensureDispatcher(minecraft, MAIN_HAND);
        ensureDispatcher(minecraft, OFF_HAND);
        MAIN_HAND.storage.clear();
        OFF_HAND.storage.clear();
        storageWrapped = true;
        return new MirroringStorage(original);
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
        captureFallbackTextureColors(minecraft, MAIN_HAND, InteractionHand.MAIN_HAND);
        captureFallbackTextureColors(minecraft, OFF_HAND, InteractionHand.OFF_HAND);
        compositeCapture(minecraft, mainTarget, MAIN_HAND, "main");
        compositeCapture(minecraft, mainTarget, OFF_HAND, "off");
        MAIN_HAND.reset();
        OFF_HAND.reset();
    }

    private static void compositeCapture(Minecraft minecraft, RenderTarget mainTarget, CaptureState state, String hand) {
        if (!state.captured) {
            return;
        }
        clear(sceneDepth);
        sceneDepth.copyDepthFrom(mainTarget);
        renderCapture(minecraft, state);
        GpuBufferSlice info = uniforms.write(buffer -> writeUniforms(buffer, mainTarget, ItemGlintRelightConfigManager.get(), minecraft, resolveMaterialPalette(state)));
        GpuSampler maskSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler depthSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "itemglintrelight_held_outline_" + hand, mainTarget.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(HeldItemOutlinePipelines.outline());
            pass.setUniform("OutlineInfo", info);
            pass.bindTexture("MaskSampler", sceneDepth.getColorTextureView(), maskSampler);
            pass.bindTexture("ItemDepthSampler", sceneDepth.getDepthTextureView(), depthSampler);
            pass.bindTexture("SceneDepthSampler", mainTarget.getDepthTextureView(), depthSampler);
            pass.draw(0, 3);
            diagnostic("composite " + hand + " submitted target=" + mainTarget.width + "x" + mainTarget.height
                    + " radius=" + resolveOutlineRadius(mainTarget, ItemGlintRelightConfigManager.get().outlineWidth()));
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

    private static CaptureState stateFor(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN_HAND : OFF_HAND;
    }

    private static boolean isFirstPersonContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private static SubmitNodeCollection captureCollection(int order) {
        if (recordingHand == null || itemSubmissionDepth <= 0) {
            return null;
        }
        CaptureState capture = stateFor(recordingHand);
        if (capture.storage == null) {
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
        } else if (sceneDepth.width != mainTarget.width || sceneDepth.height != mainTarget.height) {
            sceneDepth.resize(mainTarget.width, mainTarget.height);
        }
    }

    private static void renderCapture(Minecraft minecraft, CaptureState state) {
        if (!state.captured || state.dispatcher == null || state.buffers == null) {
            return;
        }
        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = sceneDepth.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = sceneDepth.getDepthTextureView();
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

    private static void writeUniforms(ByteBuffer buffer, RenderTarget target, ItemGlintRelightConfig config, Minecraft minecraft, float[][] materialPalette) {
        putColor(buffer, config.outlinePrimaryColor(), config.outlineOpacity());
        putColor(buffer, config.outlineSecondaryColor(), config.outlineOpacity());
        put(buffer, target.width, target.height, resolveOutlineRadius(target, config.outlineWidth()), config.outlineAlphaThreshold());
        float time = minecraft.level == null ? 0.0F
                : (minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)) * 0.05F;
        put(buffer, colorMode(config.outlineColorMode()), time, config.outlineColorScrollSpeed() * 9.0F, config.outlineSoftness());
        put(buffer, sampleCount(config.outlineQuality()), config.outlineGlowIntensity(), materialPalette.length, 0.0F);
        float directionRadians = (float) Math.toRadians(config.outlineColorScrollDirection());
        put(buffer, (float) Math.cos(directionRadians), -(float) Math.sin(directionRadians), config.outlineColorScrollInterval(), 0.0F);
        for (int index = 0; index < 8; index++) {
            float[] color = index < materialPalette.length ? materialPalette[index] : materialPalette[0];
            put(buffer, color[0], color[1], color[2], 1.0F);
        }
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
            case LOW -> 16;
            case MEDIUM -> 32;
            case HIGH -> 64;
        };
    }

    private static void captureTextureColors(List<BakedQuad> quads, int[] tints) {
        if (recordingHand == null || itemSubmissionDepth <= 0
                || ItemGlintRelightConfigManager.get().outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE
                || quads == null || quads.isEmpty()) {
            return;
        }

        CaptureState state = stateFor(recordingHand);
        for (BakedQuad quad : quads) {
            if (quad != null) captureTextureColors(state, quad.sprite(), resolveTint(quad, tints));
        }
    }

    private static void captureTextureColors(CaptureState state, TextureAtlasSprite sprite, int tint) {
        if (state == null || sprite == null || sprite.contents() == null) return;
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
        if (recordingHand == null || itemSubmissionDepth <= 0
                || ItemGlintRelightConfigManager.get().outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return;
        }
        captureTextureColors(stateFor(recordingHand), sprite, tint);
    }

    private static void captureFallbackTextureColors(Minecraft minecraft, CaptureState capture, InteractionHand hand) {
        if (ItemGlintRelightConfigManager.get().outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE
                || capture == null || !capture.materialColors.isEmpty() || capture.item == null || capture.item.isEmpty()) {
            return;
        }

        ItemStackRenderState renderState = new ItemStackRenderState();
        ItemDisplayContext context = hand == InteractionHand.MAIN_HAND
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
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

    private static void captureTextureColors(CaptureState state, List<BakedQuad> quads, int[] tints) {
        for (BakedQuad quad : quads) {
            if (quad != null) captureTextureColors(state, quad.sprite(), resolveTint(quad, tints));
        }
    }

    private static float[][] resolveMaterialPalette(CaptureState source) {
        if (ItemGlintRelightConfigManager.get().outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return new float[][]{{1.0F, 1.0F, 1.0F}};
        }
        if (source.materialColors.isEmpty()) {
            int fallback = ItemGlintRelightConfigManager.get().outlinePrimaryColor();
            return new float[][]{{((fallback >>> 16) & 255) / 255.0F, ((fallback >>> 8) & 255) / 255.0F, (fallback & 255) / 255.0F}};
        }
        int limit = Math.min(8, ItemGlintRelightConfigManager.get().outlineSampleColorCount());
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

    private static float resolveOutlineRadius(RenderTarget target, float logicalWidth) {
        return logicalWidth * Math.max(1, target.height) / REFERENCE_RENDER_HEIGHT;
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

    private static final class CaptureState {
        private SubmitNodeStorage storage;
        private FeatureRenderDispatcher dispatcher;
        private RenderBuffers buffers;
        private boolean captured;
        private boolean requested;
        private boolean replayed;
        private int submittedItems;
        private final Map<Integer, Integer> materialColors = new LinkedHashMap<>();
        private String stack = "-";
        private ItemStack item;
        private String disabledStack = "-";
        private Matrix4f modelViewMatrix;
        private GpuBufferSlice projectionMatrix;
        private ProjectionType projectionType;

        private void reset() {
            captured = false;
            requested = false;
            replayed = false;
            submittedItems = 0;
            materialColors.clear();
            item = null;
            stack = "-";
            disabledStack = "-";
            modelViewMatrix = null;
            projectionMatrix = null;
            projectionType = null;
            if (storage != null) storage.clear();
        }

        private String describe() {
            return "{requested=" + requested + ", captured=" + captured + ", nodes=" + submittedItems
                    + ", replayed=" + replayed + ", stack=" + stack + ", disabled=" + disabledStack + "}";
        }
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
            if (!isFirstPersonContext(context)) return;
            captureTextureColors(quads, tints);
            SubmitNodeCollection capture = captureCollection(order);
            if (capture != null) capture.submitItem(pose, context, light, overlay, color, tints, quads, type, foil);
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
