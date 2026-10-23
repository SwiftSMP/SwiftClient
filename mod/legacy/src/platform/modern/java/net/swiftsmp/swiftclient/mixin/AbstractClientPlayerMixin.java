package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import net.swiftsmp.swiftclient.CapeManager;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.platform.Platform;
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
		if (self instanceof LocalPlayer && !SwiftConfig.get().showOwnCape) return;
		Object cape = CapeManager.capeFor(self.getUUID());
		if (!(cape instanceof ClientAsset.Texture)) return;
		cir.setReturnValue(Platform.withCape(cir.getReturnValue(), cape));
	}
}
