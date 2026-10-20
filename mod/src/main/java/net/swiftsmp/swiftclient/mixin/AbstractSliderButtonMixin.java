package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.swiftsmp.swiftclient.ui.Theme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	@Shadow
	protected double value;

	/** Sliders (FOV, volume, render distance...): glass track, orange fill, white knob. */
	@Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
	private void swiftclient$glassSlider(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		AbstractWidget w = (AbstractWidget) (Object) this;
		int x = w.getX(), y = w.getY(), width = w.getWidth(), h = w.getHeight();
		boolean hot = w.isHoveredOrFocused() && w.isActive();
		Theme.vanillaButton(g, x, y, width, h, w.isActive(), false);
		int fill = (int) Math.round(value * (width - 4));
		if (fill > 2) Theme.round(g, x + 2, y + 2, fill, h - 4, 3, Theme.alpha(Theme.ACCENT, w.isActive() ? (hot ? 0.55f : 0.4f) : 0.15f));
		int kx = x + 2 + (int) Math.round(value * (width - 10));
		if (hot) Theme.glow(g, kx, y + 2, 6, h - 4, 3, 1f);
		Theme.round(g, kx, y + 2, 6, h - 4, 3, w.isActive() ? (hot ? 0xFFFFFFFF : 0xFFE6E8F2) : Theme.FAINT);
		Font font = Minecraft.getInstance().font;
		Component msg = w.getMessage();
		g.centeredText(font, msg, x + width / 2, y + (h - 8) / 2, w.isActive() ? 0xFFFFFFFF : Theme.MUTED);
		ci.cancel();
	}
}
