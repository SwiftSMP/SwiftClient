package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.swiftsmp.swiftclient.ui.SwiftTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {
	/** Swap the vanilla title screen for SwiftClient's. */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen swiftclient$titleScreen(Screen screen) {
		return screen instanceof TitleScreen ? new SwiftTitleScreen() : screen;
	}
}
