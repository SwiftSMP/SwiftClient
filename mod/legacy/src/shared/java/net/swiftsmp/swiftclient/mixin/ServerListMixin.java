package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.swiftsmp.swiftclient.ServerPin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned launcher servers cannot be removed from the saved server list. */
@Mixin(ServerList.class)
public abstract class ServerListMixin {
	@Inject(method = "remove", at = @At("HEAD"), cancellable = true)
	private void swiftclient$keepPinned(ServerData server, CallbackInfo ci) {
		if (ServerPin.isPinned(server)) ci.cancel();
	}
}
