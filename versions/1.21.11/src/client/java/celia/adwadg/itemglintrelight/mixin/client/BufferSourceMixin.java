package celia.adwadg.itemglintrelight.mixin.client;
import celia.adwadg.itemglintrelight.client.render.HeldItemOutlineRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin {
 @Inject(method="endBatch", at=@At("TAIL")) private void itemglintrelight$composite(CallbackInfo ci){ HeldItemOutlineRenderer.compositeThirdPersonAfterMainBatch(Minecraft.getInstance(), this); }
}
