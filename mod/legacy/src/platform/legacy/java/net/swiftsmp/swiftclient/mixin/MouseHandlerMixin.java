package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.swiftsmp.swiftclient.hud.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** Counts in-game mouse presses for the CPS module. 1.21 - 1.21.8 pass raw button ids. */
	@Inject(method = "onPress", at = @At("HEAD"))
	private void swiftclient$cps(long window, int button, int action, int modifiers, CallbackInfo ci) {
		if (action == 1 && Minecraft.getInstance().screen == null) Modules.CpsTracker.click(button);
	}
}
