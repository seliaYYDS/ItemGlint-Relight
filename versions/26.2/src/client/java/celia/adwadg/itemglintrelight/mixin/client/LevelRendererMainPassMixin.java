package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 26.2 replacement for the Fabric {@code WorldRenderEvents.START_MAIN} hook the 1.21.11 build used
 * to call {@link HeldItemOutlineRenderer#beginMainWorldPass()}.
 *
 * <p>The hook belongs at {@code LevelRenderer#addMainPass}: Iris renders its shadow pass from an
 * injection on the {@code addMainPass} <em>call site</em> inside {@code LevelRenderer#render}, so a
 * HEAD injection on the method itself sits after every pre-pass and exactly at the start of the main
 * world pass. Nothing earlier qualifies -- {@code LevelRenderer#render} HEAD runs before the pre-passes
 * the hook exists to discard, and the feature phases ({@code executeSolid} &amp; friends) already run
 * inside the pass.
 *
 * <p>It is also safe for the hand capture: {@code GameRenderer#renderLevel} calls
 * {@code LevelRenderer#render} (offset 405) and only afterwards {@code renderItemInHand} (offset 508),
 * so the hand window this resets has not opened yet when the hook fires. Under Iris the hand pass is
 * driven inside the main pass as well, i.e. still after this point.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMainPassMixin {
    @Inject(
            method = "addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginMainWorldPass(FrameGraphBuilder frameGraph,
                                                     FeatureRenderDispatcher.PreparedFrame frame,
                                                     GpuBufferSlice fog, LevelRenderState renderState,
                                                     ProfilerFiller profiler, ChunkSectionsToRender chunks,
                                                     CallbackInfo ci) {
        HeldItemOutlineRenderer.beginMainWorldPass();
    }
}
