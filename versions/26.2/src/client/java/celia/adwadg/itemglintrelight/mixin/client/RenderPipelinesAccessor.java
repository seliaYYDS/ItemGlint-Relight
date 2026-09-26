package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 26.2 made {@code RenderPipelines#register} private. Custom pipelines still have to land in the
 * same {@code PIPELINES_BY_LOCATION} map, because {@code ShaderManager} precompiles exactly the
 * pipelines returned by {@code RenderPipelines#getStaticPipelines}. This invoker restores the
 * registration entry point the mod used in 1.21.11.
 */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Invoker("register")
    static RenderPipeline itemglintrelight$register(RenderPipeline pipeline) {
        throw new AssertionError("mixin invoker not applied");
    }
}
