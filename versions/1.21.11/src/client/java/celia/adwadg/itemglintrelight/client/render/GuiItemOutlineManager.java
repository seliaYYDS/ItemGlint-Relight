package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import celia.adwadg.itemglintrelight.mixin.client.FeatureRenderDispatcherAccessor;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GUI outlines use isolated local targets so they never modify the hand or main render targets. */
public final class GuiItemOutlineManager {
    private static final float GUI_OUTLINE_SCALE = 8.0F;
    private static final float GUI_COLOR_SCROLL_SCALE = 10.0F;
    private static final int MASK_CACHE_LIMIT = 128;
    private static final int OUTPUT_TARGET_LIMIT = 256;
    private static final long OUTPUT_TARGET_IDLE_FRAMES = 600L;
    // GuiRenderState may sample a submitted texture after later item renders have run.
    // Keep a small, preallocated history per GUI slot instead of rewriting one texture.
    private static final int OUTPUT_TARGET_HISTORY = 4;
    private static final IdentityHashMap<TrackingItemStackRenderState, Capture> CAPTURES = new IdentityHashMap<>();
    private static final Map<PaletteKey, float[][]> MATERIAL_PALETTES = new LinkedHashMap<>(128, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PaletteKey, float[][]> eldest) {
            return size() > 128;
        }
    };
    private static final Map<MaskKey, CachedMask> MASK_CACHE = new LinkedHashMap<>(MASK_CACHE_LIMIT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MaskKey, CachedMask> eldest) {
            if (size() <= MASK_CACHE_LIMIT) return false;
            eldest.getValue().target().destroyBuffers();
            return true;
        }
    };
    private static final Map<OutputKey, CachedOutput> OUTPUT_TARGETS = new LinkedHashMap<>(OUTPUT_TARGET_LIMIT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<OutputKey, CachedOutput> eldest) {
            if (size() <= OUTPUT_TARGET_LIMIT) return false;
            eldest.getValue().destroy();
            return true;
        }
    };
    private static final IdentityHashMap<TextureTarget, TargetDebug> TARGET_DEBUG = new IdentityHashMap<>();
    private static final CachedOrthoProjectionMatrixBuffer GUI_PROJECTION =
            new CachedOrthoProjectionMatrixBuffer("itemglintrelight_gui_outline", -1000.0F, 1000.0F, true);
    private static SubmitNodeStorage storage;
    private static RenderBuffers buffers;
    private static FeatureRenderDispatcher dispatcher;
    private static TextureTarget maskScratch;
    private static int tooltipRenderDepth;
    private static long frameIndex;
    private static long nextDebugMillis;
    private static int debugLinesThisWindow;
    private static int captureDebugLinesThisFrame;

    private GuiItemOutlineManager() { }

    public static void beginFrame() {
        frameIndex++;
        pruneOutputTargets();
        debug("frame={} captures={} outputs={} masks={} tooltipDepth={}", frameIndex, CAPTURES.size(), OUTPUT_TARGETS.size(), MASK_CACHE.size(), tooltipRenderDepth);
        CAPTURES.clear();
        captureDebugLinesThisFrame = 0;
    }

    public static void capture(ItemStack stack, TrackingItemStackRenderState state) {
        if (tooltipRenderDepth > 0 || stack == null || stack.isEmpty() || state == null) return;
        ItemGlintRelightConfig base = ItemGlintRelightConfigManager.get();
        if (!base.renderGuiItems() || !base.outlineGuiItems() || !base.outlineEnabled()) return;
        ItemGlintRelightConfig config = DisplayRuleManager.resolve(stack, base);
        if (config == null || !config.outlineEnabled()) return;
        // TrackingItemStackRenderState is mutable while GuiRenderer prepares later items and tooltips.
        // Resolve sampling here, while it still belongs to this exact GUI item.
        float[][] palette = materialPalette(stack, config);
        CAPTURES.put(state, new Capture(stack.copy(), config.copy(), palette));
        if (config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE && captureDebugLinesThisFrame++ < 3) {
            debug("capture frame={} state={} item={} palette={} tooltipDepth={}", frameIndex,
                    System.identityHashCode(state), stack.getItem(), paletteHash(palette), tooltipRenderDepth);
        }
    }

    public static void beginTooltipRender() {
        tooltipRenderDepth++;
        debug("tooltip-enter frame={} depth={}", frameIndex, tooltipRenderDepth);
    }

    public static void tooltipScheduled() {
        debug("tooltip-scheduled frame={} depth={}", frameIndex, tooltipRenderDepth);
    }

    public static void endTooltipRender() {
        if (tooltipRenderDepth > 0) tooltipRenderDepth--;
        debug("tooltip-exit frame={} depth={}", frameIndex, tooltipRenderDepth);
    }

    public static void submit(GuiItemRenderState itemState, GuiRenderState guiRenderState) {
        if (itemState == null || guiRenderState == null || !(itemState.itemStackRenderState() instanceof TrackingItemStackRenderState trackingState)) return;
        Capture capture = CAPTURES.remove(trackingState);
        if (capture == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return;
        int precision = precision(capture.config().guiOutlineQuality());
        int padding = padding(capture.config());
        ScreenRectangle outputRect = new ScreenRectangle(itemState.x() - padding, itemState.y() - padding,
                16 + padding * 2, 16 + padding * 2);
        int maskWidth = outputRect.width() * precision;
        int maskHeight = outputRect.height() * precision;
        boolean cacheableMask = !trackingState.isAnimated();
        MaskKey maskKey = new MaskKey(capture.stack().toString(), maskWidth, maskHeight, precision);
        CachedMask cachedMask = cacheableMask ? MASK_CACHE.get(maskKey) : null;
        TextureTarget mask = cachedMask == null && cacheableMask
                ? new TextureTarget("itemglintrelight_gui_item_mask_cached", maskWidth, maskHeight, true)
                : cachedMask == null ? mask(maskWidth, maskHeight) : cachedMask.target();
        OutputKey outputKey = new OutputKey(capture.stack().toString(), itemState.x(), itemState.y(),
                outputRect.width() * precision, outputRect.height() * precision);
        TextureTarget output = output(outputKey);
        TargetDebug previousOwner = TARGET_DEBUG.get(output);
        try {
            clear(output);
            if (cachedMask == null) clear(mask);
            if (cachedMask == null && !renderMask(minecraft, trackingState, mask, padding * precision, precision)) {
                if (cacheableMask) mask.destroyBuffers();
                return;
            }
            if (cacheableMask && cachedMask == null) {
                MASK_CACHE.put(maskKey, new CachedMask(mask, frameIndex));
            }
            HeldItemOutlineRenderer.compositePreviewToTexture(minecraft, capture.config(), mask,
                    output.getColorTextureView(), output.getDepthTextureView(),
                    capture.materialPalette(),
                    GUI_OUTLINE_SCALE, GUI_COLOR_SCROLL_SCALE / precision, GUI_OUTLINE_SCALE * 0.12F);
            OUTPUT_TARGETS.get(outputKey).markUsed(frameIndex);
            if (capture.config().outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
                int paletteHash = paletteHash(capture.materialPalette());
                debug("submit frame={} state={} item={} palette={} target={} previous={} rect={}x{} outputs={}",
                        frameIndex, System.identityHashCode(trackingState), capture.stack().getItem(), paletteHash,
                        System.identityHashCode(output), previousOwner == null ? "new" : previousOwner.describe(),
                        output.width, output.height, OUTPUT_TARGETS.size());
                TARGET_DEBUG.put(output, new TargetDebug(frameIndex, capture.stack().getItem().toString(), paletteHash));
            }
            submitBlit(itemState, guiRenderState, outputRect, output);
        } catch (RuntimeException exception) {
            if (cacheableMask && cachedMask == null) {
                MASK_CACHE.remove(maskKey);
                mask.destroyBuffers();
            }
            CachedOutput cachedOutput = OUTPUT_TARGETS.remove(outputKey);
            if (cachedOutput != null) {
                cachedOutput.destroy();
            }
        }
    }

    private static boolean renderMask(Minecraft minecraft, TrackingItemStackRenderState state, TextureTarget target, int padding, int precision) {
        ensureDispatcher(minecraft);
        if (dispatcher == null) return false;
        storage.clear();
        GpuTextureView oldColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView oldDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(GUI_PROJECTION.getBuffer(target.width, target.height), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().set(new Matrix4f());
        PoseStack pose = new PoseStack();
        try {
            pose.pushPose();
            pose.translate(padding + 8.0F * precision, padding + 8.0F * precision, 0.0F);
            pose.scale(16.0F * precision, -16.0F * precision, 16.0F * precision);
            minecraft.gameRenderer.getLighting().setupFor(state.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            state.submit(pose, storage, 15728880, OverlayTexture.NO_OVERLAY, 0);
            dispatcher.renderAllFeatures();
            buffers.bufferSource().endBatch();
            buffers.outlineBufferSource().endOutlineBatch();
            buffers.crumblingBufferSource().endBatch();
            return true;
        } finally {
            pose.popPose();
            storage.endFrame();
            dispatcher.endFrame();
            storage.clear();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.outputColorTextureOverride = oldColor;
            RenderSystem.outputDepthTextureOverride = oldDepth;
        }
    }

    private static void ensureDispatcher(Minecraft minecraft) {
        if (dispatcher != null) return;
        storage = new SubmitNodeStorage();
        buffers = new RenderBuffers(1);
        FeatureRenderDispatcher source = minecraft.gameRenderer.getFeatureRenderDispatcher();
        dispatcher = new FeatureRenderDispatcher(storage, minecraft.getBlockRenderer(), buffers.bufferSource(),
                ((FeatureRenderDispatcherAccessor) source).itemglintrelight$getAtlasManager(), buffers.outlineBufferSource(),
                buffers.crumblingBufferSource(), minecraft.font);
    }

    private static void clear(TextureTarget target) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0D);
    }

    private static TextureTarget mask(int width, int height) {
        if (maskScratch == null) {
            maskScratch = new TextureTarget("itemglintrelight_gui_item_mask", width, height, true);
        } else if (maskScratch.width != width || maskScratch.height != height) {
            maskScratch.resize(width, height);
        }
        return maskScratch;
    }

    private static TextureTarget output(OutputKey key) {
        CachedOutput cached = OUTPUT_TARGETS.get(key);
        if (cached == null) {
            cached = new CachedOutput(key.width(), key.height(), frameIndex);
            OUTPUT_TARGETS.put(key, cached);
        }
        return cached.nextTarget(frameIndex);
    }

    private static void submitBlit(GuiItemRenderState itemState, GuiRenderState guiRenderState,
                                   ScreenRectangle outputRect, TextureTarget output) {
        guiRenderState.submitBlitToCurrentLayer(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(output.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                new Matrix3x2f(itemState.pose()), outputRect.left(), outputRect.top(), outputRect.right(), outputRect.bottom(),
                0.0F, 1.0F, 1.0F, 0.0F, -1, itemState.scissorArea()
        ));
    }


    private static int padding(ItemGlintRelightConfig config) {
        int padding = 3 + (int) Math.ceil(config.outlineWidth() * 4.5F + config.outlineSoftness() * 2.0F);
        if (config.outlineBloomEnabled()) padding += (int) Math.ceil(config.outlineBloomRadius() * 2.0F);
        return padding;
    }

    private static int precision(RenderQuality quality) {
        return switch (quality) {
            case LOW -> 1;
            case HIGH -> 3;
            default -> 2;
        };
    }

    private static void pruneOutputTargets() {
        java.util.Iterator<Map.Entry<OutputKey, CachedOutput>> iterator = OUTPUT_TARGETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<OutputKey, CachedOutput> entry = iterator.next();
            if (frameIndex - entry.getValue().lastUsedFrame() <= OUTPUT_TARGET_IDLE_FRAMES) continue;
            entry.getValue().destroy();
            iterator.remove();
        }
    }

    private static int paletteHash(float[][] palette) {
        int hash = 1;
        if (palette == null) return hash;
        for (float[] color : palette) {
            if (color == null) continue;
            for (float channel : color) hash = 31 * hash + Float.floatToIntBits(channel);
        }
        return hash;
    }

    private static void debug(String format, Object... arguments) {
        long now = System.currentTimeMillis();
        if (now >= nextDebugMillis) {
            nextDebugMillis = now + 2000L;
            debugLinesThisWindow = 0;
        }
        if (debugLinesThisWindow++ < 4) {
            ItemGlintRelight.LOGGER.info("[GuiOutlineDebug] " + format, arguments);
        }
    }

    private static float[][] materialPalette(ItemStack stack, ItemGlintRelightConfig config) {
        if (config.outlineColorMode() != celia.adwadg.itemglintrelight.config.OutlineColorMode.TEXTURE_SAMPLE) {
            return HeldItemOutlineRenderer.resolveGuiMaterialPalette(Minecraft.getInstance(), stack, config);
        }
        PaletteKey key = new PaletteKey(stack.toString(), config.outlineSampleSize(), config.outlineSampleColorCount());
        float[][] palette = MATERIAL_PALETTES.get(key);
        if (palette == null) {
            palette = HeldItemOutlineRenderer.resolveGuiMaterialPalette(Minecraft.getInstance(), stack, config);
            MATERIAL_PALETTES.put(key, palette);
        }
        return palette;
    }

    private record Capture(ItemStack stack, ItemGlintRelightConfig config, float[][] materialPalette) { }
    private record PaletteKey(String stackSignature, int sampleSize, int colorCount) { }
    private record MaskKey(String stackSignature, int width, int height, int precision) { }
    private record OutputKey(String stackSignature, int x, int y, int width, int height) { }
    private record CachedMask(TextureTarget target, long builtFrame) { }
    private static final class CachedOutput {
        private final TextureTarget[] targets = new TextureTarget[OUTPUT_TARGET_HISTORY];
        private long lastUsedFrame;
        private long submissionFrame = Long.MIN_VALUE;
        private int submissionsThisFrame;

        private CachedOutput(int width, int height, long lastUsedFrame) {
            for (int index = 0; index < targets.length; index++) {
                targets[index] = new TextureTarget("itemglintrelight_gui_item_outline_" + index, width, height, true);
            }
            this.lastUsedFrame = lastUsedFrame;
        }

        private long lastUsedFrame() { return lastUsedFrame; }
        private void markUsed(long frame) { lastUsedFrame = frame; }

        private TextureTarget nextTarget(long frame) {
            if (submissionFrame != frame) {
                submissionFrame = frame;
                submissionsThisFrame = 0;
            }
            TextureTarget target = targets[(int) ((frame + submissionsThisFrame) % targets.length)];
            submissionsThisFrame++;
            return target;
        }

        private void destroy() {
            for (TextureTarget target : targets) {
                TARGET_DEBUG.remove(target);
                target.destroyBuffers();
            }
        }
    }
    private record TargetDebug(long frame, String item, int paletteHash) {
        private String describe() {
            return "frame=" + frame + ",item=" + item + ",palette=" + paletteHash;
        }
    }
}
