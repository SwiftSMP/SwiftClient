package net.swiftsmp.swiftclient.hud;

import net.minecraft.client.Minecraft;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.ui.Gfx;

/** One movable HUD element (FPS, keystrokes, ...). Positions and scale live in {@link SwiftConfig}. */
public abstract class HudModule {
	public final String id;
	public final String name;
	public final String description;
	private final boolean defaultEnabled;
	private final float defaultX;
	private final float defaultY;

	protected HudModule(String id, String name, String description, boolean defaultEnabled, float defaultX, float defaultY) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.defaultEnabled = defaultEnabled;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	public SwiftConfig.Module settings() {
		return SwiftConfig.get().module(id, defaultEnabled, defaultX, defaultY);
	}

	public boolean enabled() {
		return settings().enabled;
	}

	/** Some modules only make sense in certain places (e.g. the SwiftSMP badge). */
	public boolean visible(Minecraft mc) {
		return true;
	}

	/** Icon shown on this module's card in the SwiftClient menu. */
	public net.minecraft.world.item.Item icon() {
		return null;
	}

	/** Unscaled size in GUI pixels. */
	public abstract int width(Minecraft mc);

	public abstract int height(Minecraft mc);

	/** Draws at (0,0) in a matrix already translated and scaled for this module. */
	public abstract void render(Gfx g, Minecraft mc, boolean preview);

	/** Screen-space bounds {x, y, w, h} for this GUI size, clamped to stay on screen. */
	public int[] bounds(Minecraft mc, int guiW, int guiH) {
		SwiftConfig.Module s = settings();
		int w = Math.round(width(mc) * s.scale);
		int h = Math.round(height(mc) * s.scale);
		int x = Math.max(0, Math.min(guiW - w, Math.round(s.x * guiW)));
		int y = Math.max(0, Math.min(guiH - h, Math.round(s.y * guiH)));
		return new int[] {x, y, w, h};
	}
}
