package net.swiftsmp.swiftclient.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.lyrics.LyricsService;
import net.swiftsmp.swiftclient.lyrics.NowPlaying;
import net.swiftsmp.swiftclient.ui.Gfx;
import net.swiftsmp.swiftclient.ui.Theme;

/**
 * A Dynamic-Island-style now-playing pill at the top-centre: a dark navy capsule (the same surface
 * as the menu's ghost buttons) holding an animated equaliser, the track title and artist, and a
 * thin accent progress bar. Text is drawn from the high-resolution Instrument Sans face and scaled
 * down, so it stays crisp at the pill's small size.
 */
public final class DynamicIsland {
	private DynamicIsland() {}

	private static float appear;
	private static long lastNanos;
	private static final float TITLE_SCALE = 0.36f;
	private static final float ARTIST_SCALE = 0.27f;

	public static void render(Gfx g, Minecraft mc) {
		SwiftConfig config = SwiftConfig.get();
		LyricsService.State state = LyricsService.get().state();
		NowPlaying now = state.now;
		boolean show = config.nowPlayingIsland && now.hasTrack();
		long t0 = System.nanoTime();
		float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (t0 - lastNanos) / 1_000_000_000.0f);
		lastNanos = t0;
		// Frame-rate independent smooth approach toward the target (open ~0.35s, close a touch faster).
		float rate = show ? 7.0f : 9.0f;
		appear += ((show ? 1.0f : 0.0f) - appear) * (1.0f - (float) Math.exp(-rate * dt));
		if (appear < 0.02f) {
			appear = 0.0f;
			return;
		}

		Font font = mc.font;
		int accent = LyricsService.get().accent(Theme.ACCENT);
		int maxContent = Math.min((int) (g.guiWidth() * 0.34f), 260);
		String title = fit(font, now.title, maxContent, TITLE_SCALE);
		String artist = fit(font, now.artist, maxContent, ARTIST_SCALE);
		int titleW = textW(font, title, TITLE_SCALE);
		int artistW = artist.isEmpty() ? 0 : textW(font, artist, ARTIST_SCALE);
		int titleH = (int) (font.lineHeight * TITLE_SCALE);
		int artistH = (int) (font.lineHeight * ARTIST_SCALE);
		int contentW = Math.max(titleW, artistW);

		int eqW = 15;
		int gap = 9;
		int pad = 13;
		int pillW = pad + eqW + gap + contentW + pad;
		int h = 8 + titleH + 3 + artistH + 8;
		int y = 6;
		int x = (g.guiWidth() - pillW) / 2;

		float a = Math.min(1.0f, appear);
		int easedW = (int) (pillW * (0.32f + 0.68f * a));
		int easedX = (g.guiWidth() - easedW) / 2;
		int easedY = y + (int) ((1.0f - a) * -6.0f);

		Theme.panel(g, easedX, easedY, easedW, h, h / 2,
			Theme.alpha(Theme.PANEL_2, 0.95f * a), Theme.alpha(Theme.BORDER, 0.95f * a));
		// Content fades in smoothly as the capsule opens (no hard pop-in).
		float ca = smooth(clamp((a - 0.22f) / 0.6f));
		if (ca <= 0.01f) return;

		// Equaliser bars on the left.
		float t = (float) (System.nanoTime() / 1_000_000_000.0);
		int barW = 2;
		int barGap = 3;
		int baseY = easedY + h / 2;
		for (int i = 0; i < 3; i++) {
			float phase = now.playing ? (float) Math.sin(t * 5.2f + i * 1.15f) : 0.15f;
			int barH = (int) (3 + (phase * 0.5f + 0.5f) * (h * 0.42f));
			int bx = easedX + pad + i * (barW + barGap);
			Theme.round(g, bx, baseY - barH / 2, barW, barH, 1, Theme.alpha(accent, ca));
		}

		int textX = easedX + pad + eqW + gap;
		drawScaled(g, font, title, textX, easedY + 8, TITLE_SCALE, Theme.alpha(0xFFFFFF, ca));
		if (!artist.isEmpty()) {
			drawScaled(g, font, artist, textX, easedY + 8 + titleH + 3, ARTIST_SCALE, Theme.alpha(Theme.MUTED, ca));
		}

		if (now.durationMs > 0) {
			int barX = textX;
			int barLen = contentW;
			int py = easedY + h - 6;
			g.fill(barX, py, barX + barLen, py + 1, Theme.alpha(0xFFFFFF, 0.16f * ca));
			int filled = (int) Math.max(0, Math.min(barLen, barLen * state.positionMs() / (float) now.durationMs));
			g.fill(barX, py, barX + filled, py + 1, Theme.alpha(accent, ca));
		}
	}

	private static int textW(Font font, String s, float scale) {
		return (int) (font.width(Theme.displayLarge(s).getVisualOrderText()) * scale);
	}

	private static float clamp(float v) {
		return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
	}

	/** Smoothstep easing for the content fade. */
	private static float smooth(float t) {
		return t * t * (3.0f - 2.0f * t);
	}

	private static void drawScaled(Gfx g, Font font, String s, int x, int y, float scale, int color) {
		g.pushMatrix();
		g.translate(x, y);
		g.scale(scale);
		g.text(font, Theme.displayLarge(s), 0, 0, color, false);
		g.popMatrix();
	}

	/** Trim a string so its scaled display-large width fits maxW. */
	private static String fit(Font font, String s, int maxW, float scale) {
		if (s == null || s.isEmpty() || maxW <= 0) return "";
		if (textW(font, s, scale) <= maxW) return s;
		int lo = 0;
		int hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (textW(font, s.substring(0, mid), scale) <= maxW) lo = mid;
			else hi = mid - 1;
		}
		return s.substring(0, Math.max(1, lo));
	}
}
