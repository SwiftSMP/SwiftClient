package net.swiftsmp.swiftclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.swiftsmp.swiftclient.SwiftClientMod;

/** SwiftSMP design tokens (same as the website and launcher) and drawing helpers. */
public final class Theme {
	public static final int BG = 0xFF0D1020;
	public static final int BG_DEEP = 0xFF080A16;
	public static final int SURFACE = 0xFF131830;
	public static final int SURFACE_2 = 0xFF182042;
	public static final int SURFACE_3 = 0xFF1E2750;
	public static final int BORDER = 0xFF222A48;
	public static final int TEXT = 0xFFECEEF6;
	public static final int MUTED = 0xFF8E94B0;
	public static final int FAINT = 0xFF5C6384;
	public static final int ACCENT = 0xFFFC6D00;
	public static final int ACCENT_BRIGHT = 0xFFFF9A3D;
	public static final int GOLD = 0xFFFFC83D;
	public static final int OK = 0xFF4ADE80;
	public static final int DANGER = 0xFFF25555;

	public static final Identifier LOGO = Identifier.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "icon.png");
	private static final FontDescription PIXEL = new FontDescription.Resource(Identifier.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "pixel"));

	private Theme() {}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	/** Text in the website's display face (Pixelify Sans). */
	public static MutableComponent display(String s) {
		return Component.literal(s).withStyle(Style.EMPTY.withFont(PIXEL));
	}

	/** "Swift" in white + "Client" in orange, like the site's wordmark. */
	public static MutableComponent wordmark() {
		return display("Swift").withColor(TEXT).append(display("Client").withColor(ACCENT));
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

	// Lunar-style glass: translucent navy with a faint white stroke and a top highlight.
	public static final int GLASS = 0xC80D1020;
	public static final int GLASS_LIGHT = 0xB0182042;
	public static final int STROKE = 0x16FFFFFF;
	public static final int HIGHLIGHT = 0x0DFFFFFF;

	/** Filled rectangle with real rounded corners of radius r. */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
		}
	}

	/** Frosted glass panel: stroke, translucent fill, 1px highlight along the top. */
	public static void glass(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill) {
		round(g, x, y, w, h, r, STROKE);
		round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
		g.fill(x + r, y + 1, x + w - r, y + 2, HIGHLIGHT);
	}

	/** SwiftSMP look for vanilla buttons: glass, orange when hovered or focused, dim when disabled. */
	public static void vanillaButton(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hot) {
		int r = Math.min(5, h / 2);
		if (!active) {
			round(g, x, y, w, h, r, 0x10FFFFFF);
			round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), 0x900A0D1C);
			return;
		}
		if (hot) {
			glow(g, x, y, w, h, r, 0.7f);
			round(g, x, y, w, h, r, ACCENT_BRIGHT);
			round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), mix(0xE0182042, ACCENT, 0.55f));
			g.fill(x + r, y + h - 2, x + w - r, y + h - 1, ACCENT_BRIGHT);
		} else {
			glass(g, x, y, w, h, r, 0xD0131830);
		}
	}

	/** Soft orange glow behind an element. */
	public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, float strength) {
		for (int i = 3; i >= 1; i--) round(g, x - i, y - i, w + i * 2, h + i * 2, r + i, alpha(ACCENT, 0.07f * strength));
	}

	/** Filled rectangle with 2px rounded corners. */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
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
	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
		round(g, x, y, w, h, border);
		round(g, x + 1, y + 1, w - 2, h - 2, fill);
	}

	/** The site's orange→gold hairline along the top of a card. */
	public static void brandEdge(GuiGraphicsExtractor g, int x, int y, int w) {
		for (int i = x + 2; i < x + w - 2; i++) g.fill(i, y, i + 1, y + 1, mix(ACCENT, GOLD, (i - x) / (float) w));
	}

	/** Pill toggle switch, 20x10. */
	public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, float hover) {
		int track = on ? mix(ACCENT, ACCENT_BRIGHT, hover) : mix(0x40FFFFFF, 0x60FFFFFF, hover);
		round(g, x, y, 20, 10, 5, track);
		round(g, on ? x + 11 : x + 1, y + 1, 8, 8, 4, on ? 0xFFFFFFFF : 0xFFC9CDE0);
	}

	public static void logo(GuiGraphicsExtractor g, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0, 0, size, size, size, size);
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
