package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.swiftsmp.swiftclient.hud.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** Counts in-game mouse presses for the CPS and keystrokes modules. */
	@Inject(method = "onButton", at = @At("HEAD"))
	private void swiftclient$cps(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1 && Minecraft.getInstance().gui.screen() == null) Modules.CpsTracker.click(info.button());
	}
}
