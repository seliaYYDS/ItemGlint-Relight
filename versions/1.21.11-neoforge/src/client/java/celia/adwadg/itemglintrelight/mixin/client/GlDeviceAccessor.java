package celia.adwadg.itemglintrelight.mixin.client;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GlDevice.class)
public interface GlDeviceAccessor {
    @Invoker("directStateAccess")
    DirectStateAccess itemglintrelight$getDirectStateAccess();
}
