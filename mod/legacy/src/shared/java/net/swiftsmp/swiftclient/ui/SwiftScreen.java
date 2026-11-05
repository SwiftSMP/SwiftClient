package net.swiftsmp.swiftclient.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.swiftsmp.swiftclient.platform.ScreenBase;

/**
 * Base for SwiftClient's own screens: immediate-mode drawing with click regions rebuilt
 * every frame, and eased hover states. No vanilla widgets, so everything matches the site.
 * The platform layer owns the Screen lifecycle and forwards input here.
 */
public abstract class SwiftScreen extends ScreenBase {
	protected record Hit(int x, int y, int w, int h, Runnable action) {}

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> hover = new HashMap<>();
	protected int mouseX;
	protected int mouseY;
	private int[] clip;

	protected SwiftScreen(String title) {
		super(title);
	}

	protected abstract void draw(Gfx g, float delta);

	/** Draws the frame and resets this frame's hit regions. Called by the platform layer. */
	@Override
	public final void renderFrame(Gfx g, int mx, int my, float delta) {
		hits.clear();
		mouseX = mx;
		mouseY = my;
		draw(g, delta);
	}

	/** Left click. Returns true when a SwiftClient element consumed it. */
	@Override
	public boolean onClick(double mx, double my, int button) {
		if (button != 0) return false;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (Theme.inside(mx, my, h.x, h.y, h.w, h.h)) {
				h.action.run();
				return true;
			}
		}
		return false;
	}

	protected void clickable(int x, int y, int w, int h, Runnable action) {
		if (clip != null) {
			int x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
			int x1 = Math.min(x + w, clip[2]), y1 = Math.min(y + h, clip[3]);
			if (x1 <= x0 || y1 <= y0) return;
			x = x0; y = y0; w = x1 - x0; h = y1 - y0;
		}
		hits.add(new Hit(x, y, w, h, action));
	}

	protected void clip(Gfx g, int x0, int y0, int x1, int y1) {
		clip = new int[] {x0, y0, x1, y1};
		g.enableScissor(x0, y0, x1, y1);
	}

	protected void unclip(Gfx g) {
		clip = null;
		g.disableScissor();
	}

	/** 0..1 eased hover amount for a keyed element. */
	protected float hover(String key, int x, int y, int w, int h) {
		boolean in = Theme.inside(mouseX, mouseY, x, y, w, h)
			&& (clip == null || Theme.inside(mouseX, mouseY, clip[0], clip[1], clip[2] - clip[0], clip[3] - clip[1]));
		float v = hover.getOrDefault(key, 0f);
		v += ((in ? 1f : 0f) - v) * 0.35f;
		hover.put(key, v);
		return v;
	}

	protected void icon(Gfx g, net.minecraft.world.item.Item item, int x, int y) {
		if (item == null) {
			Theme.logo(g, x, y, 16);
			return;
		}
		try {
			g.item(new net.minecraft.world.item.ItemStack(item), x, y);
		} catch (RuntimeException notBoundYet) {
			Theme.logo(g, x, y, 16);
		}
	}

	/** The site's .btn.primary: solid orange, #ff8d33 rim, dark ink label, orange glow. */
	protected void primaryButton(Gfx g, String key, int x, int y, int w, int h, String label, Runnable action) {
		float hv = hover(key, x, y, w, h);
		Theme.glow(g, x, y, w, h, h / 2, 0.6f + 0.6f * hv);
		Theme.round(g, x, y, w, h, h / 2, 0xFFFF8D33);
		Theme.round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, h / 2 - 1), Theme.mix(Theme.ACCENT, Theme.ACCENT_BRIGHT, hv));
		Component text = Theme.display(label);
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, Theme.INK, false);
		clickable(x, y, w, h, action);
	}

	/** The site's .btn: navy fill, hairline border that turns orange on hover. */
	protected void ghostButton(Gfx g, String key, int x, int y, int w, int h, String label, Runnable action) {
		float hv = hover(key, x, y, w, h);
		Theme.panel(g, x, y, w, h, Math.min(8, h / 2), Theme.PANEL_2, Theme.mix(Theme.BORDER, Theme.ACCENT, hv));
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, Theme.TEXT, false);
		clickable(x, y, w, h, action);
	}

	/** Backdrop used by our screens: the panorama (or the world) under a navy wash. */
	protected void backdrop(Gfx g) {
		if (net.minecraft.client.Minecraft.getInstance().level == null) Theme.titleBackground(g, width, height);
		// the launcher's dark glass wash, so panes read the same in game as in the launcher
		g.fillGradient(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 0.12f), Theme.alpha(Theme.BG_DEEP, 0.55f));
		g.fillGradient(0, 0, width, height / 2, Theme.alpha(Theme.ACCENT, 0.06f), 0x00000000);
	}
}
