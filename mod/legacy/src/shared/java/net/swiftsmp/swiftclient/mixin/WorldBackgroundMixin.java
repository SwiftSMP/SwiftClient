package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.swiftsmp.swiftclient.platform.GuiGfx;
import net.swiftsmp.swiftclient.ui.Theme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the Singleplayer world screens the same HD backdrop and brand as the title. */
@Mixin(Screen.class)
public abstract class WorldBackgroundMixin {
	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void swiftclient$worldBackground(GuiGraphics graphics, int mx, int my, float delta, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!(self instanceof SelectWorldScreen || self instanceof CreateWorldScreen)) return;
		if (Minecraft.getInstance().level != null) return;
		Theme.titleBackground(new GuiGfx(graphics), self.width, self.height);
		graphics.fillGradient(0, 0, self.width, self.height, Theme.alpha(Theme.BG_DEEP, 0.34f), Theme.alpha(Theme.BG_DEEP, 0.62f));
		ci.cancel();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void swiftclient$brand(GuiGraphics graphics, int mx, int my, float delta, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (!(self instanceof SelectWorldScreen || self instanceof CreateWorldScreen)) return;
		GuiGfx g = new GuiGfx(graphics);
		int y = self.height - 26;
		Theme.glass(g, 8, y, 92, 18, 9, Theme.GLASS_LIGHT);
		Theme.logo(g, 11, y + 2, 14);
		g.text(Theme.font(), Theme.wordmark(), 29, y + 4, Theme.TEXT, false);
	}
}
