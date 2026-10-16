package celia.adwadg.itemglintrelight.mixin.client;

import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
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
 * world pass.
 *
 * <p>26.3 dropped the {@code LevelRenderState} and {@code ProfilerFiller} arguments and added the
 * translucent-order flag; the hook does not read any of them.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMainPassMixin {
    @Inject(
            method = "addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V",
            at = @At("HEAD"))
    private void itemglintrelight$beginMainWorldPass(FrameGraphBuilder frameGraph,
                                                     FeatureRenderDispatcher.PreparedFrame frame,
                                                     GpuBufferSlice fog, ChunkSectionsToRender chunks,
                                                     boolean respectTranslucencyOrder, CallbackInfo ci) {
        HeldItemOutlineRenderer.beginMainWorldPass();
    }
}
