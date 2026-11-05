package net.swiftsmp.swiftclient.hud;

import net.minecraft.client.Minecraft;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.lyrics.BassPulse;
import net.swiftsmp.swiftclient.lyrics.SkyLyricsDisplay;
import net.swiftsmp.swiftclient.platform.Platform;
import net.swiftsmp.swiftclient.ui.Gfx;
import net.swiftsmp.swiftclient.ui.SwiftScreen;
import net.swiftsmp.swiftclient.ui.Theme;

/** Draws every enabled HUD module at its saved position and scale. */
public final class HudRenderer {
	private HudRenderer() {}

	public static void render(Gfx g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (Platform.screen() instanceof SwiftScreen) return;
		SwiftConfig config = SwiftConfig.get();
		// The sky line, bass vignette and now-playing island sit behind the readouts and only show in game.
		SkyLyricsDisplay.INSTANCE.render(g, mc);
		drawBassVignette(g, config);
		DynamicIsland.render(g, mc);
		if (!config.hudVisible) return;
		for (HudModule m : Modules.ALL) {
			if (m.enabled() && m.visible(mc)) draw(g, mc, m, false);
		}
	}

	/** A SwiftClient-orange edge glow that pumps inward on every bass hit. */
	private static void drawBassVignette(Gfx g, SwiftConfig config) {
		if (!config.bassMode) return;
		float p = (float) BassPulse.INSTANCE.level();
		if (p <= 0.01f) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int band = Math.max(48, (int) (h * 0.22f));
		g.fillGradient(0, 0, w, band, Theme.alpha(Theme.ACCENT, 0.40f * p), Theme.alpha(Theme.ACCENT, 0f));
		g.fillGradient(0, h - band, w, h, Theme.alpha(Theme.ACCENT, 0f), Theme.alpha(Theme.ACCENT, 0.40f * p));
		int lband = Math.max(48, (int) (w * 0.16f));
		int steps = 8;
		for (int i = 0; i < steps; i++) {
			float a = 0.30f * p * (1f - (float) i / steps);
			int x0 = i * lband / steps;
			int x1 = (i + 1) * lband / steps;
			g.fill(x0, 0, x1, h, Theme.alpha(Theme.ACCENT, a));
			g.fill(w - x1, 0, w - x0, h, Theme.alpha(Theme.ACCENT, a));
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
