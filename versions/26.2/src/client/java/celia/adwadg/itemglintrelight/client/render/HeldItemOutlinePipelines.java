package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import celia.adwadg.itemglintrelight.mixin.client.RenderPipelinesAccessor;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.resources.Identifier;

/**
 * Custom render pipelines for the item outline, ported to 26.2.
 *
 * <p>1.21.11 used {@code withSampler/withUniform}, {@code withVertexFormat}, {@code withBlend} and
 * {@code withDepthWrite/withDepthTestFunction}. 26.2 replaced those with a {@link BindGroupLayout}
 * per binding group, {@code withVertexBinding} + {@code withPrimitiveTopology},
 * a {@link ColorTargetState} carrying the blend function, and a {@link DepthStencilState}.
 * Registration moved behind the now-private {@code RenderPipelines#register}, reached through
 * {@link RenderPipelinesAccessor}.
 */
public final class HeldItemOutlinePipelines {
    private static final String OUTLINE_UNIFORM = "OutlineInfo";

    private static RenderPipeline build(String path, String fragmentShader, BindGroupLayout layout) {
        return RenderPipelinesAccessor.itemglintrelight$register(RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/" + path))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/" + fragmentShader))
                .withBindGroupLayout(layout)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build());
    }

    private static BindGroupLayout.Builder layout() {
        return BindGroupLayout.builder();
    }

    private static BindGroupLayout.Builder outlineUniform(BindGroupLayout.Builder builder) {
        return builder.withUniform(OUTLINE_UNIFORM, UniformType.UNIFORM_BUFFER);
    }

    private static final RenderPipeline OUTLINE = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_outline"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_outline"))
                    .withBindGroupLayout(outlineUniform(layout()
                            .withSampler("MaskSampler")
                            .withSampler("ItemDepthSampler")
                            .withSampler("SceneDepthSampler")
                            .withSampler("ArmOccluderSampler")
                            .withSampler("ArmOccluderDepthSampler")).build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

    private static final RenderPipeline BLOOM_BLUR = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_bloom_blur"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_bloom_blur"))
                    .withBindGroupLayout(layout()
                            .withSampler("InputSampler")
                            .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER).build())
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

    private static final RenderPipeline REGION_CLEAR = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/outline_region_clear"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/outline_region_clear"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

    private static final RenderPipeline BLOOM_COMPOSITE = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_bloom_composite"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_bloom_composite"))
                    .withBindGroupLayout(outlineUniform(layout()
                            .withSampler("BloomSampler")
                            .withSampler("MaskSampler")
                            .withSampler("ItemDepthSampler")
                            .withSampler("SceneDepthSampler")
                            .withSampler("ArmOccluderSampler")
                            .withSampler("ArmOccluderDepthSampler")).build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

    private static final RenderPipeline GUI_COMPOSITE = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/gui_item_outline_composite"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_gui_composite"))
                    .withBindGroupLayout(outlineUniform(layout()
                            .withSampler("MaskSampler")
                            .withSampler("BloomSampler")
                            .withSampler("ItemDepthSampler")).build())
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

    private static final RenderPipeline THIRD_PERSON_BLOOM_SEED = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/third_person_bloom_seed"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/third_person_bloom_seed"))
                    .withBindGroupLayout(layout()
                            .withSampler("MaskSampler")
                            .withSampler("ItemDepthSampler")
                            .withSampler("SceneDepthSampler").build())
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

    private static final RenderPipeline THIRD_PERSON_BLOOM_BLUR = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/third_person_bloom_blur"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/third_person_bloom_blur"))
                    .withBindGroupLayout(layout()
                            .withSampler("InputSampler")
                            .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER).build())
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

    private static final RenderPipeline THIRD_PERSON_BLOOM_COMPOSITE = RenderPipelinesAccessor.itemglintrelight$register(
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/third_person_bloom_composite"))
                    .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/third_person_bloom_composite"))
                    .withBindGroupLayout(outlineUniform(layout()
                            .withSampler("BloomSampler")
                            .withSampler("SeedSampler")
                            .withSampler("SceneDepthSampler")).build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .build());

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
