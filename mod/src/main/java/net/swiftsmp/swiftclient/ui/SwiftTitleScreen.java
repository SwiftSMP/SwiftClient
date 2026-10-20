package net.swiftsmp.swiftclient.ui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.swiftsmp.swiftclient.SwiftClientMod;

/** SwiftClient's title screen: the vanilla panorama under SwiftSMP glass, with one-click Join SwiftSMP. */
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
		// Dev/test hook: -Dswiftclient.debug=options opens vanilla Options to check the button theme.
		String debug = System.getProperty("swiftclient.debug", "");
		if (ticks == 60 && debug.contains("options")) Minecraft.getInstance().gui.setScreen(new OptionsScreen(this, Minecraft.getInstance().options));
		if (ticks == 60 && debug.contains("titlemenu")) Minecraft.getInstance().gui.setScreen(new SwiftMenuScreen());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		extractPanorama(g, delta);
		// navy wash, darker toward the bottom, with the site's orange glow at the top
		g.fillGradient(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 0.35f), Theme.alpha(Theme.BG_DEEP, 0.85f));
		g.fillGradient(0, 0, width, height / 2, Theme.alpha(Theme.ACCENT, 0.10f), 0x00000000);
	}

	private static String serverAddress() {
		return System.getProperty("swiftclient.server.address", "swiftsmp.net");
	}

	private static String serverName() {
		return System.getProperty("swiftclient.server.name", "SwiftSMP");
	}

	private void joinSwiftSmp() {
		Minecraft mc = Minecraft.getInstance();
		ServerData data = new ServerData(serverName(), serverAddress(), ServerData.Type.OTHER);
		ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(serverAddress()), data, false, null);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float delta) {
		Minecraft mc = Minecraft.getInstance();
		fade += (1f - fade) * 0.12f;
		int cx = width / 2;

		// logo + wordmark
		int logoSize = 40;
		int top = Math.max(14, height / 2 - 112);
		float bob = (float) Math.sin((ticks + delta) / 20.0) * 2f;
		g.pose().pushMatrix();
		g.pose().translate(0, bob);
		Theme.glow(g, cx - logoSize / 2, top, logoSize, logoSize, 20, 0.5f);
		Theme.logo(g, cx - logoSize / 2, top, logoSize);
		g.pose().popMatrix();

		g.pose().pushMatrix();
		g.pose().translate(cx, top + logoSize + 8);
		g.pose().scale(2.4f, 2.4f);
		Component word = Theme.wordmark();
		g.text(font, word, -font.width(word) / 2, 0, Theme.TEXT, true);
		g.pose().popMatrix();
		String tagline = "Built for SwiftSMP";
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
		g.text(font, Theme.display("Join " + serverName()), bx + 30, by + 6, 0xFFFFFFFF, false);
		g.text(font, serverAddress(), bx + 30, by + 17, 0xCCFFFFFF, false);
		Component play = Theme.display("PLAY");
		Theme.round(g, bx + bw - 42, by + 8, 34, 14, 7, 0x33FFFFFF);
		g.text(font, play, bx + bw - 25 - font.width(play) / 2, by + 11, 0xFFFFFFFF, false);
		clickable(bx, by, bw, 30, this::joinSwiftSmp);

		int y = by + 38;
		glassButton(g, "sp", bx, y, bw, 22, Items.GRASS_BLOCK, "Singleplayer", () -> mc.gui.setScreen(new SelectWorldScreen(this)));
		y += 27;
		glassButton(g, "mp", bx, y, bw, 22, Items.ENDER_EYE, "Multiplayer", () -> mc.gui.setScreen(new JoinMultiplayerScreen(this)));
		y += 27;
		int half = (bw - 5) / 2;
		glassButton(g, "opt", bx, y, half, 22, Items.COMPARATOR, "Options", () -> mc.gui.setScreen(new OptionsScreen(this, mc.options)));
		glassButton(g, "quit", bx + half + 5, y, bw - half - 5, 22, Items.BARRIER, "Quit", mc::stop);

		// small glass pills, top right
		int px = width - 8;
		if (FabricLoader.getInstance().isModLoaded("modmenu")) px = pill(g, "mods", px, 8, "Mods", this::openModMenu) - 5;
		px = pill(g, "lang", px, 8, "Language", () -> mc.gui.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()))) - 5;
		pill(g, "menu", px, 8, "SwiftClient", () -> mc.gui.setScreen(new SwiftMenuScreen()));

		// footer
		String left = "SwiftClient " + SwiftClientMod.version().replaceAll("\\+.*", "") + "  ·  Minecraft " + SharedConstants.getCurrentVersion().name();
		g.text(font, left, 6, height - 12, Theme.alpha(Theme.TEXT, 0.55f), false);
		String right = "Copyright Mojang AB. Do not distribute!";
		int rw = font.width(right);
		boolean hot = Theme.inside(mouseX, mouseY, width - rw - 6, height - 13, rw, 10);
		g.text(font, right, width - rw - 6, height - 12, Theme.alpha(Theme.TEXT, hot ? 0.9f : 0.55f), false);
		clickable(width - rw - 6, height - 13, rw, 10, () -> mc.gui.setScreen(new CreditsAndAttributionScreen(this)));

		// fade in from the loading screen
		if (fade < 0.99f) g.fill(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 1f - fade));
	}

	private void glassButton(GuiGraphicsExtractor g, String key, int x, int y, int w, int h, net.minecraft.world.item.Item icon, String label, Runnable action) {
		float hv = hover(key, x, y, w, h);
		if (hv > 0.05f) Theme.glow(g, x, y, w, h, 8, hv * 0.6f);
		Theme.glass(g, x, y, w, h, 8, Theme.mix(Theme.GLASS, 0xE0222A58, hv));
		if (hv > 0.05f) Theme.round(g, x + 8, y + h - 1, w - 16, 1, 0, Theme.alpha(Theme.ACCENT, hv));
		Component text = Theme.display(label);
		g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, Theme.mix(Theme.alpha(Theme.TEXT, 0.9f), 0xFFFFFFFF, hv), false);
		clickable(x, y, w, h, action);
	}

	/** Glass pill aligned to its right edge; returns its left x. */
	private int pill(GuiGraphicsExtractor g, String key, int right, int y, String label, Runnable action) {
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
			Minecraft.getInstance().gui.setScreen((Screen) cls.getConstructor(Screen.class).newInstance(this));
		} catch (ReflectiveOperationException e) {
			SwiftClientMod.LOG.warn("Mod Menu is not available: {}", e.toString());
		}
	}
}
