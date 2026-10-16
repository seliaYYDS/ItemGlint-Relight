package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the graphics backend behind 26.2's device wrapper.
 *
 * <p>26.2 renders through {@code GpuDevice}: {@code RenderSystem.getDevice()} returns
 * {@code com.mojang.renderpearl.api.device.GpuDevice}, not the OpenGL backend, and the wrapper offers no
 * way back to it. The backend is whatever implements {@code GpuDeviceBackend} -- on the OpenGL path
 * that is {@code com.mojang.renderpearl.backend.opengl.GlDevice}, the only object that can hand out the
 * {@code DirectStateAccess} framebuffer lookups need.
 */
@Mixin(FrontendGpuDevice.class)
public interface GpuDeviceAccessor {
    /**
     * Typed as the public {@link GpuDeviceBackend} interface: an {@code @Accessor} signature has to
     * match the field's own type, and the concrete OpenGL backend is package-private.
     */
    @Accessor("backend")
    GpuDeviceBackend itemglintrelight$getBackend();
}
