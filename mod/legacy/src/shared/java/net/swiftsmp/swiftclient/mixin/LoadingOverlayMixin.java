package net.swiftsmp.swiftclient.mixin;

import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.swiftsmp.swiftclient.platform.GuiGfx;
import net.swiftsmp.swiftclient.platform.Platform;
import net.swiftsmp.swiftclient.ui.SwiftLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows SwiftClient branding during the early loading splash instead of the Mojang screen. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void swiftclient$splash(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		Platform.ensureSplash();
		if (SwiftLoadingScreen.render(new GuiGfx(g), this)) ci.cancel();
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int swiftclient$background(IntSupplier supplier) {
		return 0xFF0D1020;
	}
}
