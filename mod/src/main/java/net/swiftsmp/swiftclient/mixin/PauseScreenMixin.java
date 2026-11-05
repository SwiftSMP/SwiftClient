package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.swiftsmp.swiftclient.Compat;
import net.swiftsmp.swiftclient.ui.SwiftMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lunar-style pause menu entry: a SwiftClient button under the vanilla buttons. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
	@Inject(method = "init", at = @At("TAIL"))
	private void swiftclient$menuButton(CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		int bw = 120;
		int bx = self.width / 2 - bw / 2;
		int by = self.height - 28;
		((ScreenInvoker) this).swiftclient$addRenderableWidget(
			Button.builder(Component.literal("SwiftClient"), b -> Compat.setScreen(new SwiftMenuScreen()))
				.bounds(bx, by, bw, 20)
				.build());
	}
}
