package net.swiftsmp.swiftclient.hud;

import net.minecraft.client.Minecraft;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.platform.Platform;
import net.swiftsmp.swiftclient.ui.Gfx;
import net.swiftsmp.swiftclient.ui.SwiftScreen;

/** Draws every enabled HUD module at its saved position and scale. */
public final class HudRenderer {
	private HudRenderer() {}

	public static void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !SwiftConfig.get().hudVisible) return;
		if (Platform.screen() instanceof SwiftScreen) return;
		for (HudModule m : Modules.ALL) {
			if (m.enabled() && m.visible(mc)) draw(g, mc, m, false);
		}
	}

	static void draw(Gfx g, Minecraft mc, HudModule m, boolean preview) {
		int[] b = m.bounds(mc, g.guiWidth(), g.guiHeight());
		float scale = m.settings().scale;
		g.pushMatrix();
		g.translate(b[0], b[1]);
		g.scale(scale);
		m.render(g, mc, preview);
		g.popMatrix();
	}

	public static void drawPreview(Gfx g, Minecraft mc, HudModule m) {
		draw(g, mc, m, true);
	}
}
