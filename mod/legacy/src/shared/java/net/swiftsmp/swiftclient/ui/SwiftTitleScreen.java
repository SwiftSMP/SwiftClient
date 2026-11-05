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
		if (ticks == 60 && debug.contains("worlds")) Minecraft.getInstance().setScreen(new SelectWorldScreen(this));
		if (ticks == 120 && debug.contains("worldsclose") && Minecraft.getInstance().screen instanceof SelectWorldScreen screen) screen.onClose();
		if (ticks == 60 && debug.contains("titlemenu")) {
			Minecraft.getInstance().setScreen(new SwiftMenuScreen(debug.contains("boost") ? "boost" : debug.contains("servers") ? "servers" : debug.contains("mods") ? "mods" : ""));
		}
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
		// Short screens (GUI scale 3+ at 720p/1080p) compress the column so the icon row and
		// footer stay on screen. Tall screens keep the original roomy layout.
		boolean compact = height < 343;
		int logoSize = compact ? 28 : 40;
		int joinH = compact ? 26 : 30;
		int rowH = compact ? 18 : 22;
		int step = compact ? 23 : 27;
		int iconSize = compact ? 18 : 22;
		int top;
		int by;
		if (compact) {
			int iconY = height - 25 - iconSize;
			int optY = iconY - 4 - rowH;
			by = optY - step * 3 - 6 - joinH;
			top = Math.max(4, by - logoSize - 36);
		} else {
			top = Math.max(14, height / 2 - 112);
			by = top + logoSize + 52;
		}
		float bob = (float) Math.sin((ticks + delta) / 20.0) * 2f;
		g.pushMatrix();
		g.translate(0, bob);
		Theme.glow(g, cx - logoSize / 2, top, logoSize, logoSize, 20, 0.5f);
		Theme.logo(g, cx - logoSize / 2, top, logoSize);
		g.popMatrix();

		g.pushMatrix();
		g.translate(cx, top + logoSize + (compact ? 2 : 8));
		g.scale(compact ? 0.75f : 1f);
		Component word = Theme.wordmarkPixel();
		g.text(font, word, -font.width(word) / 2, 0, Theme.TEXT, true);
		g.popMatrix();

		// buttons
		int bw = Math.min(220, width - 40);
		int bx = cx - bw / 2;

		// Join SwiftSMP: the site's solid orange .play-btn with its #ff8d33 rim and dark ink
		float hv = hover("join", bx, by, bw, joinH);
		Theme.glow(g, bx, by, bw, joinH, 10, 0.7f + hv * 0.5f);
		Theme.round(g, bx, by, bw, joinH, 10, 0xFFFF8D33);
		Theme.round(g, bx + 1, by + 1, bw - 2, joinH - 2, 9, Theme.mix(Theme.ACCENT, Theme.ACCENT_BRIGHT, hv));
		Theme.logo(g, bx + 8, by + 7, 16);
		g.text(font, Theme.display("Join " + Platform.serverName()), bx + 30, by + 6, Theme.INK, false);
		g.text(font, Theme.display(Platform.serverAddress()), bx + 30, by + 17, 0xB31A0D00, false);
		Component play = Theme.num("PLAY");
		Theme.round(g, bx + bw - 42, by + 8, 34, 14, 7, 0x331A0D00);
		g.text(font, play, bx + bw - 25 - font.width(play) / 2, by + 11, Theme.INK, false);
		clickable(bx, by, bw, joinH, () -> Platform.joinServer(this));

		int y = by + joinH + 8;
		glassButton(g, "sp", bx, y, bw, rowH, "Singleplayer", () -> mc.setScreen(new SelectWorldScreen(this)));
		y += step;
		glassButton(g, "mp", bx, y, bw, rowH, "Multiplayer", () -> mc.setScreen(new JoinMultiplayerScreen(this)));
		y += step;
		glassButton(g, "servers", bx, y, bw, rowH, "Servers", () -> mc.setScreen(new PartnersScreen(this)));
		y += step;
		int half = (bw - 5) / 2;
		glassButton(g, "opt", bx, y, half, rowH, "Options", this::openOptions);
		glassButton(g, "quit", bx + half + 5, y, bw - half - 5, rowH, "Quit", mc::stop);

		// Icon row under the main menu. Opens the SwiftClient menu, the in-game home for capes,
		// mods and HUD settings. Friends and chat live in the launcher for now.
		int iy = y + step;
		int iw = iconSize;
		iconButton(g, "icon-menu", cx - iw / 2, iy, iw, "SwiftClient menu", () -> mc.setScreen(new SwiftMenuScreen()));

		// small glass pills, top right
		int px = width - 8;
		if (FabricLoader.getInstance().isModLoaded("modmenu")) px = pill(g, "mods", px, 8, "Mods", this::openModMenu) - 5;
		px = pill(g, "lang", px, 8, "Language", () -> mc.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()))) - 5;
		pill(g, "menu", px, 8, "SwiftClient", () -> mc.setScreen(new SwiftMenuScreen()));

		// footer
		String left = "SwiftClient " + SwiftClientMod.version().replaceAll("\\+.*", "") + "  \u00b7  Minecraft " + SwiftClientMod.minecraftVersion();
		Component leftText = Theme.display(left);
		g.text(font, leftText, 6, height - 12, Theme.alpha(Theme.TEXT, 0.55f), false);
		String right = "Copyright Mojang AB. Do not distribute!";
		Component rightText = Theme.display(right);
		int rw = font.width(rightText);
		boolean hot = Theme.inside(mouseX, mouseY, width - rw - 6, height - 13, rw, 10);
		g.text(font, rightText, width - rw - 6, height - 12, Theme.alpha(Theme.TEXT, hot ? 0.9f : 0.55f), false);
		clickable(width - rw - 6, height - 13, rw, 10, () -> mc.setScreen(new CreditsAndAttributionScreen(this)));

		// fade in from the loading screen
		if (fade < 0.99f) g.fill(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 1f - fade));
	}

	/** A square icon-only button; the label shows as a hint on hover. */
	private void iconButton(Gfx g, String key, int x, int y, int size, String label, Runnable action) {
		float hv = hover(key, x, y, size, size);
		Theme.panel(g, x, y, size, size, 6, Theme.PANEL_2, Theme.mix(Theme.BORDER, Theme.ACCENT, hv));
		// The mascot texture, not an ItemStack: the same icon on every Minecraft version.
		Theme.logo(g, x + 3, y + 3, size - 6);
		if (hv > 0.5f) {
			Component hint = Theme.display(label);
			int tw = font.width(hint) + 8;
			g.fill(x + size / 2 - tw / 2, y - 12, x + size / 2 + tw / 2, y - 3, 0xC0000000);
			g.text(font, hint, x + size / 2 - tw / 2 + 4, y - 10, Theme.TEXT, false);
		}
		clickable(x, y, size, size, action);
	}

	private void glassButton(Gfx g, String key, int x, int y, int w, int h, String label, Runnable action) {
		float hv = hover(key, x, y, w, h);
		Theme.panel(g, x, y, w, h, 8, Theme.PANEL_2, Theme.mix(Theme.BORDER, Theme.ACCENT, hv));
		Component text = Theme.display(label);
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, Theme.mix(Theme.MUTED, Theme.TEXT, hv), false);
		clickable(x, y, w, h, action);
	}

	/** The site's .chip pill, aligned to its right edge; returns its left x. */
	private int pill(Gfx g, String key, int right, int y, String label, Runnable action) {
		Component text = Theme.display(label);
		int w = font.width(text) + 16;
		int x = right - w;
		float hv = hover(key, x, y, w, 16);
		Theme.panel(g, x, y, w, 16, 8, Theme.PANEL_2, Theme.mix(Theme.BORDER, Theme.ACCENT, hv));
		g.text(font, text, x + 8, y + 4, Theme.mix(Theme.MUTED, Theme.TEXT, hv), false);
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
