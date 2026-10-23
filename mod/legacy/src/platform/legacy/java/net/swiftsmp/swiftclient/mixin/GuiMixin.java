package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.swiftsmp.swiftclient.hud.HudRenderer;
import net.swiftsmp.swiftclient.platform.GuiGfx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** SwiftClient's HUD modules, drawn over the vanilla HUD. */
	@Inject(method = "render", at = @At("TAIL"))
	private void swiftclient$hud(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
		HudRenderer.render(new GuiGfx(graphics));
	}
}
