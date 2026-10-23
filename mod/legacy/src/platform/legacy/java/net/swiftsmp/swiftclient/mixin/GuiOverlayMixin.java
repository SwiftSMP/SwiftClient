package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Visibility tab: hide vanilla HUD parts on request. require = 0 keeps moved methods harmless. */
@Mixin(Gui.class)
public abstract class GuiOverlayMixin {
	@Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
	private void swiftclient$scoreboard(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (SwiftConfig.get().hideScoreboard) ci.cancel();
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
	private void swiftclient$effects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (SwiftConfig.get().hideEffects) ci.cancel();
	}

	@Inject(method = "renderTitle", at = @At("HEAD"), cancellable = true, require = 0)
	private void swiftclient$titles(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (SwiftConfig.get().hideTitles) ci.cancel();
	}

	@Inject(method = "renderBossOverlay", at = @At("HEAD"), cancellable = true, require = 0)
	private void swiftclient$bossBar(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (SwiftConfig.get().hideBossBar) ci.cancel();
	}

	@Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true, require = 0)
	private void swiftclient$heldItem(GuiGraphics g, CallbackInfo ci) {
		if (SwiftConfig.get().hideHeldItemName) ci.cancel();
	}
}
