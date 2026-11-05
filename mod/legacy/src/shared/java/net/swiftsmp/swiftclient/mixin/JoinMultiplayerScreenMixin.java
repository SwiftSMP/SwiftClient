package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.swiftsmp.swiftclient.ServerPin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Delete disabled for SwiftSMP and partner servers pinned by the launcher. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
	@Shadow protected ServerSelectionList serverSelectionList;
	@Shadow private Button deleteButton;

	@Inject(method = "onSelectedChange", at = @At("TAIL"))
	private void swiftclient$keepPinned(CallbackInfo ci) {
		if (serverSelectionList == null) return;
		ServerSelectionList.Entry selected = serverSelectionList.getSelected();
		if (selected instanceof ServerSelectionList.OnlineServerEntry entry && ServerPin.isPinned(entry.getServerData())) {
			deleteButton.active = false;
		}
	}
}
