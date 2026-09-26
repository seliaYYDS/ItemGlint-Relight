package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import celia.adwadg.itemglintrelight.mixin.client.FeatureRenderDispatcherAccessor;
import celia.adwadg.itemglintrelight.mixin.client.ItemStackLayerRenderStateAccessor;
import celia.adwadg.itemglintrelight.mixin.client.ItemStackRenderStateAccessor;
import celia.adwadg.itemglintrelight.mixin.client.SpriteContentsAccessor;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GUI outlines use isolated local targets so they never modify the hand or main render targets. */
public final class GuiItemOutlineManager {
    private static final float GUI_OUTLINE_SCALE = 8.0F;
    private static final float GUI_COLOR_SCROLL_SCALE = 10.0F;
    private static final float GUI_OUTLINE_MAX_WIDTH = 3.5F;
    /**
     * Raised from 128 so a screen holding more distinct stacks than the old budget (creative tabs,
     * recipe viewers, storage mods) stops evicting and rebuilding masks while it is on screen. A
     * cached mask is one padded item at the GUI precision, roughly 56 KiB at the default settings,
     * so the larger budget stays in the low tens of megabytes.
     */
    private static final int MASK_CACHE_LIMIT = 256;
    private static final int OUTPUT_TARGET_LIMIT = 256;
    private static final long OUTPUT_TARGET_IDLE_FRAMES = 600L;
    /**
     * GUI submission can remain in flight for more than four high-FPS frames, so a texture written
     * in frame N must not be rewritten before frame N + 8. submit() renders a content key at most
     * once per frame, which makes {@code frame % OUTPUT_TARGET_HISTORY} a correct rotation for a
     * ring of exactly this depth.
     */
    private static final int OUTPUT_TARGET_HISTORY = 8;
    /**
     * Upper bound on one content key's ring. {@code OUTPUT_TARGET_HISTORY + slot} already preserves
     * the eight-frame gap for up to 25 same-stack slots in a single frame, which covers every
     * vanilla container; this bound only stops a pathological screen from allocating without end.
     */
    private static final int OUTPUT_TARGET_HISTORY_MAX = 32;
    /**
     * Idle GPU targets grouped by shape, for both the mask cache and the per-key output rings. Both
     * evict on a fixed budget, and a screen with more distinct stacks than that budget (a large chest,
     * a recipe viewer being scrolled) used to destroy and immediately recreate same-sized GPU textures
     * on every frame. A recycled target is only handed out after it has been idle for
     * {@link #OUTPUT_TARGET_HISTORY} frames, so no queued render pass or blit can still be reading it.
     */
    private static final int TARGET_POOL_LIMIT = 64;
    /**
     * Single-colour GUI outlines are frame-invariant, so their composited texture is reused until
     * an input changes. Set {@code -Ditemglintrelight.guiResultCache=false} to disable the reuse
     * path for A/B comparison against the previous per-frame recomposite.
     */
    private static final boolean RESULT_CACHE_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("itemglintrelight.guiResultCache", "true"));
    /**
     * The mask, bloom and result caches used to be disabled for every item vanilla marks
     * {@code animated}, which includes every stack carrying the enchantment glint. The glint is
     * drawn with an EQUAL depth test inside the item's own silhouette, so it cannot move the
     * coverage boundary the outline reads; only a genuinely animated sprite can, and that case is
     * handled by {@link #ANIMATED_MASK_CACHE_ENABLED}. Set
     * {@code -Ditemglintrelight.guiFoilMaskCache=false} to restore the previous behaviour for A/B
     * comparison.
     */
    private static final boolean FOIL_MASK_CACHE_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("itemglintrelight.guiFoilMaskCache", "true"));
    /**
     * Extends mask caching to items whose sprites animate but whose alpha channel does not vary
     * between animation frames -- see {@link #alphaStableSprites}. Without this, every animated item
     * in a large container re-runs the whole mask, bloom and composite chain on every frame. Set
     * {@code -Ditemglintrelight.guiAnimatedMaskCache=false} to restore the strict per-frame
     * behaviour for A/B comparison.
     */
    private static final boolean ANIMATED_MASK_CACHE_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("itemglintrelight.guiAnimatedMaskCache", "true"));
    /**
     * Alpha-channel verdicts per sprite, keyed by identity. Sprites are long-lived atlas objects, and
     * the verdict is a pure function of the sheet pixels, so it is computed once per sprite. A full
     * clear on overflow is enough: a resource reload builds new sprites and the stale keys age out.
     */
    private static final Map<SpriteContents, Boolean> SPRITE_ALPHA_STABILITY = new IdentityHashMap<>();
    private static final int SPRITE_ALPHA_STABILITY_LIMIT = 2048;
    /**
     * Shared placeholder palette. Only TEXTURE_SAMPLE feeds materialPalette to the shaders; every
     * other colour mode resolves its colours from the config, so the sampled palette is unused.
     */
    private static final float[][] UNIT_PALETTE = {{1.0F, 1.0F, 1.0F}};
    private static final IdentityHashMap<TrackingItemStackRenderState, Capture> CAPTURES = new IdentityHashMap<>();
    private static final Map<ScreenSlot, SubmissionTrace> FRAME_SUBMISSIONS = new LinkedHashMap<>();
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
            releaseTarget(eldest.getValue().target(), true);
            return true;
        }
    };
    private static final Map<TargetShape, ArrayDeque<PooledTarget>> TARGET_POOL = new HashMap<>();
    private static int pooledTargets;
    /**
     * Per-frame memos. Every slot in a container normally resolves to the same config object -- the
     * shared base config whenever no display rule matches -- so the GUI-clamped copy and its
     * fingerprint are computed once per frame instead of once per slot. Clearing them in beginFrame()
     * means a config edit is still picked up on the very next frame.
     */
    private static final IdentityHashMap<ItemGlintRelightConfig, ItemGlintRelightConfig> GUI_CONFIGS = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemGlintRelightConfig, String> CONFIG_FINGERPRINTS = new IdentityHashMap<>();
    /**
     * Access-ordered so iteration yields the least recently used slot first. Overflow is trimmed
     * explicitly in {@link #evictOutputTargets()} because an entry whose textures may still be
     * referenced by a queued GUI blit must not be destroyed.
     */
    private static final Map<OutputKey, CachedOutput> OUTPUT_TARGETS = new LinkedHashMap<>(OUTPUT_TARGET_LIMIT, 0.75F, true);
    private static final IdentityHashMap<TextureTarget, SubmissionTrace> TARGET_TRACES = new IdentityHashMap<>();
    private static final CachedOrthoProjectionMatrixBuffer GUI_PROJECTION =
            new CachedOrthoProjectionMatrixBuffer("itemglintrelight_gui_outline", -1000.0F, 1000.0F, true);
    private static SubmitNodeStorage storage;
    private static RenderBuffers buffers;
    private static FeatureRenderDispatcher dispatcher;
    private static TextureTarget maskScratch;
    private static int tooltipRenderDepth;
    private static long frameIndex;

    private GuiItemOutlineManager() { }

    public static void beginFrame() {
        frameIndex++;
        pruneOutputTargets();
        if (!CAPTURES.isEmpty()) {
            trace("UNCONSUMED_CAPTURE", "frame={} count={} captures were not submitted in their frame", frameIndex, CAPTURES.size());
        }
        CAPTURES.clear();
        FRAME_SUBMISSIONS.clear();
        GUI_CONFIGS.clear();
        CONFIG_FINGERPRINTS.clear();
    }

    public static void capture(ItemStack stack, TrackingItemStackRenderState state) {
        if (tooltipRenderDepth > 0 || stack == null || stack.isEmpty() || state == null) return;
        ItemGlintRelightConfig base = ItemGlintRelightConfigManager.get();
        if (!base.renderGuiItems() || !base.outlineGuiItems() || !base.outlineEnabled()) return;
        ItemGlintRelightConfig config = DisplayRuleManager.resolve(stack, base);
        if (config == null || !config.outlineEnabled()) return;
        // TrackingItemStackRenderState is mutable while GuiRenderer prepares later items and tooltips.
        // Resolve sampling here, while it still belongs to this exact GUI item.
        // Keep the exact sampled result with this capture. The palette cache intentionally
        // shares entries, but a GUI capture must never observe a later cache replacement.
        // No defensive copy: materialPalette() hands back either the shared UNIT_PALETTE or an array
        // the palette cache stores once and never mutates. Retiring a cache entry drops the reference,
        // it does not rewrite the array, so a capture can hold it safely.
        float[][] palette = config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE
                ? materialPalette(stack, config) : UNIT_PALETTE;
        // The full stack identity (item + count + components) is built once here and reused by
        // the mask/output/palette keys; ItemStack.toString() would drop every component.
        // Mask stability is decided here too: this is the only place where the live stack and the
        // already populated render state are both in hand, and submit() runs once per slot.
        boolean maskStable = stableMask(stack, state);
        // The stack reference is kept rather than a copy: the only reader is a diagnostic that asks
        // for getItem(), which is immutable for a given ItemStack. The cache keys take their own
        // snapshot through ItemStackKey.of(...), which is what actually has to stay stable.
        Capture previous = CAPTURES.put(state,
                new Capture(stack, config, palette, ItemStackKey.of(stack), maskStable));
        if (previous != null && config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
            trace("CAPTURE_REPLACED", "frame={} state={} oldItem={} oldPalette={} newItem={} newPalette={} tooltipDepth={}",
                    frameIndex, System.identityHashCode(state), previous.stack().getItem(), paletteHash(previous.materialPalette()),
                    stack.getItem(), paletteHash(palette), tooltipRenderDepth);
        }
    }

    /**
     * Whether the item's rendered silhouette is unchanged from frame to frame.
     *
     * <p>This is deliberately not the same question as {@link ItemStackRenderState#isAnimated()}.
     * Vanilla sets that flag when the item atlas image has to be re-rendered: for every stack with
     * the enchantment glint ({@code BlockModelWrapper} and {@code SpecialModelWrapper} both call
     * {@code setAnimated()} inside their {@code hasFoil()} branch) and for every model whose sprite
     * is animated. Using it here disabled the mask, bloom and result caches for all enchanted gear,
     * which is the common case in every real inventory.
     *
     * <p>The mask only has to be stable in the channels the shaders actually consume. Both
     * {@code held_item_outline.fsh} and {@code held_item_bloom_composite.fsh} read the mask alpha as
     * coverage and nothing else ({@code coverage()}, {@code visibleCoverage()},
     * {@code texture(MaskSampler, ...).a}); the item depth they also sample is dead weight on the GUI
     * path, where {@code scrollBounds.z} is the constant 1.0 and scene occlusion is disabled. The
     * glint is drawn from the item's own quads with an EQUAL depth test, so it can only ever land on
     * pixels the item itself already wrote, and it cannot move the coverage boundary. An animated
     * sprite can change the rendered alpha, so it still disables the caches.
     *
     * <p>Residual exposure: the glint is composited on top of the item, so for a model drawn with a
     * translucent render type (non-binary texture alpha) the cached mask keeps the glint
     * contribution of the frame it was built on. Vanilla item textures are hard-edged, and a fully
     * transparent cutout pixel writes no depth for the depth-equal glint to land on, so the coverage
     * the outline reads is unchanged there. Set
     * {@code -Ditemglintrelight.guiFoilMaskCache=false} to restore the strict per-frame behaviour
     * while checking this against a specific resource pack.
     */
    private static boolean stableMask(ItemStack stack, TrackingItemStackRenderState state) {
        if (!state.isAnimated()) return true;
        if (hasAnimatedSprite(state)) {
            // An animated sprite can change the rendered alpha, so it may only keep its mask when
            // every frame of it carries an identical alpha channel.
            return ANIMATED_MASK_CACHE_ENABLED && alphaStableSprites(state);
        }
        // Animated purely by the enchantment glint, which cannot move the coverage boundary.
        return FOIL_MASK_CACHE_ENABLED && stack != null && stack.hasFoil();
    }

    /** Mirrors the sprite test {@code BlockModelWrapper} uses to decide it has an animated texture. */
    private static boolean hasAnimatedSprite(ItemStackRenderState renderState) {
        ItemStackRenderStateAccessor stateAccessor = (ItemStackRenderStateAccessor) (Object) renderState;
        ItemStackRenderState.LayerRenderState[] layers = stateAccessor.itemglintrelight$getLayers();
        int layerCount = layers == null ? 0 : Math.min(stateAccessor.itemglintrelight$getActiveLayerCount(), layers.length);
        for (int index = 0; index < layerCount; index++) {
            ItemStackRenderState.LayerRenderState layer = layers[index];
            if (layer == null) continue;
            List<BakedQuad> quads = ((ItemStackLayerRenderStateAccessor) (Object) layer).itemglintrelight$getQuads();
            if (quads == null) continue;
            for (BakedQuad quad : quads) {
                if (quad == null) continue;
                TextureAtlasSprite sprite = quad.sprite();
                if (sprite != null && sprite.contents() != null && sprite.contents().isAnimated()) return true;
            }
        }
        return false;
    }

    /**
     * Whether every animated sprite this item renders keeps an identical alpha channel across all of
     * its animation frames.
     *
     * <p>The outline and bloom shaders read the mask alpha as coverage and nothing else, and an
     * animated sprite does not move the model's geometry, so a sprite whose frames share one alpha
     * channel produces the same mask on every frame even though its colours animate. That is the
     * common case for vanilla and modded item textures; flickering shapes such as fire genuinely
     * change alpha per frame and are rejected here. Without this, every animated item in a large
     * container re-runs the whole mask, bloom and composite chain on every single frame.
     */
    private static boolean alphaStableSprites(ItemStackRenderState renderState) {
        ItemStackRenderStateAccessor stateAccessor = (ItemStackRenderStateAccessor) (Object) renderState;
        ItemStackRenderState.LayerRenderState[] layers = stateAccessor.itemglintrelight$getLayers();
        int layerCount = layers == null ? 0 : Math.min(stateAccessor.itemglintrelight$getActiveLayerCount(), layers.length);
        for (int index = 0; index < layerCount; index++) {
            ItemStackRenderState.LayerRenderState layer = layers[index];
            if (layer == null) continue;
            List<BakedQuad> quads = ((ItemStackLayerRenderStateAccessor) (Object) layer).itemglintrelight$getQuads();
            if (quads == null) continue;
            for (BakedQuad quad : quads) {
                if (quad == null) continue;
                TextureAtlasSprite sprite = quad.sprite();
                SpriteContents contents = sprite == null ? null : sprite.contents();
                if (contents == null || !contents.isAnimated()) continue;
                if (!spriteAlphaStable(contents)) return false;
            }
        }
        return true;
    }

    private static boolean spriteAlphaStable(SpriteContents contents) {
        Boolean cached = SPRITE_ALPHA_STABILITY.get(contents);
        if (cached != null) return cached;
        boolean stable = scanSpriteAlpha(contents);
        if (SPRITE_ALPHA_STABILITY.size() >= SPRITE_ALPHA_STABILITY_LIMIT) SPRITE_ALPHA_STABILITY.clear();
        SPRITE_ALPHA_STABILITY.put(contents, stable);
        return stable;
    }

    /**
     * Compares the alpha channel of every distinct animation frame against the first frame.
     *
     * <p>Frames are packed as a row-major grid of {@code width x height} cells inside mip level 0 --
     * the same layout {@code SpriteContents.isTransparent(frame, x, y)} walks -- so the whole sheet can
     * be read straight from the mip level the mod already has an accessor for. Every uncertain branch
     * answers "unstable": a sheet whose dimensions are not an exact multiple of the frame size, or a
     * frame index the visible grid does not cover, is never assumed to be safe.
     */
    private static boolean scanSpriteAlpha(SpriteContents contents) {
        NativeImage[] mipLevels = ((SpriteContentsAccessor) (Object) contents).itemglintrelight$getByMipLevel();
        NativeImage sheet = mipLevels == null || mipLevels.length == 0 ? null : mipLevels[0];
        int width = contents.width();
        int height = contents.height();
        if (sheet == null || width <= 0 || height <= 0) return false;
        int[] frames = contents.getUniqueFrames().toArray();
        if (frames.length <= 1) return true;
        int sheetWidth = sheet.getWidth();
        int sheetHeight = sheet.getHeight();
        if (sheetWidth % width != 0 || sheetHeight % height != 0) return false;
        int columns = sheetWidth / width;
        int rows = sheetHeight / height;
        if (columns * rows <= 1) return false;
        for (int frame : frames) {
            if (frame < 0 || frame >= columns * rows) return false;
        }
        for (int frame : frames) {
            if (frame == 0) continue;
            int originX = (frame % columns) * width;
            int originY = (frame / columns) * height;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((sheet.getPixel(originX + x, originY + y) >>> 24) != (sheet.getPixel(x, y) >>> 24)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void beginTooltipRender() {
        tooltipRenderDepth++;
    }

    public static void tooltipScheduled(int x, int y) {
        // This callback is also used by container integrations with synthetic coordinates.
        // It cannot identify a rendered slot, so it must not label the entire frame as hover work.
    }

    public static void endTooltipRender() {
        if (tooltipRenderDepth > 0) tooltipRenderDepth--;
    }

    public static void submit(GuiItemRenderState itemState, GuiRenderState guiRenderState) {
        if (itemState == null || guiRenderState == null || !(itemState.itemStackRenderState() instanceof TrackingItemStackRenderState trackingState)) return;
        Capture capture = CAPTURES.remove(trackingState);
        if (capture == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return;
        ItemGlintRelightConfig config = guiConfig(capture.config());
        int precision = precision(config.guiOutlineQuality());
        int padding = padding(config);
        ScreenRectangle outputRect = new ScreenRectangle(itemState.x() - padding, itemState.y() - padding,
                16 + padding * 2, 16 + padding * 2);
        int maskWidth = outputRect.width() * precision;
        int maskHeight = outputRect.height() * precision;
        boolean cacheableMask = capture.stableMask();
        ItemStackKey stackKey = capture.key();
        MaskKey maskKey = new MaskKey(stackKey, maskWidth, maskHeight, precision);
        // The composited texture holds the outline around a fixed local origin, so no pixel of it
        // depends on where the slot sits on screen: x/y only ever reach submitBlit(), while the
        // composite shader reads the target's own size, its local texCoord and the global clock.
        // Dropping the position from the key changes no pixel and lets every slot holding the same
        // stack share one cached output (and one texture ring) instead of one output per slot.
        OutputKey outputKey = new OutputKey(stackKey,
                outputRect.width() * precision, outputRect.height() * precision,
                textureSampleIdentity(capture));
        CachedOutput cachedOutput = cachedOutput(outputKey);
        // Every slot resolving to this content key composites the same pixels, so the first one in a
        // frame renders and the rest blit the texture it produced. That removes the duplicate work
        // and, just as importantly, holds this key to one production per frame -- the invariant the
        // texture ring depends on to keep its eight-frame reuse gap.
        if (cachedOutput.producedThisFrame(frameIndex)) {
            cachedOutput.markUsed(frameIndex);
            submitBlit(itemState, guiRenderState, outputRect, cachedOutput.producedTarget());
            return;
        }
        // A single-colour outline is the only time-invariant mode: resolveColor() returns the
        // primary colour before scrollPosition() is ever evaluated, in both the outline and the
        // bloom composite shaders. Everything else keeps the per-frame recomposite.
        String reuseKey = RESULT_CACHE_ENABLED && cacheableMask && config.outlineColorMode() == OutlineColorMode.SINGLE
                ? configFingerprint(config) : null;
        TextureTarget output;
        if (reuseKey != null && reuseKey.equals(cachedOutput.cachedKey())) {
            output = cachedOutput.cachedTarget();
            cachedOutput.markUsed(frameIndex);
            cachedOutput.markProduced(frameIndex, output);
            submitBlit(itemState, guiRenderState, outputRect, output);
            return;
        }

        output = cachedOutput.nextTarget(frameIndex);
        SubmissionTrace currentTrace = null;
        if (config.outlineColorMode() == OutlineColorMode.TEXTURE_SAMPLE) {
            currentTrace = new SubmissionTrace(frameIndex, System.identityHashCode(trackingState),
                    capture.stack().getItem().toString(), paletteHash(capture.materialPalette()), itemState.x(), itemState.y());
            SubmissionTrace previousSlot = FRAME_SUBMISSIONS.put(
                    new ScreenSlot(itemState.x(), itemState.y(), outputRect.width(), outputRect.height()), currentTrace);
            SubmissionTrace previousTexture = TARGET_TRACES.get(output);
            if (previousSlot != null && !previousSlot.sameVisualIdentity(currentTrace)) {
                trace("SLOT_REPLACED", "frame={} old=[{}] new=[{}]", frameIndex, previousSlot.describe(), currentTrace.describe());
            }
            if (previousTexture != null && frameIndex - previousTexture.frame() < OUTPUT_TARGET_HISTORY) {
                trace("TEXTURE_REUSED_EARLY", "frame={} target={} old=[{}] new=[{}]", frameIndex,
                        System.identityHashCode(output), previousTexture.describe(), currentTrace.describe());
            }
        }
        CachedMask cachedMask = cacheableMask ? MASK_CACHE.get(maskKey) : null;
        TextureTarget mask = null;
        try {
            clearColor(output);
            mask = cachedMask == null && cacheableMask
                    ? acquireTarget("itemglintrelight_gui_item_mask_cached", maskWidth, maskHeight, true)
                    : cachedMask == null ? mask(maskWidth, maskHeight) : cachedMask.target();
            if (cachedMask == null) clear(mask);
            if (cachedMask == null && !renderMask(minecraft, trackingState, mask, padding * precision, precision)) {
                if (cacheableMask) releaseTarget(mask, true);
                return;
            }
            if (cacheableMask && cachedMask == null) {
                MASK_CACHE.put(maskKey, new CachedMask(mask, frameIndex));
            }
            GpuTextureView bloom = null;
            if (config.outlineBloomEnabled()) {
                // The blur chain only reads the mask alpha, so it is frame-invariant for cacheable
                // masks. Reuse it and only re-run the colour-dependent composite and outline.
                String bloomKey = cacheableMask ? configFingerprint(config) : null;
                if (bloomKey != null && bloomKey.equals(cachedOutput.bloomKey())) {
                    bloom = cachedOutput.bloomView();
                } else {
                    TextureTarget[] bloomTargets = cachedOutput.bloomTargets(maskWidth, maskHeight);
                    bloom = HeldItemOutlineRenderer.renderGuiBloom(maskWidth, maskHeight, mask.getColorTextureView(),
                            config, bloomTargets[0], bloomTargets[1], GUI_OUTLINE_SCALE * 0.12F);
                    if (bloomKey != null) cachedOutput.markBloom(bloomKey, bloom);
                }
            }
            // A single pass: the GUI composite has no scene-occlusion or arm term, so it needs no
            // depth stand-in, and it folds the bloom composite in rather than blending twice.
            HeldItemOutlineRenderer.compositeGuiItemToTexture(minecraft, config, mask,
                    output.getColorTextureView(), capture.materialPalette(),
                    GUI_OUTLINE_SCALE, GUI_COLOR_SCROLL_SCALE / precision, bloom);
            cachedOutput.markUsed(frameIndex);
            cachedOutput.markProduced(frameIndex, output);
            if (reuseKey != null) cachedOutput.markCached(output, reuseKey);
            if (currentTrace != null) {
                TARGET_TRACES.put(output, currentTrace);
            }
            submitBlit(itemState, guiRenderState, outputRect, output);
        } catch (RuntimeException exception) {
            if (cacheableMask && cachedMask == null && mask != null) {
                MASK_CACHE.remove(maskKey);
                releaseTarget(mask, true);
            }
            OUTPUT_TARGETS.remove(outputKey);
            cachedOutput.destroy();
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

    /** Output targets carry no depth attachment; only their colour is cleared. */
    private static void clearColor(TextureTarget target) {
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), 0);
    }

    private static TextureTarget mask(int width, int height) {
        if (maskScratch == null) {
            maskScratch = new TextureTarget("itemglintrelight_gui_item_mask", width, height, true);
        } else if (maskScratch.width != width || maskScratch.height != height) {
            maskScratch.resize(width, height);
        }
        return maskScratch;
    }

    /**
     * Takes a texture of the requested shape from the recycle pool, or creates one. Both paths return
     * an uncleared target; every caller clears what it acquires.
     */
    private static TextureTarget acquireTarget(String label, int width, int height, boolean depth) {
        ArrayDeque<PooledTarget> pool = TARGET_POOL.get(new TargetShape(width, height, depth));
        if (pool != null) {
            for (Iterator<PooledTarget> iterator = pool.iterator(); iterator.hasNext(); ) {
                PooledTarget pooled = iterator.next();
                if (frameIndex - pooled.releasedFrame() < OUTPUT_TARGET_HISTORY) continue;
                iterator.remove();
                pooledTargets--;
                return pooled.target();
            }
        }
        return new TextureTarget(label, width, height, depth);
    }

    /**
     * Returns an evicted texture to the recycle pool instead of destroying it. An entry stays parked
     * until it has been idle for {@link #OUTPUT_TARGET_HISTORY} frames -- the same window the output
     * ring uses -- so a render pass or blit queued in an earlier frame can never observe recycled
     * content. Entries are scanned oldest first, and the pool as a whole stays bounded.
     */
    private static void releaseTarget(TextureTarget target, boolean depth) {
        if (target == null) return;
        if (pooledTargets >= TARGET_POOL_LIMIT) {
            target.destroyBuffers();
            return;
        }
        TARGET_POOL.computeIfAbsent(new TargetShape(target.width, target.height, depth), shape -> new ArrayDeque<>())
                .addLast(new PooledTarget(target, frameIndex));
        pooledTargets++;
    }

    private static CachedOutput cachedOutput(OutputKey key) {
        CachedOutput cached = OUTPUT_TARGETS.get(key);
        if (cached == null) {
            cached = new CachedOutput(key.width(), key.height(), frameIndex);
            OUTPUT_TARGETS.put(key, cached);
        }
        return cached;
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

    /** GUI item masks become visually unstable beyond this width; other render paths keep it unchanged. */
    private static ItemGlintRelightConfig guiConfig(ItemGlintRelightConfig source) {
        ItemGlintRelightConfig cached = GUI_CONFIGS.get(source);
        if (cached != null) return cached;
        ItemGlintRelightConfig config = source.copy();
        config.setOutlineWidth(Math.min(config.outlineWidth(), GUI_OUTLINE_MAX_WIDTH));
        GUI_CONFIGS.put(source, config);
        return config;
    }

    private static int precision(RenderQuality quality) {
        return switch (quality) {
            case LOW -> 1;
            case HIGH -> 3;
            default -> 2;
        };
    }

    private static void pruneOutputTargets() {
        Iterator<Map.Entry<OutputKey, CachedOutput>> iterator = OUTPUT_TARGETS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<OutputKey, CachedOutput> entry = iterator.next();
            if (frameIndex - entry.getValue().lastUsedFrame() <= OUTPUT_TARGET_IDLE_FRAMES) continue;
            entry.getValue().destroy();
            iterator.remove();
        }
        evictOutputTargets();
    }

    /**
     * Enforces the hard cap the access-ordered map used to defer indefinitely. Entries whose
     * textures may still be referenced by a queued blit (used within the history window) are
     * skipped, so the map can temporarily exceed the cap but never grows without bound.
     */
    private static void evictOutputTargets() {
        if (OUTPUT_TARGETS.size() <= OUTPUT_TARGET_LIMIT) return;
        Iterator<Map.Entry<OutputKey, CachedOutput>> iterator = OUTPUT_TARGETS.entrySet().iterator();
        while (iterator.hasNext() && OUTPUT_TARGETS.size() > OUTPUT_TARGET_LIMIT) {
            Map.Entry<OutputKey, CachedOutput> entry = iterator.next();
            CachedOutput evicted = entry.getValue();
            if (frameIndex - evicted.lastUsedFrame() <= OUTPUT_TARGET_HISTORY) continue;
            evicted.destroy();
            iterator.remove();
        }
    }

    /**
     * Every field that can change the composited pixels for a time-invariant (SINGLE) outline.
     * The palette is deliberately absent: writePreviewUniforms only reads it in TEXTURE_SAMPLE
     * mode, which is never cached.
     */
    private static String configFingerprint(ItemGlintRelightConfig config) {
        String cached = CONFIG_FINGERPRINTS.get(config);
        if (cached != null) return cached;
        StringBuilder fingerprint = new StringBuilder(160);
        fingerprint.append(config.outlinePrimaryColor()).append('|').append(config.outlineSecondaryColor())
                .append('|').append(config.outlineOpacity())
                .append('|').append(config.outlineWidth())
                .append('|').append(config.outlineSoftness())
                .append('|').append(config.outlineAlphaThreshold())
                .append('|').append(config.outlineQuality())
                .append('|').append(config.guiOutlineQuality())
                .append('|').append(config.outlineGlowIntensity())
                .append('|').append(config.outlineBloomEnabled())
                .append('|').append(config.outlineBloomQuality())
                .append('|').append(config.outlineBloomRadius())
                .append('|').append(config.outlineBloomIntensity())
                .append('|').append(config.outlineBloomSoftness())
                .append('|').append(config.outlineBloomBlurPasses())
                .append('|').append(config.outlineRenderMode())
                .append('|').append(config.outlineColorMode());
        String result = fingerprint.toString();
        CONFIG_FINGERPRINTS.put(config, result);
        return result;
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

    /**
     * Mirrors the reference GUI cache rule: sampled colors are part of the texture identity.
     * This is deliberately empty for every other color mode, preserving their existing cache
     * behavior and allocation profile.
     */
    private static String textureSampleIdentity(Capture capture) {
        ItemGlintRelightConfig config = capture.config();
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) return "";
        StringBuilder identity = new StringBuilder(96);
        identity.append(config.outlineSampleSize()).append('|').append(config.outlineSampleColorCount());
        for (float[] color : capture.materialPalette()) {
            if (color == null) continue;
            for (float channel : color) identity.append('|').append(Float.floatToIntBits(channel));
        }
        return identity.toString();
    }

    private static void trace(String event, String format, Object... arguments) {
        Object[] values = new Object[arguments.length + 1];
        values[0] = event;
        System.arraycopy(arguments, 0, values, 1, arguments.length);
        ItemGlintRelight.LOGGER.warn("[GuiOutlineTrace:{}] " + format, values);
    }

    private static float[][] materialPalette(ItemStack stack, ItemGlintRelightConfig config) {
        if (config.outlineColorMode() != celia.adwadg.itemglintrelight.config.OutlineColorMode.TEXTURE_SAMPLE) {
            return HeldItemOutlineRenderer.resolveGuiMaterialPalette(Minecraft.getInstance(), stack, config);
        }
        PaletteKey key = new PaletteKey(ItemStackKey.of(stack), config.outlineSampleSize(), config.outlineSampleColorCount());
        float[][] palette = MATERIAL_PALETTES.get(key);
        if (palette == null) {
            palette = HeldItemOutlineRenderer.resolveGuiMaterialPalette(Minecraft.getInstance(), stack, config);
            MATERIAL_PALETTES.put(key, palette);
        }
        return palette;
    }

    /** {@code stack} and {@code config} are borrowed, not owned: see capture(). */
    private record Capture(ItemStack stack, ItemGlintRelightConfig config, float[][] materialPalette, ItemStackKey key,
                           boolean stableMask) { }
    private record PaletteKey(ItemStackKey stack, int sampleSize, int colorCount) { }
    private record MaskKey(ItemStackKey stack, int width, int height, int precision) { }
    private record OutputKey(ItemStackKey stack, int width, int height, String textureSampleIdentity) { }
    private record CachedMask(TextureTarget target, long builtFrame) { }
    private record TargetShape(int width, int height, boolean depth) { }
    private record PooledTarget(TextureTarget target, long releasedFrame) { }
    private static final class CachedOutput {
        private final int width;
        private final int height;
        /**
         * Ring of textures this key has produced, grown on demand and never larger than
         * {@link #OUTPUT_TARGET_HISTORY_MAX}.
         *
         * <p>An entry is only handed out again once {@link #OUTPUT_TARGET_HISTORY} frames have passed
         * since it was written, which is the gap that stops a queued GUI blit from reading a texture a
         * later frame has rewritten. A key whose result is cached is therefore written exactly once
         * and settles at a single texture, where the previous fixed eight-entry ring wasted seven
         * textures per slot -- tens of megabytes across a full container or a recipe viewer.
         */
        private final TextureTarget[] targets = new TextureTarget[OUTPUT_TARGET_HISTORY_MAX];
        private final long[] writtenFrames = new long[OUTPUT_TARGET_HISTORY_MAX];
        private int targetCount;
        private long lastUsedFrame;
        private TextureTarget cachedTarget;
        private String cachedKey;
        /** Texture this key composited for the given frame, so later slots blit instead of redoing it. */
        private long producedFrame = Long.MIN_VALUE;
        private TextureTarget producedTarget;
        private TextureTarget bloomFirst;
        private TextureTarget bloomSecond;
        private String bloomKey;
        private GpuTextureView bloomView;

        private CachedOutput(int width, int height, long lastUsedFrame) {
            // No depth attachment: the GUI composite binds a constant far-depth stand-in and
            // never enables scene occlusion, so a per-slot depth buffer was pure overhead.
            this.width = width;
            this.height = height;
            this.lastUsedFrame = lastUsedFrame;
        }

        private long lastUsedFrame() { return lastUsedFrame; }
        private void markUsed(long frame) { lastUsedFrame = frame; }

        private TextureTarget cachedTarget() { return cachedTarget; }
        private String cachedKey() { return cachedKey; }

        private boolean producedThisFrame(long frame) {
            return producedFrame == frame && producedTarget != null;
        }

        private TextureTarget producedTarget() { return producedTarget; }

        private void markProduced(long frame, TextureTarget target) {
            producedFrame = frame;
            producedTarget = target;
        }

        private void markCached(TextureTarget target, String key) {
            cachedTarget = target;
            cachedKey = key;
        }

        private String bloomKey() { return bloomKey; }
        private GpuTextureView bloomView() { return bloomView; }

        private void markBloom(String key, GpuTextureView view) {
            bloomKey = key;
            bloomView = view;
        }

        /** Bloom scratch sized to this slot; recreated only when the slot size changes. */
        private TextureTarget[] bloomTargets(int width, int height) {
            if (bloomFirst == null || bloomFirst.width != width || bloomFirst.height != height) {
                releaseTarget(bloomFirst, false);
                releaseTarget(bloomSecond, false);
                bloomFirst = acquireTarget("itemglintrelight_gui_bloom_first", width, height, false);
                bloomSecond = acquireTarget("itemglintrelight_gui_bloom_second", width, height, false);
                bloomKey = null;
                bloomView = null;
            }
            return new TextureTarget[]{bloomFirst, bloomSecond};
        }

        private TextureTarget nextTarget(long frame) {
            for (int index = 0; index < targetCount; index++) {
                if (frame - writtenFrames[index] < OUTPUT_TARGET_HISTORY) continue;
                writtenFrames[index] = frame;
                return targets[index];
            }
            if (targetCount < OUTPUT_TARGET_HISTORY_MAX) {
                TextureTarget created = acquireTarget(
                        "itemglintrelight_gui_item_outline_" + targetCount, width, height, false);
                targets[targetCount] = created;
                writtenFrames[targetCount] = frame;
                targetCount++;
                return created;
            }
            // Pathological: this key produced more textures in one frame than the ring can hold apart.
            // Reusing the oldest write keeps the loop bounded. The per-frame production guard in
            // submit() means this is not reachable in normal use.
            int oldest = 0;
            for (int index = 1; index < targetCount; index++) {
                if (writtenFrames[index] < writtenFrames[oldest]) oldest = index;
            }
            writtenFrames[oldest] = frame;
            return targets[oldest];
        }

        private void destroy() {
            cachedTarget = null;
            cachedKey = null;
            producedFrame = Long.MIN_VALUE;
            producedTarget = null;
            bloomKey = null;
            bloomView = null;
            releaseTarget(bloomFirst, false);
            releaseTarget(bloomSecond, false);
            bloomFirst = null;
            bloomSecond = null;
            for (int index = 0; index < targetCount; index++) {
                TARGET_TRACES.remove(targets[index]);
                releaseTarget(targets[index], false);
                targets[index] = null;
            }
            targetCount = 0;
        }
    }
    private record ScreenSlot(int x, int y, int width, int height) { }
    private record SubmissionTrace(long frame, int stateId, String item, int paletteHash, int x, int y) {
        private boolean sameVisualIdentity(SubmissionTrace other) {
            return item.equals(other.item) && paletteHash == other.paletteHash;
        }

        private String describe() {
            return "frame=" + frame + ",state=" + stateId + ",item=" + item + ",palette=" + paletteHash + ",at=" + x + "," + y;
        }
    }
}
