package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.swiftsmp.swiftclient.ServerPin;
import net.swiftsmp.swiftclient.SwiftClientMod;
import net.swiftsmp.swiftclient.ui.SwiftTitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** Swap the vanilla title screen for SwiftClient's, and keep SwiftSMP first in the list. */
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen swiftclient$screens(Screen screen) {
		Minecraft mc = (Minecraft) (Object) this;
		if (screen instanceof JoinMultiplayerScreen) ServerPin.pin(mc);
		return screen instanceof TitleScreen ? new SwiftTitleScreen() : screen;
	}

	/** Drives the menu/hud keys, presence and tweaks without Fabric API events. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void swiftclient$tick(CallbackInfo ci) {
		SwiftClientMod.onTick((Minecraft) (Object) this);
	}

	/** Window title: "SwiftClient 1.21.x" plus whatever vanilla appends. */
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void swiftclient$title(CallbackInfoReturnable<String> cir) {
		String vanilla = cir.getReturnValue();
		String version = SwiftClientMod.minecraftVersion();
		int dash = vanilla.indexOf(" - ");
		cir.setReturnValue("SwiftClient " + version + (dash >= 0 ? vanilla.substring(dash) : ""));
	}
}
