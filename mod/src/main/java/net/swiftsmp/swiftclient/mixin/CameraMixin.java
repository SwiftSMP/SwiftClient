package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Camera;
import net.swiftsmp.swiftclient.Tweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	/** Zoom: narrow the field of view while the zoom key is held. */
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void swiftclient$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		float z = Tweaks.zoom();
		if (z > 1.001f) cir.setReturnValue(cir.getReturnValue() / z);
	}
}
