package net.swiftsmp.swiftclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.swiftsmp.swiftclient.platform.Platform;

/** SwiftSMP design tokens (same as the website and launcher) and drawing helpers. */
public final class Theme {
	public static final int BG = 0xFF0D1020;
	public static final int BG_DEEP = 0xFF080A16;
	public static final int SURFACE = 0xFF131830;
	public static final int PANEL_2 = 0xFF0B0E1E;
	public static final int SURFACE_2 = 0xFF182042;
	public static final int SURFACE_3 = 0xFF1E2750;
	public static final int BORDER = 0xFF222A48;
	public static final int LINE_SOFT = 0xFF1A2140;
	public static final int TEXT = 0xFFECEEF6;
	public static final int MUTED = 0xFF8E94B0;
	public static final int FAINT = 0xFF5C6384;
	public static final int ACCENT = 0xFFFC6D00;
	public static final int ACCENT_BRIGHT = 0xFFFF9A3D;
	public static final int GOLD = 0xFFFFC83D;
	public static final int OK = 0xFF4ADE80;
	public static final int DANGER = 0xFFF25555;
	public static final int INK = 0xFF1A0D00;
	public static final int SCROLL = 0xFF2A3358;

	// The site's two surfaces: --panel for the big panes, --panel-2 for rows and cards.
	public static final int GLASS = SURFACE;
	public static final int GLASS_LIGHT = PANEL_2;

	private Theme() {}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Text in the clean display face (Instrument Sans). */
	public static MutableComponent display(String s) {
		return Component.literal(s).withStyle(Platform.sansStyle());
	}

	/** Brand text in Pixelify Sans, for the loading and title screens only. */
	public static MutableComponent displayPixel(String s) {
		return Component.literal(s).withStyle(Platform.pixelStyle());
	}

	/** Large, high-resolution Instrument Sans for the title wordmark (crisp when big). */
	public static MutableComponent displayLarge(String s) {
		return Component.literal(s).withStyle(Platform.sansLargeStyle());
	}

	/** Large Pixelify Sans for the pixel-brand title wordmark. */
	public static MutableComponent displayPixelLarge(String s) {
		return Component.literal(s).withStyle(Platform.pixelLargeStyle());
	}

	/** Numbers, codes and state chips in Silkscreen, the site's --num face. */
	public static MutableComponent num(String s) {
		return Component.literal(s).withStyle(Platform.numStyle());
	}

	/** "Swift" in white + "Client" in orange, like the site's wordmark. */
	public static MutableComponent wordmark() {
		return display("Swift").withColor(TEXT).append(display("Client").withColor(ACCENT));
	}

	/** The title screen's bigger wordmark, rasterized at 24px instead of scaling a 10px face. */
	public static MutableComponent wordmarkLarge() {
		return displayLarge("Swift").withColor(TEXT).append(displayLarge("Client").withColor(ACCENT));
	}

	/** Pixel-brand wordmark for the title screen, matching the site and splash. */
	public static MutableComponent wordmarkPixel() {
		return displayPixelLarge("Swift").withColor(TEXT).append(displayPixelLarge("Client").withColor(ACCENT));
	}

	/** Small "Swift" + "Client" wordmark in Pixelify Sans, for the menu sidebar. */
	public static MutableComponent wordmarkSmall() {
		return displayPixel("Swift").withColor(TEXT).append(displayPixel("Client").withColor(ACCENT));
	}

	public static int alpha(int argb, float a) {
		return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (argb & 0xFFFFFF);
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255, aa = a >>> 24;
		int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255, ba = b >>> 24;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	/** Filled rectangle with real rounded corners, anti-aliased on the curved edges. */
	public static void round(Gfx g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			double dx = Math.sqrt(Math.max(0, r * r - dy * dy));
			double left = r - dx, right = w - r + dx;
			rowSpan(g, x, y + i, left, right, color);
			rowSpan(g, x, y + h - i - 1, left, right, color);
		}
	}

	/** One pixel row of a rounded shape, with fractional coverage at both curved ends. */
	private static void rowSpan(Gfx g, int x, int y, double left, double right, int color) {
		int li = (int) Math.floor(left), ri = (int) Math.ceil(right) - 1;
		if (ri < li) return;
		float lc = (float) (1 - (left - li));
		float rc = (float) (right - ri);
		if (ri == li) {
			float c = Math.min(lc, rc);
			if (c > 0.02f) g.fill(x + li, y, x + li + 1, y + 1, cover(color, c));
			return;
		}
		if (lc > 0.02f) g.fill(x + li, y, x + li + 1, y + 1, cover(color, lc));
		if (ri - li > 1) g.fill(x + li + 1, y, x + ri, y + 1, color);
		if (rc > 0.02f) g.fill(x + ri, y, x + ri + 1, y + 1, cover(color, rc));
	}

	/** Scales a colour's alpha by pixel coverage, for smooth curved edges. */
	private static int cover(int color, float coverage) {
		coverage = Math.max(0, Math.min(1, coverage));
		int a = Math.round((color >>> 24) * coverage);
		return (a << 24) | (color & 0xFFFFFF);
	}

	/**
	 * The site's .panel: an opaque navy fill inside a 1px #222A48 hairline, with the soft
	 * drop shadow the nav and dropdowns use. No sheen, smoke fade or white halo.
	 */
	public static void glass(Gfx g, int x, int y, int w, int h, int r, int fill) {
		int rr = Math.min(r, Math.min(w, h) / 2);
		if (w > 90 && h > 40) {
			round(g, x + 1, y + 3, w - 2, h - 2, rr, 0x0C000000);
			round(g, x + 2, y + 5, w - 4, h - 4, Math.max(0, rr - 1), 0x08000000);
			round(g, x + 3, y + 7, w - 6, h - 6, Math.max(0, rr - 2), 0x06000000);
		}
		panel(g, x, y, w, h, r, fill, BORDER);
	}

	/** In-game HUD backdrop: a light translucent pane with no border, so the world shows through. */
	public static void hudGlass(Gfx g, int x, int y, int w, int h, int r, int fill) {
		round(g, x, y, w, h, Math.min(r, Math.min(w, h) / 2), fill);
	}

	/** A rounded pane with a 1px border in any colour. */
	public static void panel(Gfx g, int x, int y, int w, int h, int r, int fill, int border) {
		if (w <= 2 || h <= 2) return;
		int rr = Math.min(r, Math.min(w, h) / 2);
		round(g, x, y, w, h, rr, border);
		round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, rr - 1), fill);
	}

	/** The site's .btn: navy fill, hairline border that turns orange on hover, dim when disabled. */
	public static void vanillaButton(Gfx g, int x, int y, int w, int h, boolean active, boolean hot) {
		int r = Math.min(5, h / 2);
		if (!active) {
			panel(g, x, y, w, h, r, BG_DEEP, LINE_SOFT);
			return;
		}
		panel(g, x, y, w, h, r, hot ? SURFACE_2 : PANEL_2, hot ? ACCENT : BORDER);
	}

	/** Soft orange glow behind an element. */
	public static void glow(Gfx g, int x, int y, int w, int h, int r, float strength) {
		for (int i = 3; i >= 1; i--) round(g, x - i, y - i, w + i * 2, h + i * 2, r + i, alpha(ACCENT, 0.07f * strength));
	}

	/** Filled rectangle with 2px rounded corners. */
	public static void round(Gfx g, int x, int y, int w, int h, int color) {
		if (w < 4 || h < 4) { g.fill(x, y, x + w, y + h, color); return; }
		g.fill(x + 2, y, x + w - 2, y + h, color);
		g.fill(x, y + 2, x + 2, y + h - 2, color);
		g.fill(x + w - 2, y + 2, x + w, y + h - 2, color);
		g.fill(x + 1, y + 1, x + 2, y + 2, color);
		g.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
		g.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
		g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
	}

	/** A card: 1px border colour around a rounded surface. */
	public static void card(Gfx g, int x, int y, int w, int h, int fill, int border) {
		round(g, x, y, w, h, border);
		round(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** "cyanrealms", "valenmc", or empty for the default SwiftClient mascot. */
	public static String serverIconKey(String address) {
		String a = address == null ? "" : address.toLowerCase(java.util.Locale.ROOT);
		if (a.contains("cyanrealms")) return "cyanrealms";
		if (a.contains("valenmc")) return "valenmc";
		return "";
	}

	public static boolean hasServerIcon(String address) {
		return !serverIconKey(address).isEmpty();
	}

	/** The site's orange→gold gradient, used for progress bars. */
	public static void barGradient(Gfx g, int x, int y, int w) {
		barGradient(g, x, y, w, 6);
	}

	/** Orange→gold fill inset by r so it follows the rounded corner. */
	public static void barGradient(Gfx g, int x, int y, int w, int r) {
		int start = x + Math.max(4, r), end = x + w - Math.max(4, r);
		int span = end - start;
		if (span < 8) return;
		int steps = 32;
		for (int s = 0; s < steps; s++) {
			int x0 = start + span * s / steps;
			int x1 = start + span * (s + 1) / steps;
			if (x1 <= x0) continue;
			int color = mix(ACCENT, GOLD, (s + 0.5f) / steps);
			float edge = Math.min(s, steps - 1 - s) / (steps / 2f);
			float a = 0.92f * Math.min(1f, edge * 3f);
			g.fill(x0, y, x1, y + 1, alpha(color, a));
			g.fill(x0, y + 1, x1, y + 2, alpha(color, a * 0.3f));
		}
	}

	/** Pill toggle switch, 20x10: orange when on, the site's hairline navy when off. */
	public static void toggle(Gfx g, int x, int y, boolean on, float hover) {
		if (on) {
			round(g, x, y, 20, 10, 5, mix(ACCENT, ACCENT_BRIGHT, hover));
			round(g, x + 11, y + 1, 8, 8, 4, 0xFFFFFFFF);
		} else {
			panel(g, x, y, 20, 10, 5, mix(PANEL_2, SURFACE_2, hover), mix(BORDER, 0xFF303A62, hover));
			round(g, x + 2, y + 2, 6, 6, 3, 0xFFC9CDE0);
		}
	}

	/** The SwiftClient mascot, the same texture the 26.x build blits. */
	public static void logo(Gfx g, int x, int y, int size) {
		g.logo(x, y, size);
	}

	/** Smooth mascot for menus; the title screen keeps the pixel-art logo. */
	public static void menuLogo(Gfx g, int x, int y, int size) {
		g.menuLogo(x, y, size);
	}

	/** Cover-crops the 1920x1080 title art with a slow drift, so every aspect ratio stays filled. */
	public static void titleBackground(Gfx g, int width, int height) {
		float t = System.nanoTime() / 1_000_000_000f;
		float cover = Math.max(width / 1920f, height / 1080f) * 1.08f;
		float zoom = 1f + (float) Math.sin(t * 0.05f) * 0.012f;
		float scale = cover * zoom;
		float dx = (width - 1920f * scale) / 2f + (float) Math.sin(t * 0.14f) * 12f;
		float dy = (height - 1080f * scale) / 2f + (float) Math.cos(t * 0.09f) * 8f;
		g.pushMatrix();
		g.translate(dx, dy);
		g.scale(scale);
		g.background(0, 0, 1920, 1080);
		g.popMatrix();
	}

	/**
	 * Flat 16x16 glyph for a HUD module card. Drawn instead of an ItemStack icon so the same
	 * icon works on the title screen, before item components are bound.
	 */
	public static void moduleIcon(Gfx g, String id, int x, int y) {
		if (g.uiIcon(id, x, y, 16)) return;
		switch (id) {
			case "fps" -> {
				g.fill(x + 8, y + 1, x + 13, y + 4, ACCENT);
				g.fill(x + 6, y + 4, x + 11, y + 7, ACCENT);
				g.fill(x + 3, y + 7, x + 11, y + 9, ACCENT);
				g.fill(x + 5, y + 9, x + 10, y + 12, ACCENT);
				g.fill(x + 3, y + 12, x + 8, y + 15, ACCENT);
			}
			case "cps" -> {
				g.fill(x + 6, y + 2, x + 10, y + 3, TEXT);
				g.fill(x + 4, y + 3, x + 6, y + 6, TEXT);
				g.fill(x + 10, y + 3, x + 12, y + 6, TEXT);
				g.fill(x + 4, y + 6, x + 5, y + 12, TEXT);
				g.fill(x + 11, y + 6, x + 12, y + 12, TEXT);
				g.fill(x + 6, y + 13, x + 10, y + 14, TEXT);
				g.fill(x + 8, y + 3, x + 9, y + 8, TEXT);
				g.fill(x + 4, y + 8, x + 12, y + 9, TEXT);
				g.fill(x + 7, y + 4, x + 9, y + 6, ACCENT);
			}
			case "ping" -> {
				g.fill(x + 1, y + 11, x + 4, y + 15, ACCENT);
				g.fill(x + 5, y + 8, x + 8, y + 15, ACCENT);
				g.fill(x + 9, y + 5, x + 12, y + 15, ACCENT);
				g.fill(x + 13, y + 1, x + 16, y + 15, ACCENT);
			}
			case "coords" -> {
				g.fill(x + 7, y + 1, x + 9, y + 6, TEXT);
				g.fill(x + 7, y + 11, x + 9, y + 15, TEXT);
				g.fill(x + 1, y + 7, x + 6, y + 9, TEXT);
				g.fill(x + 11, y + 7, x + 15, y + 9, TEXT);
				g.fill(x + 6, y + 6, x + 10, y + 10, ACCENT);
			}
			case "clock" -> {
				round(g, x + 2, y + 2, 12, 12, 6, TEXT);
				g.fill(x + 7, y + 5, x + 9, y + 10, ACCENT);
				g.fill(x + 7, y + 8, x + 11, y + 10, ACCENT);
			}
			case "keystrokes" -> {
				g.fill(x + 1, y + 4, x + 15, y + 5, TEXT);
				g.fill(x + 1, y + 11, x + 15, y + 12, TEXT);
				g.fill(x + 1, y + 5, x + 2, y + 11, TEXT);
				g.fill(x + 14, y + 5, x + 15, y + 11, TEXT);
				g.fill(x + 3, y + 6, x + 5, y + 8, ACCENT);
				g.fill(x + 7, y + 6, x + 9, y + 8, ACCENT);
				g.fill(x + 11, y + 6, x + 13, y + 8, ACCENT);
				g.fill(x + 4, y + 9, x + 12, y + 11, ACCENT);
			}
			case "armor" -> {
				g.fill(x + 4, y + 1, x + 12, y + 3, TEXT);
				g.fill(x + 3, y + 3, x + 13, y + 9, TEXT);
				g.fill(x + 4, y + 9, x + 12, y + 12, TEXT);
				g.fill(x + 6, y + 12, x + 10, y + 14, TEXT);
				g.fill(x + 7, y + 14, x + 9, y + 15, TEXT);
				g.fill(x + 7, y + 3, x + 9, y + 12, ACCENT);
			}
			default -> logo(g, x, y, 16);
		}
	}

	/** Flat 16x16 glyph for the menu's sidebar tabs. */
	/** Smooth sidebar glyph, dimmed until the tab is active or hovered. */
	public static void tabIcon(Gfx g, String tab, int x, int y, boolean active, float hover) {
		String id = tab.toLowerCase(java.util.Locale.ROOT);
		if (g.uiIcon(active || hover > 0.4f ? id : id + "_dim", x, y, 16)) return;
		tabIcon(g, tab, x, y, active ? TEXT : MUTED);
	}

	public static void tabIcon(Gfx g, String tab, int x, int y, int color) {
		switch (tab) {
			case "HOME" -> {
				g.fill(x + 4, y + 5, x + 6, y + 7, color);
				g.fill(x + 3, y + 6, x + 7, y + 8, color);
				g.fill(x + 2, y + 7, x + 8, y + 9, color);
				g.fill(x + 8, y + 5, x + 12, y + 7, color);
				g.fill(x + 9, y + 6, x + 13, y + 8, color);
				g.fill(x + 8, y + 7, x + 14, y + 9, color);
				g.fill(x + 4, y + 9, x + 12, y + 14, color);
				g.fill(x + 7, y + 10, x + 9, y + 14, ACCENT);
			}
			case "MODS" -> {
				round(g, x + 2, y + 2, 5, 5, 1, ACCENT);
				round(g, x + 9, y + 2, 5, 5, 1, color);
				round(g, x + 2, y + 9, 5, 5, 1, color);
				round(g, x + 9, y + 9, 5, 5, 1, color);
			}
			case "VISIBILITY" -> {
				g.fill(x + 5, y + 4, x + 11, y + 5, color);
				g.fill(x + 3, y + 5, x + 13, y + 6, color);
				g.fill(x + 2, y + 6, x + 14, y + 10, color);
				g.fill(x + 3, y + 10, x + 13, y + 11, color);
				g.fill(x + 5, y + 11, x + 11, y + 12, color);
				g.fill(x + 6, y + 6, x + 10, y + 10, ACCENT);
			}
			case "TWEAKS" -> {
				g.fill(x + 2, y + 4, x + 14, y + 5, color);
				g.fill(x + 2, y + 8, x + 14, y + 9, color);
				g.fill(x + 2, y + 12, x + 14, y + 13, color);
				g.fill(x + 5, y + 3, x + 8, y + 6, ACCENT);
				g.fill(x + 9, y + 7, x + 12, y + 10, ACCENT);
				g.fill(x + 6, y + 11, x + 9, y + 14, ACCENT);
			}
			case "SERVER", "SERVERS" -> {
				round(g, x + 2, y + 2, 12, 5, 2, color);
				round(g, x + 2, y + 9, 12, 5, 2, color);
				g.fill(x + 11, y + 4, x + 13, y + 5, ACCENT);
				g.fill(x + 11, y + 11, x + 13, y + 12, ACCENT);
			}
			case "BOOST" -> {
				g.fill(x + 8, y + 1, x + 13, y + 5, color);
				g.fill(x + 5, y + 5, x + 11, y + 9, color);
				g.fill(x + 8, y + 9, x + 12, y + 13, color);
				g.fill(x + 7, y + 13, x + 10, y + 15, ACCENT);
				g.fill(x + 1, y + 4, x + 5, y + 5, ACCENT);
				g.fill(x + 2, y + 10, x + 6, y + 11, ACCENT);
			}
			default -> logo(g, x, y, 16);
		}
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
