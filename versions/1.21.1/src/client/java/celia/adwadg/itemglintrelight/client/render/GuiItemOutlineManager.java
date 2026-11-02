package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.config.DisplayRuleManager;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfig;
import celia.adwadg.itemglintrelight.config.ItemGlintRelightConfigManager;
import celia.adwadg.itemglintrelight.config.OutlineColorMode;
import celia.adwadg.itemglintrelight.config.RenderQuality;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import celia.adwadg.itemglintrelight.mixin.client.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GUI item outline for 1.21.1.
 *
 * <p>The 1.21.11 implementation collects {@code GuiItemRenderState}s while {@code GuiGraphics}
 * submits them and composites them later, inside {@code GuiRenderer}. 1.21.1 has neither a GUI render
 * state nor a deferred GUI pass: every GUI primitive is drawn immediately. This class therefore does
 * the whole job synchronously, at the head of {@code GuiGraphics#renderItem}, and re-renders the item
 * into an off-screen mask instead of replaying a render state.
 *
 * <p>The composite is blitted <em>before</em> vanilla draws the item, which reproduces the reference
 * layering: the outline is submitted ahead of the item's own blit, so the item covers the inside of
 * the stroke.
 *
 * <p>Because nothing stays in flight after the blit, the reference's eight-frame output ring, target
 * pool and "produced this frame" guard are unnecessary here. The mask / bloom / output caches are
 * kept: without them a full inventory would re-render and recomposite every slot on every frame.
 */
public final class GuiItemOutlineManager {
    private static final float GUI_OUTLINE_SCALE = 8.0F;
    private static final float GUI_COLOR_SCROLL_SCALE = 10.0F;
    private static final float GUI_OUTLINE_MAX_WIDTH = 3.5F;
    private static final float GUI_BLOOM_SCALE = GUI_OUTLINE_SCALE * 0.12F;
    private static final int REFERENCE_RENDER_HEIGHT = 1080;

    private static final int MASK_CACHE_LIMIT = 128;
    private static final int OUTPUT_CACHE_LIMIT = 128;
    private static final int PALETTE_CACHE_LIMIT = 128;
    private static final int SPRITE_ALPHA_LIMIT = 2048;
    private static final int FULL_BRIGHT = 15728880;
    private static final int MAX_MASK_SIZE = 2048;

    private static final float[][] UNIT_PALETTE = {{1.0F, 1.0F, 1.0F}};

    private static final LinkedHashMap<MaskKey, MaskEntry> MASKS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MaskKey, MaskEntry> eldest) {
            if (size() <= MASK_CACHE_LIMIT) {
                return false;
            }
            eldest.getValue().destroy();
            return true;
        }
    };
    private static final LinkedHashMap<OutputKey, OutputEntry> OUTPUTS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<OutputKey, OutputEntry> eldest) {
            if (size() <= OUTPUT_CACHE_LIMIT) {
                return false;
            }
            eldest.getValue().destroy();
            return true;
        }
    };
    private static final LinkedHashMap<PaletteKey, float[][]> PALETTES = new LinkedHashMap<>(32, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PaletteKey, float[][]> eldest) {
            return size() > PALETTE_CACHE_LIMIT;
        }
    };
    private static final IdentityHashMap<SpriteContents, Boolean> SPRITE_ALPHA_STABILITY = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemStack, ItemStackKey> FRAME_KEYS = new IdentityHashMap<>();
    private static final LinkedHashMap<ItemStackKey, Boolean> STABILITY = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ItemStackKey, Boolean> eldest) {
            return size() > 512;
        }
    };
    private static final IdentityHashMap<ItemGlintRelightConfig, ItemGlintRelightConfig> GUI_CONFIGS = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemGlintRelightConfig, String> FINGERPRINTS = new IdentityHashMap<>();

    private static TextureTarget scratchMask;
    private static MaskEntry scratchEntry;
    private static RenderBuffers maskBuffers;
    private static int tooltipDepth;
    private static boolean inPass;
    private static long frame;
    private static int failures;

    private GuiItemOutlineManager() {
    }

    public static void beginFrame() {
        frame++;
        FRAME_KEYS.clear();
        GUI_CONFIGS.clear();
        FINGERPRINTS.clear();
    }

    public static void beginTooltipRender() {
        tooltipDepth++;
    }

    public static void endTooltipRender() {
        if (tooltipDepth > 0) {
            tooltipDepth--;
        }
    }

    /**
     * Called at the head of {@code GuiGraphics#renderItem(LivingEntity, Level, ItemStack, int, int, int, int)}.
     *
     * <p>That private overload is the one funnel every public draw path ends in, so it covers container
     * slots, the carried stack, {@code renderFakeItem} and creative-tab grids alike.
     */
    public static void renderBeforeItem(GuiGraphics graphics, LivingEntity entity, Level level, ItemStack stack,
                                        int x, int y, int seed) {
        if (inPass || tooltipDepth > 0 || graphics == null || stack == null || stack.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        ItemGlintRelightConfig base = ItemGlintRelightConfigManager.get();
        if (!base.outlineEnabled() || !base.renderGuiItems() || !base.outlineGuiItems()) {
            return;
        }
        ItemGlintRelightConfig config = DisplayRuleManager.resolve(stack, base);
        if (config == null || !config.outlineEnabled()) {
            return;
        }
        try {
            render(minecraft, graphics, guiConfig(config), stack, entity, level, x, y, seed);
        } catch (RuntimeException exception) {
            if (failures++ < 3) {
                ItemGlintRelight.LOGGER.error("[GuiOutline] GUI item outline failed", exception);
            }
        }
    }

    private static void render(Minecraft minecraft, GuiGraphics graphics, ItemGlintRelightConfig config,
                               ItemStack stack, LivingEntity entity, Level level, int x, int y, int seed) {
        int precision = precision(config.guiOutlineQuality());
        int padding = padding(config);
        int maskWidth = (16 + padding * 2) * precision;
        int maskHeight = maskWidth;
        if (maskWidth <= 0 || maskWidth > MAX_MASK_SIZE) {
            return;
        }

        ItemStackKey stackKey = keyOf(stack);
        float[][] palette = palette(stackKey, stack, level, entity, seed, config);

        // Everything below switches render targets; the GUI buffer must be empty first or vanilla's
        // pending geometry would be flushed into the off-screen mask.
        graphics.flush();

        GuiGlState state = GuiGlState.capture();
        inPass = true;
        try {
            // The screen's scissor is in window pixels; it would clip the small mask target away.
            RenderSystem.disableScissor();

            String fingerprint = configFingerprint(config);
            OutputKey outputKey = new OutputKey(stackKey, maskWidth, maskHeight, paletteHash(palette));
            OutputEntry output = OUTPUTS.get(outputKey);
            // A cached composite is reusable within the frame and, for the time-invariant single-colour
            // mode, across frames. Reusing it skips the mask lookup and the bloom key entirely -- on a
            // full creative tab that is the difference between "one blit per slot" and "rebuild the
            // bloom key string for every slot".
            boolean reusable = output != null && (output.producedFrame == frame
                    || (output.stable && config.outlineColorMode() == OutlineColorMode.SINGLE
                        && fingerprint.equals(output.fingerprint)));
            if (!reusable) {
                MaskEntry maskEntry = mask(minecraft, stackKey, stack, level, entity, seed, padding, precision,
                        maskWidth, maskHeight);
                int bloomTexture = config.outlineBloomEnabled()
                        ? bloom(minecraft, maskEntry, config, maskWidth, maskHeight) : 0;
                if (output == null) {
                    output = new OutputEntry(new TextureTarget(maskWidth, maskHeight, false, false));
                    OUTPUTS.put(outputKey, output);
                }
                composite(minecraft, output.target, maskEntry.target, bloomTexture, config, palette,
                        maskWidth, maskHeight, precision);
                output.producedFrame = frame;
                output.fingerprint = fingerprint;
                output.stable = maskEntry.stable;
            }

            state.restoreScissor();
            blit(minecraft, graphics, output.target, x - padding, y - padding, 16 + padding * 2);
        } finally {
            inPass = false;
            minecraft.getMainRenderTarget().bindWrite(true);
            state.restore();
        }
    }

    private static MaskEntry mask(Minecraft minecraft, ItemStackKey stackKey, ItemStack stack, Level level,
                                  LivingEntity entity, int seed, int padding, int precision,
                                  int width, int height) {
        MaskKey maskKey = new MaskKey(stackKey, width, height, precision);
        MaskEntry cached = MASKS.get(maskKey);
        if (cached != null) {
            return cached;
        }
        boolean stable = stableMask(minecraft, stackKey, stack, level, entity, seed);
        if (stable) {
            MaskEntry entry = new MaskEntry(new TextureTarget(width, height, true, false));
            entry.target.setFilterMode(GL11.GL_LINEAR);
            renderMask(minecraft, entry.target, stack, level, entity, seed, padding, precision);
            MASKS.put(maskKey, entry);
            return entry;
        }
        // Animated silhouettes cannot be cached; reuse one scratch target and rebuild it per draw.
        if (scratchMask == null || scratchMask.width != width || scratchMask.height != height) {
            if (scratchMask != null) {
                scratchMask.destroyBuffers();
            }
            scratchMask = new TextureTarget(width, height, true, false);
            scratchMask.setFilterMode(GL11.GL_LINEAR);
            if (scratchEntry != null) {
                scratchEntry.bloomFirst = null;
                scratchEntry.bloomSecond = null;
                scratchEntry.bloomTexture = 0;
            }
        }
        if (scratchEntry == null) {
            scratchEntry = new MaskEntry(scratchMask);
            scratchEntry.stable = false;
        }
        scratchEntry.target = scratchMask;
        renderMask(minecraft, scratchMask, stack, level, entity, seed, padding, precision);
        return scratchEntry;
    }

    private static void renderMask(Minecraft minecraft, TextureTarget target, ItemStack stack, Level level,
                                   LivingEntity entity, int seed, int padding, int precision) {
        int width = target.width;
        int height = target.height;
        // glClear honours the write masks, and the GUI pass leaves them anywhere; the outline reads
        // this texture's alpha as coverage, so a stale alpha channel would grow the stroke every frame.
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.clear(true);
        // RenderTarget#clear binds, clears and then unbinds the framebuffer.
        target.bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, width, height, 0.0F, 1000.0F, 21000.0F),
                VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        // Same convention as GameRenderer#render: the GUI projection needs this -11000 shift.
        modelView.translation(0.0F, 0.0F, -11000.0F);
        RenderSystem.applyModelViewMatrix();
        try {
            BakedModel model = minecraft.getItemRenderer().getModel(stack, level, entity, seed);
            Lighting.setupFor3DItems();
            boolean flat = !model.usesBlockLight();
            if (flat) {
                Lighting.setupForFlatItems();
            }
            PoseStack pose = new PoseStack();
            pose.translate(padding * precision + 8.0F * precision, padding * precision + 8.0F * precision, 0.0F);
            pose.scale(16.0F * precision, -16.0F * precision, 16.0F * precision);
            // A bare shared ByteBufferBuilder cannot render a foiled item: the item render type and
            // the glint render type are requested together, and the shared buffer ends the first batch
            // when the second type starts, which leaves the foil consumer writing into a built builder
            // ("Not building!"). RenderBuffers owns one builder per render type, exactly like vanilla.
            MultiBufferSource.BufferSource source = maskBuffers().bufferSource();
            minecraft.getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, source,
                    FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            source.endBatch();
            if (flat) {
                Lighting.setupFor3DItems();
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static int bloom(Minecraft minecraft, MaskEntry entry, ItemGlintRelightConfig config, int width, int height) {
        ShaderInstance blur = HeldItemOutlineRenderer.bloomBlurShader(minecraft);
        if (blur == null) {
            return 0;
        }
        if (entry.bloomTexture != 0 && entry.bloomWidth == width && entry.bloomHeight == height
                && entry.bloomRadius == config.outlineBloomRadius()
                && entry.bloomQuality == config.outlineBloomQuality()
                && entry.bloomPasses == config.outlineBloomBlurPasses()) {
            return entry.bloomTexture;
        }
        if (entry.bloomFirst == null || entry.bloomFirst.width != width || entry.bloomFirst.height != height) {
            if (entry.bloomFirst != null) {
                entry.bloomFirst.destroyBuffers();
                entry.bloomSecond.destroyBuffers();
            }
            entry.bloomFirst = new TextureTarget(width, height, false, false);
            entry.bloomSecond = new TextureTarget(width, height, false, false);
            entry.bloomFirst.setFilterMode(GL11.GL_LINEAR);
            entry.bloomSecond.setFilterMode(GL11.GL_LINEAR);
        }
        int passes = Math.max(1, config.outlineBloomBlurPasses());
        float radius = config.outlineBloomRadius() * 3.0F * 0.4F * height / REFERENCE_RENDER_HEIGHT
                * GUI_BLOOM_SCALE / (float) Math.sqrt(passes);
        int source = entry.target.getColorTextureId();
        for (int pass = 0; pass < passes; pass++) {
            source = blurPass(width, height, entry.bloomFirst, source, radius, 1.0F, 0.0F,
                    config.outlineBloomQuality(), blur);
            source = blurPass(width, height, entry.bloomSecond, source, radius, 0.0F, 1.0F,
                    config.outlineBloomQuality(), blur);
        }
        entry.bloomTexture = source;
        entry.bloomWidth = width;
        entry.bloomHeight = height;
        entry.bloomRadius = config.outlineBloomRadius();
        entry.bloomQuality = config.outlineBloomQuality();
        entry.bloomPasses = config.outlineBloomBlurPasses();
        return source;
    }

    private static int blurPass(int width, int height, TextureTarget destination, int source, float radius,
                                float directionX, float directionY, RenderQuality quality, ShaderInstance blur) {
        destination.bindWrite(true);
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

    private static void composite(Minecraft minecraft, TextureTarget target, TextureTarget mask, int bloomTexture,
                                  ItemGlintRelightConfig config, float[][] palette, int width, int height,
                                  int precision) {
        ShaderInstance shader = HeldItemOutlineRenderer.guiCompositeShader(minecraft);
        if (shader == null) {
            return;
        }
        target.bindWrite(true);
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.setShader(() -> shader);
        shader.setSampler("MaskSampler", mask.getColorTextureId());
        // With bloom disabled the shader never samples BloomSampler; feeding it the mask keeps every
        // declared sampler bound to a live texture.
        shader.setSampler("BloomSampler", bloomTexture == 0 ? mask.getColorTextureId() : bloomTexture);
        shader.setSampler("ItemDepthSampler", mask.getDepthTextureId());
        HeldItemOutlineRenderer.writeGuiUniforms(shader, width, height, config, palette,
                GUI_OUTLINE_SCALE, GUI_COLOR_SCROLL_SCALE / precision, bloomTexture != 0);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        HeldItemOutlineRenderer.drawFullscreenQuad();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void blit(Minecraft minecraft, GuiGraphics graphics, TextureTarget texture,
                             int left, int top, int size) {
        minecraft.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture.getColorTextureId());
        Matrix4f matrix = graphics.pose().last().pose();
        // The mask is rendered with the GUI's y-down ortho, so its v=1 row is the item's top row.
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, left, top, 150.0F).setUv(0.0F, 1.0F);
        builder.addVertex(matrix, left, top + size, 150.0F).setUv(0.0F, 0.0F);
        builder.addVertex(matrix, left + size, top + size, 150.0F).setUv(1.0F, 0.0F);
        builder.addVertex(matrix, left + size, top, 150.0F).setUv(1.0F, 1.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        GlStateManager._bindTexture(0);
    }

    private static ItemGlintRelightConfig guiConfig(ItemGlintRelightConfig source) {
        ItemGlintRelightConfig cached = GUI_CONFIGS.get(source);
        if (cached != null) {
            return cached;
        }
        ItemGlintRelightConfig config = source.copy();
        // GUI masks become visually unstable beyond this width in the reference implementation.
        config.setOutlineWidth(Math.min(config.outlineWidth(), GUI_OUTLINE_MAX_WIDTH));
        GUI_CONFIGS.put(source, config);
        return config;
    }

    private static int padding(ItemGlintRelightConfig config) {
        int padding = 3 + (int) Math.ceil(config.outlineWidth() * 4.5F + config.outlineSoftness() * 2.0F);
        if (config.outlineBloomEnabled()) {
            padding += (int) Math.ceil(config.outlineBloomRadius() * 2.0F);
        }
        return padding;
    }

    private static int precision(RenderQuality quality) {
        return switch (quality) {
            case LOW -> 1;
            case HIGH -> 3;
            default -> 2;
        };
    }

    private static float[][] palette(ItemStackKey stackKey, ItemStack stack, Level level, LivingEntity entity,
                                     int seed, ItemGlintRelightConfig config) {
        if (config.outlineColorMode() != OutlineColorMode.TEXTURE_SAMPLE) {
            return UNIT_PALETTE;
        }
        PaletteKey key = new PaletteKey(stackKey, config.outlineSampleSize(), config.outlineSampleColorCount());
        float[][] cached = PALETTES.get(key);
        if (cached != null) {
            return cached;
        }
        float[][] resolved = HeldItemOutlineRenderer.resolveGuiMaterialPalette(stack, level, entity, seed, config);
        if (resolved == null || resolved.length == 0) {
            resolved = UNIT_PALETTE;
        }
        PALETTES.put(key, resolved);
        return resolved;
    }

    private static ItemStackKey keyOf(ItemStack stack) {
        ItemStackKey cached = FRAME_KEYS.get(stack);
        if (cached != null && cached.matchesCount(stack)) {
            return cached;
        }
        ItemStackKey key = ItemStackKey.of(stack);
        FRAME_KEYS.put(stack, key);
        return key;
    }

    private static int paletteHash(float[][] palette) {
        if (palette == null || palette.length == 0) {
            return 0;
        }
        int hash = 1;
        for (float[] color : palette) {
            for (float channel : color) {
                hash = 31 * hash + Float.floatToIntBits(channel);
            }
        }
        return 31 * hash + palette.length;
    }

    private static String configFingerprint(ItemGlintRelightConfig config) {
        String cached = FINGERPRINTS.get(config);
        if (cached != null) {
            return cached;
        }
        String value = config.outlineWidth() + "|" + config.outlineOpacity() + "|" + config.outlineSoftness()
                + "|" + config.outlineAlphaThreshold() + "|" + config.outlineRenderMode()
                + "|" + config.outlineColorMode() + "|" + config.outlinePrimaryColor()
                + "|" + config.outlineSecondaryColor() + "|" + config.outlineColorScrollSpeed()
                + "|" + config.outlineColorScrollDirection() + "|" + config.outlineColorScrollInterval()
                + "|" + config.outlineColorScrollMode() + "|" + config.outlineGlowIntensity()
                + "|" + config.outlineQuality() + "|" + config.outlineBloomEnabled()
                + "|" + config.outlineBloomRadius() + "|" + config.outlineBloomIntensity()
                + "|" + config.outlineBloomSoftness() + "|" + config.outlineBloomBlurPasses()
                + "|" + config.outlineBloomQuality() + "|" + config.outlineSampleSize()
                + "|" + config.outlineSampleColorCount();
        FINGERPRINTS.put(config, value);
        return value;
    }

    private static RenderBuffers maskBuffers() {
        if (maskBuffers == null) {
            maskBuffers = new RenderBuffers(1);
        }
        return maskBuffers;
    }

    /**
     * Whether the rendered silhouette is frame-invariant.
     *
     * <p>1.21.1 has no {@code ItemStackRenderState#isAnimated()}, so the equivalent test is done on the
     * model's own quads: only a genuinely animated sprite can move the mask's alpha channel. The
     * enchantment glint is drawn with an EQUAL depth test inside the item's own silhouette, so it can
     * never change the coverage boundary the outline reads.
     */
    private static boolean stableMask(Minecraft minecraft, ItemStackKey stackKey, ItemStack stack, Level level,
                                      LivingEntity entity, int seed) {
        Boolean cached = STABILITY.get(stackKey);
        if (cached != null) {
            return cached;
        }
        boolean stable = isMaskStable(minecraft, stack, level, entity, seed);
        STABILITY.put(stackKey, stable);
        return stable;
    }

    private static boolean isMaskStable(Minecraft minecraft, ItemStack stack, Level level, LivingEntity entity, int seed) {
        BakedModel model = minecraft.getItemRenderer().getModel(stack, level, entity, seed);
        for (BakedQuad quad : model.getQuads(null, null, RandomSource.create(42L))) {
            if (quad == null) {
                continue;
            }
            TextureAtlasSprite sprite = quad.getSprite();
            if (sprite == null) {
                continue;
            }
            SpriteContents contents = sprite.contents();
            if (contents == null || contents.getUniqueFrames().count() <= 1) {
                continue;
            }
            if (!spriteAlphaStable(contents)) {
                return false;
            }
        }
        return true;
    }

    private static boolean spriteAlphaStable(SpriteContents contents) {
        Boolean cached = SPRITE_ALPHA_STABILITY.get(contents);
        if (cached != null) {
            return cached;
        }
        boolean stable = scanSpriteAlpha(contents);
        if (SPRITE_ALPHA_STABILITY.size() >= SPRITE_ALPHA_LIMIT) {
            SPRITE_ALPHA_STABILITY.clear();
        }
        SPRITE_ALPHA_STABILITY.put(contents, stable);
        return stable;
    }

    /**
     * Compares the alpha channel of every distinct animation frame against the first frame.
     *
     * <p>Frames are packed as a row-major grid of {@code width x height} cells inside mip level 0.
     * Every uncertain branch answers "unstable": a sheet whose dimensions are not an exact multiple of
     * the frame size, or a frame index the grid does not cover, is never assumed to be safe.
     */
    private static boolean scanSpriteAlpha(SpriteContents contents) {
        NativeImage[] mipLevels = ((SpriteContentsAccessor) (Object) contents).itemglintrelight$getByMipLevel();
        NativeImage sheet = mipLevels == null || mipLevels.length == 0 ? null : mipLevels[0];
        int width = contents.width();
        int height = contents.height();
        if (sheet == null || width <= 0 || height <= 0) {
            return false;
        }
        int[] frames = contents.getUniqueFrames().toArray();
        if (frames.length <= 1) {
            return true;
        }
        int sheetWidth = sheet.getWidth();
        int sheetHeight = sheet.getHeight();
        if (sheetWidth % width != 0 || sheetHeight % height != 0) {
            return false;
        }
        int columns = sheetWidth / width;
        int rows = sheetHeight / height;
        if (columns * rows <= 1) {
            return false;
        }
        for (int frame : frames) {
            if (frame < 0 || frame >= columns * rows) {
                return false;
            }
        }
        for (int frame : frames) {
            if (frame == 0) {
                continue;
            }
            int originX = (frame % columns) * width;
            int originY = (frame / columns) * height;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((sheet.getPixelRGBA(originX + x, originY + y) >>> 24) != (sheet.getPixelRGBA(x, y) >>> 24)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private record MaskKey(ItemStackKey stack, int width, int height, int precision) {
    }

    private record OutputKey(ItemStackKey stack, int width, int height, int paletteHash) {
    }

    private record PaletteKey(ItemStackKey stack, int sampleSize, int colorCount) {
    }

    private static final class MaskEntry {
        private TextureTarget target;
        private boolean stable = true;
        private TextureTarget bloomFirst;
        private TextureTarget bloomSecond;
        private int bloomWidth;
        private int bloomHeight;
        private float bloomRadius;
        private RenderQuality bloomQuality;
        private int bloomPasses;
        private int bloomTexture;

        private MaskEntry(TextureTarget target) {
            this.target = target;
        }

        private void destroy() {
            if (this.target != null) {
                this.target.destroyBuffers();
            }
            if (this.bloomFirst != null) {
                this.bloomFirst.destroyBuffers();
            }
            if (this.bloomSecond != null) {
                this.bloomSecond.destroyBuffers();
            }
            this.bloomTexture = 0;
        }
    }

    private static final class OutputEntry {
        private final TextureTarget target;
        private long producedFrame = Long.MIN_VALUE;
        private String fingerprint = "";
        private boolean stable = true;

        private OutputEntry(TextureTarget target) {
            this.target = target;
        }

        private void destroy() {
            this.target.destroyBuffers();
        }
    }
}
