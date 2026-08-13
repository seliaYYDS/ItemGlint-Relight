package celia.adwadg.itemglintrelight.client.render;

import celia.adwadg.itemglintrelight.ItemGlintRelight;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class HeldItemOutlinePipelines {
    private static final RenderPipeline OUTLINE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_outline"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_outline"))
            .withSampler("MaskSampler")
            .withSampler("ItemDepthSampler")
            .withSampler("SceneDepthSampler")
            .withSampler("ArmOccluderSampler")
            .withSampler("ArmOccluderDepthSampler")
            .withUniform("OutlineInfo", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build());
    private static final RenderPipeline BLOOM_BLUR = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_bloom_blur"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_bloom_blur"))
            .withSampler("InputSampler")
            .withUniform("BlurInfo", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build());
    private static final RenderPipeline BLOOM_COMPOSITE = RenderPipelines.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "pipeline/held_item_bloom_composite"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(ItemGlintRelight.MOD_ID, "core/held_item_bloom_composite"))
            .withSampler("BloomSampler")
            .withSampler("MaskSampler")
            .withSampler("ItemDepthSampler")
            .withSampler("SceneDepthSampler")
            .withUniform("OutlineInfo", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build());

    private HeldItemOutlinePipelines() { }

    public static RenderPipeline outline() {
        return OUTLINE;
    }

    public static RenderPipeline bloomBlur() {
        return BLOOM_BLUR;
    }

    public static RenderPipeline bloomComposite() {
        return BLOOM_COMPOSITE;
    }
}
