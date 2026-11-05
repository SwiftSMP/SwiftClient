package net.swiftsmp.swiftclient.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The loading screen in the SwiftSMP website's design: navy with an orange top glow, a centred
 * bordered brand pill, the site's spinner and an orange→gold progress bar.
 *
 * Vanilla is only replaced while loading is actually in progress. As soon as the reload completes
 * (or if anything about the overlay cannot be read) this returns false, so vanilla draws its own
 * fade-out and dismisses the overlay normally — the loading screen cannot get stuck.
 */
public final class SwiftLoadingScreen {
	private static final int TRACK = 0xFF1E2546;
	private static long firstFrame;
	private static Object lastOverlay;
	private static Field reloadField;
	private static Field progressField;
	private static Method readyMethod;
	private static boolean lookedUp;

	private SwiftLoadingScreen() {}

	public static boolean render(GuiGraphicsExtractor g, Object overlay) {
		lookup(overlay);
		Object reload = read(reloadField, overlay);
		Float reloadProgress = reloadProgress(reload);
		Float current = currentProgress(overlay);
		Float progress = reloadProgress != null ? reloadProgress : current;
		if (progress == null) return false;

		// Vanilla owns completion and dismissal, always.
		if (complete(overlay, reload, reloadProgress, current)) return false;
		if (overlay != lastOverlay) {
			lastOverlay = overlay;
			firstFrame = 0;
		}
		long now = System.currentTimeMillis();
		if (firstFrame == 0) firstFrame = now;
		// Safety net: never hold the loading screen hostage if completion cannot be read.
		if (now - firstFrame > 90_000) return false;

		int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		background(g, w, h);
		brand(g, w, h, progress);
		float t = Math.min(1f, (now - firstFrame) / 300f);
		if (t < 1f) g.fill(0, 0, w, h, Theme.alpha(Theme.BG_DEEP, 1f - t));
		return true;
	}

	/** The site's body: navy with a soft orange radial glow at the top centre. */
	private static void background(GuiGraphicsExtractor g, int w, int h) {
		g.fill(0, 0, w, h, Theme.BG);
		g.fillGradient(0, 0, w, Math.min(h, 380), Theme.BG_DEEP, Theme.BG);
		int cx = w / 2;
		for (int i = 6; i >= 1; i--) {
			Theme.round(g, cx - 160 - i * 60, -80 - i * 10, 320 + i * 120, 220 + i * 20, 100, Theme.alpha(Theme.ACCENT, 0.025f));
		}
	}

	private static void brand(GuiGraphicsExtractor g, int w, int h, float progress) {
		Component word = Theme.wordmarkPixel();
		int textW = Theme.font().width(word);
		int pw = 10 + 34 + 8 + textW + 12;
		int ph = 54;
		int px = (w - pw) / 2;
		int py = h / 2 - 70;

		Theme.glow(g, px, py, pw, ph, 14, 1.2f);
		Theme.panel(g, px, py, pw, ph, 14, Theme.PANEL_2, Theme.BORDER);
		g.fillGradient(px + 2, py + 2, px + pw - 2, py + ph / 2, 0x4011162E, 0x0011162E);
		Theme.logo(g, px + 10, py + 10, 34);
		g.text(Theme.font(), word, px + 52, py + 15, Theme.TEXT, false);

		int cy = py + ph + 16;
		spinner(g, w / 2 - 46, cy + 4);
		g.text(Theme.font(), Theme.display("Loading…"), w / 2 - 32, cy, Theme.MUTED, false);

		int bw = Math.min(180, w - 80);
		int bx = (w - bw) / 2;
		int by = cy + 18;
		Theme.round(g, bx, by, bw, 6, 3, TRACK);
		int fill = Math.round(bw * Math.max(0f, Math.min(1f, progress)));
		if (fill > 0) {
			g.enableScissor(bx, by, bx + fill, by + 6);
			int steps = 32;
			for (int s = 0; s < steps; s++) {
				int x0 = bx + bw * s / steps;
				int x1 = bx + bw * (s + 1) / steps;
				if (x1 > x0) g.fill(x0, by, x1, by + 6, Theme.mix(Theme.ACCENT, Theme.GOLD, (s + 0.5f) / steps));
			}
			g.disableScissor();
		}
		Component pct = Theme.num(Math.round(progress * 100) + "%");
		g.text(Theme.font(), pct, bx + bw + 8, by - 1, Theme.GOLD, false);
	}

	/** The site's .loading spinner: an orange head on a hairline ring, rotating with time. */
	private static void spinner(GuiGraphicsExtractor g, int cx, int cy) {
		int head = (int) ((System.nanoTime() / 90_000_000L) % 18);
		for (int i = 0; i < 18; i++) {
			double a = i * Math.PI * 2 / 18 - Math.PI / 2;
			int x = cx + (int) Math.round(Math.cos(a) * 6);
			int y = cy + (int) Math.round(Math.sin(a) * 6);
			g.fill(x, y, x + 2, y + 2, i == head ? Theme.ACCENT : Theme.BORDER);
		}
	}

	// ---- overlay introspection (private fields/methods, all optional) --------------------

	private static void lookup(Object overlay) {
		if (lookedUp) return;
		lookedUp = true;
		for (Class<?> type = overlay.getClass(); type != null; type = type.getSuperclass()) {
			if (reloadField == null) {
				try {
					reloadField = type.getDeclaredField("reload");
					reloadField.setAccessible(true);
				} catch (NoSuchFieldException ignored) {}
			}
			if (progressField == null) {
				for (String name : new String[] {"currentProgress", "progress", "actualProgress"}) {
					try {
						progressField = type.getDeclaredField(name);
						progressField.setAccessible(true);
						break;
					} catch (NoSuchFieldException ignored) {}
				}
			}
			if (readyMethod == null) {
				try {
					readyMethod = type.getDeclaredMethod("isReadyToFadeOut");
					readyMethod.setAccessible(true);
				} catch (NoSuchMethodException ignored) {}
			}
		}
	}

	private static Object read(Field field, Object overlay) {
		if (field == null) return null;
		try {
			return field.get(overlay);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	private static Float currentProgress(Object overlay) {
		Object value = read(progressField, overlay);
		return value instanceof Float ? (Float) value : null;
	}

	private static Float reloadProgress(Object reload) {
		if (reload == null) return null;
		try {
			Method m = reload.getClass().getMethod("getProgress");
			return (Float) m.invoke(reload);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static Boolean reloadComplete(Object reload) {
		if (reload == null) return null;
		for (String name : new String[] {"isComplete", "isDone", "isFinished"}) {
			try {
				Method m = reload.getClass().getMethod(name);
				return (Boolean) m.invoke(reload);
			} catch (NoSuchMethodException ignored) {
			} catch (ReflectiveOperationException ignored) {}
		}
		return null;
	}

	private static boolean complete(Object overlay, Object reload, Float reloadProgress, Float current) {
		Boolean done = reloadComplete(reload);
		if (done != null) return done;
		// The reload's own progress reaches 1.0 whether or not vanilla's render runs.
		if (reloadProgress != null && reloadProgress >= 0.999f) return true;
		if (readyMethod != null) {
			try {
				return (Boolean) readyMethod.invoke(overlay);
			} catch (ReflectiveOperationException ignored) {}
		}
		if (current != null) return current >= 0.99f;
		return false;
	}
}
