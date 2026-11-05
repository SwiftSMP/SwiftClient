package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.swiftsmp.swiftclient.ui.SwiftTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Esc / Cancel in Singleplayer world selection returns to the SwiftClient title. */
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin {
	@Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
	private void swiftclient$backToTitle(CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			mc.setScreen(new SwiftTitleScreen());
			ci.cancel();
		}
	}
}
