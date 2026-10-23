package net.swiftsmp.swiftclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.platform.Platform;

/** Small gameplay tweaks run every client tick: auto sprint, bright mode, zoom state. */
public final class Tweaks {
	private static boolean sprintHeldByUs;
	private static float zoom = 1f;

	private Tweaks() {}

	static void tick(Minecraft mc) {
		SwiftConfig c = SwiftConfig.get();
		Options o = mc.options;

		// Auto sprint: hold the sprint key for the player while they walk forward.
		boolean forward = mc.player != null && Platform.screen() == null && Platform.isKeyDown(Platform.boundKey(o.keyUp));
		if (c.autoSprint && forward) {
			o.keySprint.setDown(true);
			sprintHeldByUs = true;
		} else if (sprintHeldByUs) {
			o.keySprint.setDown(Platform.isKeyDown(Platform.boundKey(o.keySprint)));
			sprintHeldByUs = false;
		}

		// Zoom eases in and out.
		boolean zooming = c.smoothZoom && Platform.screen() == null && Platform.isKeyDown(SwiftClientMod.KEY_ZOOM);
		float target = zooming ? c.zoomFactor : 1f;
		zoom += (target - zoom) * 0.5f;
		if (Math.abs(zoom - target) < 0.01f) zoom = target;
		Platform.applyZoom(zoom);
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
			c.savedGamma = gamma.get();
			gamma.set(1.0);
		} else {
			gamma.set(c.savedGamma);
		}
	}
}
