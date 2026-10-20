package net.swiftsmp.swiftclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.swiftsmp.swiftclient.config.SwiftConfig;

/** Small gameplay tweaks run every client tick: auto sprint, bright mode, zoom state. */
public final class Tweaks {
	private static boolean sprintHeldByUs;
	private static float zoom = 1f;

	private Tweaks() {}

	static void tick(Minecraft mc) {
		SwiftConfig c = SwiftConfig.get();
		Options o = mc.options;

		// Auto sprint: hold the sprint key for the player while they walk forward.
		boolean forward = mc.player != null && mc.gui.screen() == null && o.keyUp.isDown();
		if (c.autoSprint && forward) {
			o.keySprint.setDown(true);
			sprintHeldByUs = true;
		} else if (sprintHeldByUs) {
			o.keySprint.setDown(InputConstants.isKeyDown(KeyMappingHelper.getBoundKeyOf(o.keySprint).getValue()));
			sprintHeldByUs = false;
		}

		// Zoom eases in and out.
		boolean zooming = c.smoothZoom && mc.gui.screen() == null && SwiftClientMod.zoomKey().isDown();
		float target = zooming ? c.zoomFactor : 1f;
		zoom += (target - zoom) * 0.5f;
		if (Math.abs(zoom - target) < 0.01f) zoom = target;
	}

	/** Field of view divisor for the zoom (1 when not zooming). */
	public static float zoom() {
		return zoom;
	}

	/** Bright mode sets the vanilla brightness slider to max and restores it when turned off. */
	public static void applyBrightness() {
		SwiftConfig c = SwiftConfig.get();
		var gamma = Minecraft.getInstance().options.gamma();
		if (c.brightMode) {
			if (gamma.get() < 1.0) c.savedGamma = gamma.get();
			gamma.set(1.0);
		} else {
			gamma.set(c.savedGamma);
		}
		Minecraft.getInstance().options.save();
	}
}
