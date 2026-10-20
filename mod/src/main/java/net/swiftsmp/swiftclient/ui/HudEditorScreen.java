package net.swiftsmp.swiftclient.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.hud.HudModule;
import net.swiftsmp.swiftclient.hud.HudRenderer;
import net.swiftsmp.swiftclient.hud.Modules;

/** Drag HUD modules around, scroll to resize, right-click to hide. Snaps to edges and centre lines. */
public final class HudEditorScreen extends SwiftScreen {
	private static final int SNAP = 4;
	private final Screen parent;
	private HudModule dragging;
	private double grabX;
	private double grabY;
	private boolean guideX;
	private boolean guideY;

	public HudEditorScreen(Screen parent) {
		super("Edit HUD");
		this.parent = parent;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fill(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 0.35f));
	}

	@Override
	public void onClose() {
		SwiftConfig.save();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float delta) {
		Minecraft mc = Minecraft.getInstance();
		// centre guides while dragging
		if (dragging != null && guideX) g.fill(width / 2, 0, width / 2 + 1, height, Theme.alpha(Theme.ACCENT, 0.6f));
		if (dragging != null && guideY) g.fill(0, height / 2, width, height / 2 + 1, Theme.alpha(Theme.ACCENT, 0.6f));

		for (HudModule m : Modules.ALL) {
			if (!m.enabled()) continue;
			int[] b = m.bounds(mc, width, height);
			boolean hot = m == dragging || Theme.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]);
			Theme.round(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 5, Theme.alpha(hot ? Theme.ACCENT : 0xFFFFFFFF, hot ? 0.18f : 0.06f));
			HudRenderer.drawPreview(g, mc, m);
			outline(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, hot ? Theme.ACCENT : Theme.alpha(Theme.MUTED, 0.6f));
			if (hot) {
				String label = m.name + "  " + Math.round(m.settings().scale * 100) + "%";
				int ly = b[1] - 14 < 0 ? b[1] + b[3] + 4 : b[1] - 14;
				Theme.round(g, b[0] - 2, ly, font.width(label) + 8, 11, 5, Theme.ACCENT);
				g.text(font, label, b[0] + 2, ly + 2, 0xFFFFFFFF, false);
			}
		}

		// toolbar
		// compact pill in the middle of the screen, hint underneath (Lunar-style)
		if (dragging == null) {
			int tw = 168;
			int tx = (width - tw) / 2, ty = height / 2 - 13;
			Theme.glow(g, tx, ty, tw, 26, 13, 0.5f);
			Theme.glass(g, tx, ty, tw, 26, 13, Theme.GLASS);
			Theme.logo(g, tx + 7, ty + 5, 16);
			ghostButton(g, "reset", tx + 30, ty + 5, 60, 16, "Reset", this::reset);
			primaryButton(g, "done", tx + 96, ty + 5, 64, 16, "Done", this::onClose);
			String help = "Drag to move · Scroll to resize · Right-click to hide";
			g.text(font, help, (width - font.width(help)) / 2, ty + 32, Theme.alpha(Theme.TEXT, 0.75f), true);
		}
	}

	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		// dashed outline
		for (int i = x; i < x + w; i += 4) {
			g.fill(i, y, Math.min(i + 2, x + w), y + 1, c);
			g.fill(i, y + h - 1, Math.min(i + 2, x + w), y + h, c);
		}
		for (int j = y; j < y + h; j += 4) {
			g.fill(x, j, x + 1, Math.min(j + 2, y + h), c);
			g.fill(x + w - 1, j, x + w, Math.min(j + 2, y + h), c);
		}
	}

	private void reset() {
		SwiftConfig.get().modules.clear();
		SwiftConfig.save();
	}

	private HudModule at(double mx, double my) {
		Minecraft mc = Minecraft.getInstance();
		for (int i = Modules.ALL.size() - 1; i >= 0; i--) {
			HudModule m = Modules.ALL.get(i);
			if (!m.enabled()) continue;
			int[] b = m.bounds(mc, width, height);
			if (Theme.inside(mx, my, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4)) return m;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) return true;
		HudModule m = at(e.x(), e.y());
		if (m == null) return false;
		if (e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
			m.settings().enabled = false;
			return true;
		}
		int[] b = m.bounds(Minecraft.getInstance(), width, height);
		dragging = m;
		grabX = e.x() - b[0];
		grabY = e.y() - b[1];
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging == null) return false;
		int[] b = dragging.bounds(Minecraft.getInstance(), width, height);
		double nx = e.x() - grabX;
		double ny = e.y() - grabY;
		guideX = guideY = false;
		// snap to screen edges and centre lines
		if (Math.abs(nx) < SNAP) nx = 0;
		if (Math.abs(nx + b[2] - width) < SNAP) nx = width - b[2];
		if (Math.abs(nx + b[2] / 2.0 - width / 2.0) < SNAP) { nx = width / 2.0 - b[2] / 2.0; guideX = true; }
		if (Math.abs(ny) < SNAP) ny = 0;
		if (Math.abs(ny + b[3] - height) < SNAP) ny = height - b[3];
		if (Math.abs(ny + b[3] / 2.0 - height / 2.0) < SNAP) { ny = height / 2.0 - b[3] / 2.0; guideY = true; }
		SwiftConfig.Module s = dragging.settings();
		s.x = (float) Math.max(0, Math.min(1, nx / width));
		s.y = (float) Math.max(0, Math.min(1, ny / height));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = null;
		guideX = guideY = false;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		HudModule m = at(mx, my);
		if (m == null) return false;
		SwiftConfig.Module s = m.settings();
		s.scale = Math.round(Math.max(0.5f, Math.min(2.5f, s.scale + (float) Math.signum(sy) * 0.1f)) * 10) / 10f;
		return true;
	}
}
