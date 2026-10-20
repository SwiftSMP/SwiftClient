package net.swiftsmp.swiftclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base for SwiftClient's own screens: immediate-mode drawing with click regions rebuilt
 * every frame, and eased hover states. No vanilla widgets, so everything matches the site.
 */
public abstract class SwiftScreen extends Screen {
	private record Hit(int x, int y, int w, int h, int button, Runnable action) {}

	private final List<Hit> hits = new ArrayList<>();
	private final Map<String, Float> hover = new HashMap<>();
	protected int mouseX;
	protected int mouseY;
	/** Current clip rectangle; clicks outside it are ignored. Null when not clipping. */
	private int[] clip;

	protected SwiftScreen(String title) {
		super(Component.literal(title));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		// Our own dim instead of the vanilla blur: the world stays visible behind the panel.
		// Without a world (opened from the title screen) show the menu panorama instead of black.
		if (net.minecraft.client.Minecraft.getInstance().level == null) extractPanorama(g, delta);
		g.fillGradient(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 0.35f), Theme.alpha(Theme.BG_DEEP, 0.7f));
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		hits.clear();
		mouseX = mx;
		mouseY = my;
		draw(g, delta);
	}

	protected abstract void draw(GuiGraphicsExtractor g, float delta);

	/** Registers a left-click area for this frame. */
	protected void clickable(int x, int y, int w, int h, Runnable action) {
		clickable(x, y, w, h, InputConstants.MOUSE_BUTTON_LEFT, action);
	}

	protected void clip(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		clip = new int[] {x0, y0, x1, y1};
		g.enableScissor(x0, y0, x1, y1);
	}

	protected void unclip(GuiGraphicsExtractor g) {
		clip = null;
		g.disableScissor();
	}

	/** Registers a clickable area; button is an InputConstants.MOUSE_BUTTON_* value (SDL numbering) or -1 for any. */
	protected void clickable(int x, int y, int w, int h, int button, Runnable action) {
		if (clip != null) {
			int x0 = Math.max(x, clip[0]), y0 = Math.max(y, clip[1]);
			int x1 = Math.min(x + w, clip[2]), y1 = Math.min(y + h, clip[3]);
			if (x1 <= x0 || y1 <= y0) return;
			x = x0; y = y0; w = x1 - x0; h = y1 - y0;
		}
		hits.add(new Hit(x, y, w, h, button, action));
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

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		// Last registered wins: things drawn on top get the click.
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if ((h.button == -1 || h.button == e.button()) && Theme.inside(e.x(), e.y(), h.x, h.y, h.w, h.h)) {
				h.action.run();
				return true;
			}
		}
		return super.mouseClicked(e, doubleClick);
	}

	// ---- shared widgets ---------------------------------------------------------------

	/**
	 * Draws an item icon. The title screen is first drawn while the game is still loading,
	 * before item components are bound; icons simply appear once they are.
	 */
	protected void icon(GuiGraphicsExtractor g, net.minecraft.world.item.Item item, int x, int y) {
		net.minecraft.world.item.ItemStack stack;
		try {
			stack = new net.minecraft.world.item.ItemStack(item);
		} catch (NullPointerException notBoundYet) {
			return;
		}
		g.item(stack, x, y);
	}

	protected void primaryButton(GuiGraphicsExtractor g, String key, int x, int y, int w, int h, String label, Runnable action) {
		float hv = hover(key, x, y, w, h);
		Theme.glow(g, x, y, w, h, h / 2, 0.6f + 0.6f * hv);
		Theme.round(g, x, y, w, h, h / 2, Theme.mix(Theme.ACCENT, Theme.ACCENT_BRIGHT, hv));
		g.fill(x + h / 2, y + 1, x + w - h / 2, y + 2, 0x30FFFFFF);
		Component text = Theme.display(label);
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
		clickable(x, y, w, h, action);
	}

	protected void ghostButton(GuiGraphicsExtractor g, String key, int x, int y, int w, int h, String label, Runnable action) {
		float hv = hover(key, x, y, w, h);
		Theme.glass(g, x, y, w, h, Math.min(8, h / 2), Theme.mix(Theme.GLASS_LIGHT, Theme.alpha(Theme.ACCENT, 0.35f), hv));
		g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, Theme.TEXT, false);
		clickable(x, y, w, h, action);
	}
}
