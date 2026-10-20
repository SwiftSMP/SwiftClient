package net.swiftsmp.swiftclient.mixin;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** Window title: "SwiftClient 26.3" plus whatever vanilla appends (e.g. "- Multiplayer (3rd-party Server)"). */
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void swiftclient$title(CallbackInfoReturnable<String> cir) {
		String vanilla = cir.getReturnValue();
		String version = SharedConstants.getCurrentVersion().name();
		int dash = vanilla.indexOf(" - ");
		cir.setReturnValue("SwiftClient " + version + (dash >= 0 ? vanilla.substring(dash) : ""));
	}
}
