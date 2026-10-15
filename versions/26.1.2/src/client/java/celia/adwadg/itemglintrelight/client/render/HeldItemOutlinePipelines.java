package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.mixin.client.RenderPipelinesAccessor;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

/**
 * Custom render pipelines for the item outline, back-ported to 26.1.2.
 *
 * <p>26.2 split the old sampler/uniform lists out into a {@code BindGroupLayout} and replaced
 * {@code VertexFormat.Mode} with the top-level {@code PrimitiveTopology} enum. 26.1.2 still uses the
 * pre-26.2 shape: samplers and uniforms are declared directly on the builder and the primitive mode
 * rides along with {@code withVertexFormat}. Everything that 26.2 moved into a {@code ColorTargetState}
 * (blending) and a {@code DepthStencilState} is unchanged, because 26.1 already had both.
 *
 * <p>{@code RenderPipelines#register} is private in 26.1.2 exactly as in 26.2, so registration still
 * goes through {@link RenderPipelinesAccessor}.
 */
public final class HeldItemOutlinePipelines {
    private static final VertexFormat EMPTY = DefaultVertexFormat.EMPTY;
    private static final VertexFormat.Mode TRIANGLES = VertexFormat.Mode.TRIANGLES;

    private static final RenderPipeline OUTLINE = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_outline"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_outline"))
            .withSampler("MaskSampler")
            .withSampler("ItemDepthSampler")
            .withSampler("SceneDepthSampler")
            .withSampler("ArmOccluderSampler")
            .withSampler("ArmOccluderDepthSampler")
            .withUniform("OutlineInfo", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    private static final RenderPipeline BLOOM_BLUR = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_bloom_blur"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_bloom_blur"))
            .withSampler("InputSampler")
            .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    /**
     * Writes transparent black with blending disabled, so a scissored pass can clear exactly the
     * region a bloom chain touches instead of the whole target.
     */
    private static final RenderPipeline REGION_CLEAR = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/outline_region_clear"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/outline_region_clear"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    private static final RenderPipeline BLOOM_COMPOSITE = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_bloom_composite"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_bloom_composite"))
            .withSampler("BloomSampler")
            .withSampler("MaskSampler")
            .withSampler("ItemDepthSampler")
            .withSampler("SceneDepthSampler")
            .withSampler("ArmOccluderSampler")
            .withSampler("ArmOccluderDepthSampler")
            .withUniform("OutlineInfo", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    /**
     * GUI-only single-pass composite: the GUI pins {@code scrollBounds.z} to 1.0, so scene occlusion
     * is off there and the scene-depth and arm-occluder terms can never change a pixel. Those samplers
     * are dropped instead of being fed the 1x1 placeholders.
     */
    private static final RenderPipeline GUI_COMPOSITE = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/gui_item_outline_composite"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_gui_composite"))
            .withSampler("MaskSampler")
            .withSampler("BloomSampler")
            .withSampler("ItemDepthSampler")
            .withUniform("OutlineInfo", UniformType.UNIFORM_BUFFER)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    private static final RenderPipeline THIRD_PERSON_BLOOM_SEED = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/third_person_bloom_seed"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/third_person_bloom_seed"))
            .withSampler("MaskSampler")
            .withSampler("ItemDepthSampler")
            .withSampler("SceneDepthSampler")
            .withUniform("OutlineInfo", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    private static final RenderPipeline THIRD_PERSON_BLOOM_BLUR = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/third_person_bloom_blur"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/third_person_bloom_blur"))
            .withSampler("InputSampler")
            .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    private static final RenderPipeline THIRD_PERSON_BLOOM_COMPOSITE = register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/third_person_bloom_composite"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/third_person_bloom_composite"))
            .withSampler("BloomSampler")
            .withSampler("SeedSampler")
            .withSampler("SceneDepthSampler")
            .withUniform("OutlineInfo", UniformType.UNIFORM_BUFFER)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withVertexFormat(EMPTY, TRIANGLES)
            .build());

    private static RenderPipeline register(RenderPipeline pipeline) {
        return RenderPipelinesAccessor.itemglintrelight$register(pipeline);
    }

    private HeldItemOutlinePipelines() { }

    public static RenderPipeline outline() { return OUTLINE; }
    public static RenderPipeline bloomBlur() { return BLOOM_BLUR; }
    public static RenderPipeline regionClear() { return REGION_CLEAR; }
    public static RenderPipeline bloomComposite() { return BLOOM_COMPOSITE; }
    public static RenderPipeline guiComposite() { return GUI_COMPOSITE; }
    public static RenderPipeline thirdPersonBloomSeed() { return THIRD_PERSON_BLOOM_SEED; }
    public static RenderPipeline thirdPersonBloomBlur() { return THIRD_PERSON_BLOOM_BLUR; }
    public static RenderPipeline thirdPersonBloomComposite() { return THIRD_PERSON_BLOOM_COMPOSITE; }
}
