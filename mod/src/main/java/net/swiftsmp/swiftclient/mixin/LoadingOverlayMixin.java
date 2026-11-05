package net.swiftsmp.swiftclient.mixin;

import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.swiftsmp.swiftclient.ui.SwiftLoadingScreen;
import net.swiftsmp.swiftclient.ui.SwiftSplash;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows SwiftClient branding during the early loading splash instead of the Mojang screen. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void swiftclient$splash(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SwiftSplash.ensure();
		if (SwiftLoadingScreen.render(g, this)) ci.cancel();
	}

	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int swiftclient$background(IntSupplier supplier) {
		return 0xFF0D1020;
	}
}
