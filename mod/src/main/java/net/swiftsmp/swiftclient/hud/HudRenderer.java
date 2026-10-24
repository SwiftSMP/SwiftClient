package net.swiftsmp.swiftclient.hud;

import net.swiftsmp.swiftclient.Compat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.swiftsmp.swiftclient.config.SwiftConfig;

/** Draws every enabled HUD module at its saved position and scale. */
public final class HudRenderer {
	private HudRenderer() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !SwiftConfig.get().hudVisible) return;
		// The HUD editor draws its own copies.
		if (Compat.screen() instanceof net.swiftsmp.swiftclient.ui.SwiftScreen) return;
		for (HudModule m : Modules.ALL) {
			if (m.enabled() && m.visible(mc)) draw(g, mc, m, false);
		}
	}

	static void draw(GuiGraphicsExtractor g, Minecraft mc, HudModule m, boolean preview) {
		int[] b = m.bounds(mc, g.guiWidth(), g.guiHeight());
		float scale = m.settings().scale;
		g.pose().pushMatrix();
		g.pose().translate(b[0], b[1]);
		g.pose().scale(scale, scale);
		m.render(g, mc, preview);
		g.pose().popMatrix();
	}

	public static void drawPreview(GuiGraphicsExtractor g, Minecraft mc, HudModule m) {
		draw(g, mc, m, true);
	}
}
