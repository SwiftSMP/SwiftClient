package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import net.swiftsmp.swiftclient.CapeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	/** Swap in the player's SwiftClient cape (also used for the elytra unless they have a custom one). */
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void swiftclient$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (self instanceof net.minecraft.client.player.LocalPlayer && !net.swiftsmp.swiftclient.config.SwiftConfig.get().showOwnCape) return;
		ClientAsset.Texture cape = CapeManager.capeFor(self.getUUID());
		if (cape == null) return;
		PlayerSkin skin = cir.getReturnValue();
		ClientAsset.Texture elytra = skin.elytra() != null ? skin.elytra() : cape;
		cir.setReturnValue(new PlayerSkin(skin.body(), cape, elytra, skin.model(), skin.secure()));
	}
}
