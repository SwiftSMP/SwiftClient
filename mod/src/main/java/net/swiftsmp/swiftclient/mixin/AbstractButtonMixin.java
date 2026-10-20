package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.swiftsmp.swiftclient.ui.Theme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	/** Every vanilla button (Options, Done, pause menu, icon buttons...) gets a SwiftSMP glass background. */
	@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void swiftclient$glassButton(GuiGraphicsExtractor g, CallbackInfo ci) {
		AbstractWidget w = (AbstractWidget) (Object) this;
		Theme.vanillaButton(g, w.getX(), w.getY(), w.getWidth(), w.getHeight(), w.isActive(), w.isHoveredOrFocused());
		ci.cancel();
	}
}
