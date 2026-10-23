package net.swiftsmp.swiftclient.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.swiftsmp.swiftclient.SwiftClientMod;
import net.swiftsmp.swiftclient.platform.Platform;

/**
 * SwiftClient's title screen, matching the 26.x design: the vanilla panorama under the
 * SwiftSMP navy wash, a bobbing logo, the hero Join button and glass menu buttons with
 * item icons. The platform layer supplies the two screens whose constructors moved.
 */
public final class SwiftTitleScreen extends SwiftScreen {
	private float fade;
	private int ticks;

	public SwiftTitleScreen() {
		super("SwiftClient");
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		ticks++;
		// Dev/test hooks, same keys as the 26.x build.
		String debug = System.getProperty("swiftclient.debug", "");
		if (ticks == 60 && debug.contains("options")) openOptions();
		if (ticks == 60 && debug.contains("titlemenu")) Minecraft.getInstance().setScreen(new SwiftMenuScreen());
	}

	private void openOptions() {
		Minecraft mc = Minecraft.getInstance();
		Screen options = Platform.optionsScreen(this);
		if (options != null) mc.setScreen(options);
	}

	@Override
	protected void draw(Gfx g, float delta) {
		backdrop(g);
		Minecraft mc = Minecraft.getInstance();
		fade += (1f - fade) * 0.12f;
		int cx = width / 2;

		// logo + wordmark
		int logoSize = 40;
		int top = Math.max(14, height / 2 - 112);
		float bob = (float) Math.sin((ticks + delta) / 20.0) * 2f;
		g.pushMatrix();
		g.translate(0, bob);
		Theme.glow(g, cx - logoSize / 2, top, logoSize, logoSize, 20, 0.5f);
		Theme.logo(g, cx - logoSize / 2, top, logoSize);
		g.popMatrix();

		g.pushMatrix();
		g.translate(cx, top + logoSize + 8);
		g.scale(2.4f);
		Component word = Theme.wordmark();
		g.text(font, word, -font.width(word) / 2, 0, Theme.TEXT, true);
		g.popMatrix();
		String tagline = "Built for " + Platform.serverName();
		g.text(font, tagline, cx - font.width(tagline) / 2, top + logoSize + 34, Theme.alpha(Theme.GOLD, 0.9f), false);

		// buttons
		int bw = Math.min(220, width - 40);
		int bx = cx - bw / 2;
		int by = top + logoSize + 52;

		// Join SwiftSMP: the hero button
		float hv = hover("join", bx, by, bw, 30);
		Theme.glow(g, bx, by, bw, 30, 10, 0.9f + hv);
		Theme.round(g, bx, by, bw, 30, 10, Theme.mix(Theme.ACCENT, Theme.ACCENT_BRIGHT, hv));
		g.fill(bx + 10, by + 1, bx + bw - 10, by + 2, 0x40FFFFFF);
		Theme.logo(g, bx + 8, by + 7, 16);
		g.text(font, Theme.display("Join " + Platform.serverName()), bx + 30, by + 6, 0xFFFFFFFF, false);
		g.text(font, Platform.serverAddress(), bx + 30, by + 17, 0xCCFFFFFF, false);
		Component play = Theme.display("PLAY");
		Theme.round(g, bx + bw - 42, by + 8, 34, 14, 7, 0x33FFFFFF);
		g.text(font, play, bx + bw - 25 - font.width(play) / 2, by + 11, 0xFFFFFFFF, false);
		clickable(bx, by, bw, 30, () -> Platform.joinServer(this));

		int y = by + 38;
		glassButton(g, "sp", bx, y, bw, 22, "Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this)));
		y += 27;
		glassButton(g, "mp", bx, y, bw, 22, "Multiplayer", () -> mc.setScreen(new JoinMultiplayerScreen(this)));
		y += 27;
		int half = (bw - 5) / 2;
		glassButton(g, "opt", bx, y, half, 22, "Options", this::openOptions);
		glassButton(g, "quit", bx + half + 5, y, bw - half - 5, 22, "Quit", mc::stop);

		// small glass pills, top right
		int px = width - 8;
		if (FabricLoader.getInstance().isModLoaded("modmenu")) px = pill(g, "mods", px, 8, "Mods", this::openModMenu) - 5;
		px = pill(g, "lang", px, 8, "Language", () -> mc.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()))) - 5;
		pill(g, "menu", px, 8, "SwiftClient", () -> mc.setScreen(new SwiftMenuScreen()));

		// footer
		String left = "SwiftClient " + SwiftClientMod.version().replaceAll("\\+.*", "") + "  \u00b7  Minecraft " + SwiftClientMod.minecraftVersion();
		g.text(font, left, 6, height - 12, Theme.alpha(Theme.TEXT, 0.55f), false);
		String right = "Copyright Mojang AB. Do not distribute!";
		int rw = font.width(right);
		boolean hot = Theme.inside(mouseX, mouseY, width - rw - 6, height - 13, rw, 10);
		g.text(font, right, width - rw - 6, height - 12, Theme.alpha(Theme.TEXT, hot ? 0.9f : 0.55f), false);
		clickable(width - rw - 6, height - 13, rw, 10, () -> mc.setScreen(new CreditsAndAttributionScreen(this)));

		// fade in from the loading screen
		if (fade < 0.99f) g.fill(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 1f - fade));
	}

	private void glassButton(Gfx g, String key, int x, int y, int w, int h, String label, Runnable action) {
		float hv = hover(key, x, y, w, h);
		if (hv > 0.05f) Theme.glow(g, x, y, w, h, 8, hv * 0.6f);
		Theme.glass(g, x, y, w, h, 8, Theme.mix(Theme.GLASS, 0xE0222A58, hv));
		if (hv > 0.05f) Theme.round(g, x + 8, y + h - 1, w - 16, 1, 0, Theme.alpha(Theme.ACCENT, hv));
		Component text = Theme.display(label);
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, Theme.mix(Theme.alpha(Theme.TEXT, 0.9f), 0xFFFFFFFF, hv), false);
		clickable(x, y, w, h, action);
	}

	/** Glass pill aligned to its right edge; returns its left x. */
	private int pill(Gfx g, String key, int right, int y, String label, Runnable action) {
		int w = font.width(label) + 16;
		int x = right - w;
		float hv = hover(key, x, y, w, 16);
		Theme.glass(g, x, y, w, 16, 8, Theme.mix(Theme.GLASS, Theme.alpha(Theme.ACCENT, 0.55f), hv));
		g.text(font, label, x + 8, y + 4, Theme.mix(Theme.alpha(Theme.TEXT, 0.85f), 0xFFFFFFFF, hv), false);
		clickable(x, y, w, 16, action);
		return x;
	}

	private void openModMenu() {
		try {
			Class<?> cls = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			Minecraft.getInstance().setScreen((Screen) cls.getConstructor(Screen.class).newInstance(this));
		} catch (ReflectiveOperationException e) {
			SwiftClientMod.LOG.warn("Mod Menu is not available: {}", e.toString());
		}
	}
}
