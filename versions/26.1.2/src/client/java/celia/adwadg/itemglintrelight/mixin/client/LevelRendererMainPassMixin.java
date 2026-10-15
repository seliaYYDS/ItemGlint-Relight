package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 26.1.2 replacement for the Fabric {@code WorldRenderEvents.START_MAIN} hook the 1.21.11 build
 * used to call {@link HeldItemOutlineRenderer#beginMainWorldPass()}.
 *
 * <p>The hook belongs at {@code LevelRenderer#addMainPass}: Iris renders its shadow pass from an
 * injection on the {@code addMainPass} <em>call site</em> inside {@code LevelRenderer#render}, so a
 * HEAD injection on the method itself sits after every pre-pass and exactly at the start of the main
 * world pass. Nothing earlier qualifies -- {@code LevelRenderer#render} HEAD runs before the pre-passes
 * the hook exists to discard, and the feature phases already run inside the pass.
 *
 * <p>26.1.2 spells the parameters differently from 26.2 (a {@code Frustum}, the model-view matrix, the
 * outline flag, the delta tracker and the chunk sections are still passed explicitly; there is no
 * {@code PreparedFrame} yet), so this descriptor has to match that list exactly.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMainPassMixin {
    @Inject(
            method = "addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginMainWorldPass(FrameGraphBuilder frameGraph,
                                                     Frustum frustum,
                                                     Matrix4fc modelViewMatrix,
                                                     GpuBufferSlice fog,
                                                     boolean renderOutline,
                                                     LevelRenderState renderState,
                                                     DeltaTracker deltaTracker,
                                                     ProfilerFiller profiler,
                                                     ChunkSectionsToRender chunks,
                                                     CallbackInfo ci) {
        HeldItemOutlineRenderer.beginMainWorldPass();
    }
}
