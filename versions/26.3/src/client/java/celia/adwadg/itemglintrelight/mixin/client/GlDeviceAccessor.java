package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.renderpearl.backend.opengl.DirectStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code GlDevice} is package-private in 26.2, so it is targeted by name and this interface is mixed
 * into it. Callers test {@code instanceof GlDeviceAccessor} rather than referencing the class, which
 * keeps them compiling against the public {@code GpuDevice} surface only.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice")
public interface GlDeviceAccessor {
    @Invoker("directStateAccess")
    DirectStateAccess itemglintrelight$getDirectStateAccess();
}
