package net.swiftsmp.swiftclient.ui;

import net.swiftsmp.swiftclient.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.swiftsmp.swiftclient.ServerPin;
import net.swiftsmp.swiftclient.SwiftBoost;
import net.swiftsmp.swiftclient.SwiftClientMod;
import net.swiftsmp.swiftclient.Tweaks;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.hud.HudModule;
import net.swiftsmp.swiftclient.hud.Modules;
import net.swiftsmp.swiftclient.lyrics.LyricsService;
import net.swiftsmp.swiftclient.lyrics.NowPlaying;
import net.swiftsmp.swiftclient.lyrics.NowPlayingReader;

/**
 * The SwiftClient menu (Right Shift), in the SwiftSMP website's design: opaque navy panels
 * with 1px hairlines, a sidebar with an orange left accent, Pixelify headings and Silkscreen
 * numbers, and big mod cards with ENABLED/DISABLED chips.
 */
public final class SwiftMenuScreen extends SwiftScreen {
	private enum Tab {
		HOME("Home", "Your client at a glance"),
		MODS("Mods", "HUD mods you can place anywhere"),
		LYRICS("Lyrics", "Now playing and synced lyrics"),
		VISIBILITY("Visibility", "Show or hide parts of your screen"),
		TWEAKS("Tweaks", "Gameplay quality of life"),
		BOOST("Boost", "SwiftClient-exclusive performance"),
		SERVERS("Servers", "SwiftSMP and partner worlds");

		final String label;
		final String subtitle;

		Tab(String label, String subtitle) {
			this.label = label;
			this.subtitle = subtitle;
		}
	}

	/** A switch row used by the Visibility and Tweaks tabs. */
	private record Row(String key, Item icon, String title, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {}

	private static Tab lastTab = Tab.HOME;
	private Tab tab = lastTab;
	private String search = "";
	private float open;
	private float tabFade = 1f;
	private int ticks;
	private float scroll;
	private float scrollTarget;
	private int contentHeight;
	private int viewHeight;
	private int[] body = new int[4];

	public SwiftMenuScreen() {
		super("SwiftClient");
	}

	/** Opens on a given tab by name (used by the -Dswiftclient.debug test hook). */
	public SwiftMenuScreen(String tabName) {
		this();
		for (Tab t : Tab.values()) if (t.name().equalsIgnoreCase(tabName)) tab = t;
	}

	@Override
	public void tick() {
		ticks++;
	}

	@Override
	public void onClose() {
		SwiftConfig.save();
		lastTab = tab;
		// Closing from the title must land on the SwiftClient title, not vanilla's TitleScreen.
		if (Minecraft.getInstance().level == null) Compat.setScreen(new SwiftTitleScreen());
		else super.onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
			search = search.substring(0, search.length() - 1);
			return true;
		}
		if (e.key() == InputConstants.KEY_ESCAPE && !search.isEmpty()) {
			search = "";
			return true;
		}
		if (SwiftClientMod.isMenuKey(e.key()) && search.isEmpty()) {
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		// Typing anywhere searches, like Lunar.
		String c = e.codepointAsString();
		if (search.length() < 24 && !c.isBlank() || (!search.isEmpty() && c.equals(" "))) {
			search += c;
			return true;
		}
		return false;
	}

	private boolean matches(String... texts) {
		if (search.isEmpty()) return true;
		String q = search.toLowerCase(Locale.ROOT);
		for (String t : texts) if (t != null && t.toLowerCase(Locale.ROOT).contains(q)) return true;
		return false;
	}

	private void select(Tab t) {
		if (t != tab) {
			tabFade = 0f;
			scroll = scrollTarget = 0;
		}
		tab = t;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (Theme.inside(mx, my, body[0], body[1], body[2], body[3])) {
			scrollTarget = (float) Math.max(0, Math.min(Math.max(0, contentHeight - viewHeight), scrollTarget - sy * 24));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	// ---- layout ------------------------------------------------------------------------

	@Override
	protected void draw(GuiGraphicsExtractor g, float delta) {
		open += (1f - open) * 0.25f;
		tabFade += (1f - tabFade) * 0.3f;
		int pw = Math.min(560, width - 16);
		int ph = Math.min(304, height - 16);
		int px = (width - pw) / 2;
		int py = (height - ph) / 2;

		// open animation: grow from 96% around the centre
		float s = 0.96f + 0.04f * open;
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-width / 2f, -height / 2f);

		Theme.glow(g, px, py, pw, ph, 10, 0.35f * open);
		Theme.glass(g, px, py, pw, ph, 14, Theme.GLASS);

		int sw = 112;
		drawSidebar(g, px, py, sw, ph);

		int cx = px + sw + 12, cy = py + 10, cw = pw - sw - 22;
		drawHeader(g, cx, cy, cw);
		int bodyY = cy + 30;
		int bodyH = py + ph - 8 - bodyY;
		body = new int[] {cx - 4, bodyY - 4, cw + 8, bodyH + 4};
		viewHeight = bodyH;
		scroll += (scrollTarget - scroll) * 0.35f;
		int sy = bodyY - Math.round(scroll);
		// a few px of padding so card glows are not cut off at the top
		clip(g, cx - 4, bodyY - 4, cx + cw + 4, bodyY + bodyH);
		int end = switch (tab) {
			case HOME -> drawHome(g, cx, sy, cw);
			case MODS -> drawMods(g, cx, sy, cw);
			case LYRICS -> drawLyrics(g, cx, sy, cw);
			case VISIBILITY -> drawRows(g, cx, sy, cw, visibilityRows());
			case TWEAKS -> drawTweaks(g, cx, sy, cw);
			case BOOST -> drawBoost(g, cx, sy, cw);
			case SERVERS -> drawServers(g, cx, sy, cw);
		};
		unclip(g);
		contentHeight = end - sy;
		scrollTarget = Math.max(0, Math.min(Math.max(0, contentHeight - viewHeight), scrollTarget));
		// scrollbar
		if (contentHeight > viewHeight) {
			int trackX = cx + cw + 5;
			int thumbH = Math.max(16, viewHeight * viewHeight / contentHeight);
			int thumbY = bodyY + Math.round((viewHeight - thumbH) * (scroll / (contentHeight - viewHeight)));
			Theme.round(g, trackX, bodyY, 2, viewHeight, 1, Theme.alpha(Theme.PANEL_2, 0.8f));
			Theme.round(g, trackX, thumbY, 2, thumbH, 1, Theme.SCROLL);
		}
		// fade the tab body in when switching
		if (tabFade < 0.98f) g.fill(cx - 4, bodyY - 4, cx + cw + 4, bodyY + bodyH, Theme.alpha(Theme.BG, (1f - tabFade) * 0.85f));
		g.pose().popMatrix();
	}

	private void drawSidebar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		// the site's board-list: a flat navy column, no gradient or separator
		Theme.round(g, x + 1, y + 1, w, h - 2, 11, Theme.PANEL_2);

		Theme.menuLogo(g, x + 9, y + 9, 18);
		g.text(font, Theme.wordmarkSmall(), x + 31, y + 14, Theme.TEXT, false);

		int navTop = y + 38;
		int navBottom = y + h - 52;
		int step = Math.max(20, Math.min(25, (navBottom - navTop) / Tab.values().length));
		int rowH = Math.min(22, step - 2);
		int ny = navTop;
		for (Tab t : Tab.values()) {
			boolean active = tab == t;
			float hv = hover("nav" + t, x + 6, ny, w - 12, rowH);
			if (active) {
				// selected board button: orange tint plus an inset orange bar on the left
				Theme.round(g, x + 6, ny, w - 12, rowH, 6, 0x1FFC6D00);
				Theme.round(g, x + 6, ny, 2, rowH, 1, Theme.ACCENT);
			} else if (hv > 0.02f) {
				Theme.round(g, x + 6, ny, w - 12, rowH, 6, Theme.alpha(0xFFFFFFFF, 0.04f * hv));
			}
			Theme.tabIcon(g, t.name(), x + 12, ny + (rowH - 16) / 2, active, hv);
			g.text(font, Theme.displayPixel(t.label), x + 32, ny + (rowH - 8) / 2, active ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, hv), false);
			clickable(x + 6, ny, w - 12, rowH, () -> select(t));
			ny += step;
		}

		int by = y + h - 40;
		primaryButton(g, "edit", x + 8, by, w - 16, 18, "Edit HUD", () -> Compat.setScreen(new HudEditorScreen(this)));
		String ver = "v" + SwiftClientMod.version().replaceAll("\\+.*", "");
		Component verText = Theme.display(ver);
		g.text(font, verText, x + (w - font.width(verText)) / 2, by + 24, Theme.FAINT, false);
	}

	private void drawHeader(GuiGraphicsExtractor g, int x, int y, int w) {
		g.text(font, Theme.displayPixel(tab.label).withColor(Theme.TEXT), x, y + 2, Theme.TEXT, false);
		g.text(font, Theme.display(tab.subtitle), x, y + 14, Theme.MUTED, false);

		int sw = Math.min(120, w / 2 - 6);
		int sx = x + w - sw;
		boolean focused = !search.isEmpty();
		Theme.panel(g, sx, y + 2, sw, 18, 9, Theme.PANEL_2, focused ? Theme.ACCENT : Theme.BORDER);
		// magnifier
		Theme.round(g, sx + 7, y + 7, 6, 6, 3, Theme.MUTED);
		Theme.round(g, sx + 8, y + 8, 4, 4, 2, Theme.SURFACE_2);
		g.fill(sx + 12, y + 12, sx + 14, y + 14, Theme.MUTED);
		if (search.isEmpty()) {
			g.text(font, Theme.display("Type to search"), sx + 18, y + 7, Theme.MUTED, false);
		} else {
			String shown = font.plainSubstrByWidth(search, sw - 26);
			g.text(font, Theme.display(shown), sx + 18, y + 7, Theme.TEXT, false);
			if (ticks / 6 % 2 == 0) g.fill(sx + 18 + font.width(shown) + 1, y + 6, sx + 19 + font.width(shown) + 1, y + 15, Theme.ACCENT);
		}
	}

	// ---- mods --------------------------------------------------------------------------

	private int drawMods(GuiGraphicsExtractor g, int x, int y, int w) {
		List<HudModule> shown = new ArrayList<>();
		for (HudModule m : Modules.ALL) if (matches(m.name, m.description)) shown.add(m);
		if (shown.isEmpty()) {
			empty(g, x, y, w);
			return y + 60;
		}
		int cols = w >= 360 ? 4 : 3;
		int gap = 7;
		int cardW = (w - gap * (cols - 1)) / cols;
		int cardH = 66;
		for (int i = 0; i < shown.size(); i++) {
			int cx = x + (i % cols) * (cardW + gap);
			int cy = y + (i / cols) * (cardH + gap);
			modCard(g, shown.get(i), cx, cy, cardW, cardH);
		}
		return y + ((shown.size() + cols - 1) / cols) * (cardH + gap);
	}

	private void modCard(GuiGraphicsExtractor g, HudModule m, int x, int y, int w, int h) {
		SwiftConfig.Module s = m.settings();
		float hv = hover("mod" + m.id, x, y, w, h);
		int lift = Math.round(hv);
		y -= lift;
		Theme.panel(g, x, y, w, h, 10, Theme.PANEL_2,
			s.enabled ? Theme.mix(Theme.BORDER, Theme.ACCENT, 0.6f + 0.4f * hv) : Theme.mix(Theme.LINE_SOFT, 0xFF303A62, hv));

		// icon tile, the site's .ah .slot
		int tx = x + (w - 26) / 2, ty = y + 7;
		Theme.panel(g, tx, ty, 26, 26, 7, 0xFF161C3A, Theme.BORDER);
		Theme.moduleIcon(g, m.id, tx + 5, ty + 5);

		Component name = Theme.display(font.plainSubstrByWidth(m.name, w - 8));
		g.text(font, name, x + (w - font.width(name)) / 2, y + 37, s.enabled ? Theme.TEXT : Theme.MUTED, false);

		// state chip, the site's selected tab
		int by = y + h - 15;
		Theme.panel(g, x + 5, by, w - 10, 11, 5, s.enabled ? Theme.mix(Theme.PANEL_2, Theme.ACCENT, 0.16f) : Theme.BG_DEEP, s.enabled ? Theme.ACCENT : Theme.LINE_SOFT);
		Component label = Theme.num(s.enabled ? "ENABLED" : "DISABLED");
		g.text(font, label, x + (w - font.width(label)) / 2, by + 2, s.enabled ? Theme.ACCENT_BRIGHT : Theme.MUTED, false);
		clickable(x, y, w, h, () -> { s.enabled = !s.enabled; SwiftConfig.save(); });
	}

	private void empty(GuiGraphicsExtractor g, int x, int y, int w) {
		String t = "Nothing matches \"" + search + "\"";
		Component msg = Theme.display(t);
		g.text(font, msg, x + (w - font.width(msg)) / 2, y + 40, Theme.MUTED, false);
	}

	// ---- lyrics --------------------------------------------------------------------------

	private int drawLyrics(GuiGraphicsExtractor g, int x, int y, int w) {
		SwiftConfig c = SwiftConfig.get();
		LyricsService.State state = LyricsService.get().state();
		NowPlaying now = state.now;
		int yy = y;
		int cardH = 84;
		int accent = LyricsService.get().accent(Theme.ACCENT);
		Theme.glass(g, x, yy, w, cardH, 9, Theme.GLASS_LIGHT);
		g.fill(x, yy, x + 3, yy + cardH, accent);

		if (now.hasTrack()) {
			g.text(font, Theme.display(font.plainSubstrByWidth(now.title, w - 26)), x + 12, yy + 9, Theme.TEXT, false);
			String meta = now.artist;
			if (!now.app.isEmpty()) meta = meta.isEmpty() ? now.app : meta + "  ·  " + now.app;
			if (!now.playing) meta = meta + "  ·  Paused";
			g.text(font, Theme.display(font.plainSubstrByWidth(meta, w - 26)), x + 12, yy + 23, Theme.MUTED, false);
			g.text(font, Theme.display(font.plainSubstrByWidth(lyricLine(state), w - 26)), x + 12, yy + 42, Theme.TEXT, false);
			if (now.durationMs > 0) {
				int barW = w - 26;
				g.fill(x + 12, yy + cardH - 11, x + 12 + barW, yy + cardH - 10, Theme.alpha(0xFFFFFF, 0.16f));
				int filled = (int) Math.max(0, Math.min(barW, barW * state.positionMs() / (float) now.durationMs));
				g.fill(x + 12, yy + cardH - 11, x + 12 + filled, yy + cardH - 10, accent);
			}
		} else {
			g.text(font, Theme.display("Nothing playing"), x + 12, yy + 12, Theme.TEXT, false);
			g.text(font, Theme.display("Start Spotify, Apple Music, YouTube Music or a browser."), x + 12, yy + 29, Theme.MUTED, false);
			g.text(font, Theme.display(NowPlayingReader.isWindows()
				? "Music is read from your Windows media session."
				: "Allow music access in the SwiftClient launcher drawer."), x + 12, yy + 48, Theme.FAINT, false);
		}
		yy += cardH + 8;

		int bw = (w - 12) / 3;
		ghostButton(g, "lprev", x, yy, bw, 20, "Prev", () -> LyricsService.get().command("prev"));
		primaryButton(g, "lplay", x + bw + 6, yy, bw, 20, now.playing ? "Pause" : "Play", () -> LyricsService.get().command("toggle"));
		ghostButton(g, "lnext", x + (bw + 6) * 2, yy, bw, 20, "Next", () -> LyricsService.get().command("next"));
		yy += 28;

		int rowsY = sectionHeader(g, "In the sky", x, yy, w);
		yy = drawRows(g, x, rowsY, w, List.of(
			new Row("lyrics", Items.JUKEBOX, "In-world lyrics", "3D words in the sky (F7)",
				() -> c.lyricsEnabled, v -> c.lyricsEnabled = v),
			new Row("lyricsword", Items.NAME_TAG, "Word by word", "Show one word at a time",
				() -> c.lyricsWordByWord, v -> c.lyricsWordByWord = v),
			new Row("bassmode", Items.NOTE_BLOCK, "Bass pulse", "Pump the screen and words to the music",
				() -> c.bassMode, v -> c.bassMode = v),
			new Row("island", Items.NAME_TAG, "Now-playing island", "Song pill at the top of the screen",
				() -> c.nowPlayingIsland, v -> c.nowPlayingIsland = v))) + 4;

		int cy = yy;
		int ch = 28;
		Theme.glass(g, x, cy, w, ch, 7, Theme.GLASS_LIGHT);
		g.text(font, Theme.display("Height"), x + 10, cy + 10, Theme.TEXT, false);
		ghostButton(g, "lheightminus", x + w - 126, cy + 5, 40, 18, "-1", () -> {
			c.lyricsSkyHeight = Math.max(0f, c.lyricsSkyHeight - 1f);
			SwiftConfig.save();
		});
		String height = String.format(java.util.Locale.ROOT, "%.0f", c.lyricsSkyHeight);
		g.text(font, Theme.num(height), x + w - 74 - font.width(Theme.num(height)) / 2, cy + 11, Theme.GOLD, false);
		ghostButton(g, "lheightplus", x + w - 42, cy + 5, 34, 18, "+1", () -> {
			c.lyricsSkyHeight = Math.min(40f, c.lyricsSkyHeight + 1f);
			SwiftConfig.save();
		});
		yy += ch + 6;

		cy = yy;
		Theme.glass(g, x, cy, w, ch, 7, Theme.GLASS_LIGHT);
		g.text(font, Theme.display("Size"), x + 10, cy + 10, Theme.TEXT, false);
		ghostButton(g, "lscaleminus", x + w - 126, cy + 5, 40, 18, "-0.1", () -> {
			c.lyricsScale = Math.max(0.5f, Math.round((c.lyricsScale - 0.1f) * 10) / 10f);
			SwiftConfig.save();
		});
		String scale = String.format(java.util.Locale.ROOT, "%.1fx", c.lyricsScale);
		g.text(font, Theme.num(scale), x + w - 74 - font.width(Theme.num(scale)) / 2, cy + 11, Theme.GOLD, false);
		ghostButton(g, "lscaleplus", x + w - 42, cy + 5, 34, 18, "+0.1", () -> {
			c.lyricsScale = Math.min(2f, Math.round((c.lyricsScale + 0.1f) * 10) / 10f);
			SwiftConfig.save();
		});
		yy += ch + 6;

		cy = yy;
		Theme.glass(g, x, cy, w, ch, 7, Theme.GLASS_LIGHT);
		g.text(font, Theme.display("Sync"), x + 10, cy + 10, Theme.TEXT, false);
		ghostButton(g, "loffminus", x + w - 158, cy + 5, 40, 18, "-250", () -> {
			c.lyricsOffsetMs = Math.max(-10000, c.lyricsOffsetMs - 250);
			SwiftConfig.save();
		});
		String offset = (c.lyricsOffsetMs > 0 ? "+" : "") + c.lyricsOffsetMs + " ms";
		g.text(font, Theme.num(offset), x + w - 112 - font.width(Theme.num(offset)) / 2, cy + 11, Theme.GOLD, false);
		ghostButton(g, "loffplus", x + w - 64, cy + 5, 40, 18, "+250", () -> {
			c.lyricsOffsetMs = Math.min(10000, c.lyricsOffsetMs + 250);
			SwiftConfig.save();
		});
		yy += ch + 8;

		g.text(font, Theme.display("Words from LRCLIB. Only the song title and artist are sent."), x, yy, Theme.FAINT, false);
		return yy + 20;
	}

	private static String lyricLine(LyricsService.State state) {
		if (state.lyrics == null || !state.lyrics.hasLines()) {
			return switch (state.status) {
				case LOADING -> "Finding lyrics...";
				case INSTRUMENTAL -> "Instrumental";
				case ERROR -> "Lyrics offline, retrying";
				case NONE -> "No synced lyrics";
				default -> "Waiting for music";
			};
		}
		int index = state.lyrics.indexAt(Math.max(0, state.positionMs() - SwiftConfig.get().lyricsOffsetMs));
		return state.lyrics.displayTextAt(index);
	}

	// ---- switch rows (Visibility, Tweaks) -------------------------------------------------------

	private List<Row> visibilityRows() {
		SwiftConfig c = SwiftConfig.get();
		return List.of(
			new Row("hud", Items.SPYGLASS, "SwiftClient HUD", "Every HUD mod at once (F6)", () -> c.hudVisible, v -> c.hudVisible = v),
			new Row("hudbg", Items.PAINTING, "HUD backgrounds", "Glass behind HUD mods", () -> c.hudBackground, v -> c.hudBackground = v),
			new Row("score", Items.OAK_SIGN, "Scoreboard", "The sidebar on the right", () -> !c.hideScoreboard, v -> c.hideScoreboard = !v),
			new Row("boss", Items.DRAGON_HEAD, "Boss bar", "Boss and event bars on top", () -> !c.hideBossBar, v -> c.hideBossBar = !v),
			new Row("effects", Items.POTION, "Potion effects", "Effect icons, top right", () -> !c.hideEffects, v -> c.hideEffects = !v),
			new Row("titles", Items.NAME_TAG, "Titles", "Big titles and subtitles", () -> !c.hideTitles, v -> c.hideTitles = !v),
			new Row("helditem", Items.ITEM_FRAME, "Held item name", "Name above the hotbar", () -> !c.hideHeldItemName, v -> c.hideHeldItemName = !v));
	}

	private List<Row> tweakRows() {
		SwiftConfig c = SwiftConfig.get();
		return List.of(
			new Row("sprint", Items.SUGAR, "Auto sprint", "Sprint whenever you walk forward", () -> c.autoSprint, v -> c.autoSprint = v),
			new Row("bright", Items.BLAZE_POWDER, "Bright mode", "Max brightness in caves and at night", () -> c.brightMode, v -> { c.brightMode = v; Tweaks.applyBrightness(); }),
			new Row("zoom", Items.SPYGLASS, "Zoom", "Hold " + SwiftClientMod.zoomKeyName() + " to zoom in", () -> c.smoothZoom, v -> c.smoothZoom = v),
			new Row("cape", Items.FEATHER, "Show my cloak", "Your SwiftClient cloak, in HD", () -> c.showOwnCape, v -> c.showOwnCape = v),
			new Row("badge", Items.FIREWORK_ROCKET, "SwiftSMP badge", "Server badge on SwiftSMP", () -> c.swiftsmpHud, v -> c.swiftsmpHud = v));
	}

	/** Two-column grid of switch rows; returns the y below the last row. */
	private int drawRows(GuiGraphicsExtractor g, int x, int y, int w, List<Row> rows) {
		List<Row> shown = rows.stream().filter(r -> matches(r.title, r.desc)).toList();
		if (shown.isEmpty()) {
			empty(g, x, y, w);
			return y + 60;
		}
		int gap = 6;
		int cols = w >= 250 ? 2 : 1;
		int rw = (w - gap * (cols - 1)) / cols;
		int rh = 28;
		for (int i = 0; i < shown.size(); i++) {
			row(g, shown.get(i), x + (i % cols) * (rw + gap), y + (i / cols) * (rh + gap), rw, rh);
		}
		return y + ((shown.size() + cols - 1) / cols) * (rh + gap);
	}

	private void row(GuiGraphicsExtractor g, Row r, int x, int y, int w, int h) {
		boolean on = r.get.get();
		float hv = hover("row" + r.key, x, y, w, h);
		Theme.glass(g, x, y, w, h, 7, Theme.mix(Theme.GLASS_LIGHT, 0xFF1E2750, hv));
		int textW = w - 50;
		g.text(font, Theme.display(font.plainSubstrByWidth(r.title, textW)), x + 10, y + 5, on ? Theme.TEXT : Theme.MUTED, false);
		g.text(font, Theme.display(font.plainSubstrByWidth(r.desc, textW)), x + 10, y + 16, Theme.MUTED, false);
		Theme.toggle(g, x + w - 27, y + 9, on, hv);
		clickable(x, y, w, h, () -> { r.set.accept(!on); SwiftConfig.save(); });
	}

	private int drawTweaks(GuiGraphicsExtractor g, int x, int y, int w) {
		int below = drawRows(g, x, y, w, tweakRows());
		if (!matches("zoom strength")) return below;
		SwiftConfig c = SwiftConfig.get();
		int h = 28;
		int rw = w >= 250 ? (w - 6) / 2 : w;
		int ry = below;
		Theme.glass(g, x, ry, rw, h, 7, Theme.GLASS_LIGHT);
		g.text(font, Theme.display("Zoom strength"), x + 9, ry + 10, Theme.TEXT, false);
		int sx = x + rw - 66;
		ghostButton(g, "zminus", sx, ry + 6, 16, 16, "-", () -> { c.zoomFactor = Math.max(2, c.zoomFactor - 1); SwiftConfig.save(); });
		String val = Math.round(c.zoomFactor) + "x";
		g.text(font, Theme.display(val), sx + 33 - font.width(Theme.display(val)) / 2, ry + 10, Theme.GOLD, false);
		ghostButton(g, "zplus", sx + 50, ry + 6, 16, 16, "+", () -> { c.zoomFactor = Math.min(10, c.zoomFactor + 1); SwiftConfig.save(); });
		return ry + h + 6;
	}

	private int drawBoost(GuiGraphicsExtractor g, int x, int y, int w) {
		SwiftConfig c = SwiftConfig.get();
		Minecraft mc = Minecraft.getInstance();
		int h = 82;
		float hv = hover("boostcard", x, y, w, h);
		if (c.swiftBoost) Theme.glow(g, x, y, w, h, 9, 0.4f + hv * 0.3f);
		Theme.glass(g, x, y, w, h, 11, Theme.mix(Theme.GLASS_LIGHT, 0xFF1E2750, hv));
		g.text(font, Theme.display("SwiftBoost"), x + 12, y + 10, Theme.TEXT, false);
		g.text(font, Theme.display("SwiftClient exclusive · adaptive performance"), x + 12, y + 24, Theme.MUTED, false);

		float tv = hover("boosttoggle", x + w - 38, y + 8, 30, 20);
		Theme.toggle(g, x + w - 34, y + 12, c.swiftBoost, tv);
		clickable(x + w - 38, y + 8, 30, 20, () -> { c.swiftBoost = !c.swiftBoost; SwiftConfig.save(); });

		String state = !c.swiftBoost
			? "OFF · adapts video settings to hold your FPS target"
			: SwiftBoost.active()
				? "ON · " + mc.getFps() + " FPS · level " + SwiftBoost.level() + "/5 · render " + mc.options.renderDistance().get()
				: "ON · applies when you close the menu";
		g.text(font, Theme.display(state), x + 12, y + 46, c.swiftBoost ? Theme.GOLD : Theme.MUTED, false);

		Component label = Theme.display("FPS target");
		g.text(font, label, x + 12, y + 62, Theme.TEXT, false);
		int sx = x + w - 96;
		ghostButton(g, "bminus", sx, y + 58, 16, 16, "-", () -> { c.boostTarget = Math.max(30, c.boostTarget - 10); SwiftConfig.save(); });
		Component val = Theme.display(c.boostTarget + " FPS");
		g.text(font, val, sx + 20 + (46 - font.width(val)) / 2, y + 62, Theme.GOLD, false);
		ghostButton(g, "bplus", sx + 70, y + 58, 16, 16, "+", () -> { c.boostTarget = Math.min(240, c.boostTarget + 10); SwiftConfig.save(); });

		int ny = y + h + 8;
		Theme.glass(g, x, ny, w, 56, 8, Theme.GLASS_LIGHT);
		g.text(font, Theme.display("Five levels, one FPS target"), x + 12, ny + 7, Theme.TEXT, false);
		g.text(font, Theme.display("Level 1 trims particles and entity distance."), x + 12, ny + 19, Theme.MUTED, false);
		g.text(font, Theme.display("Higher levels cut clouds, shadows, smooth light."), x + 12, ny + 30, Theme.MUTED, false);
		g.text(font, Theme.display("Off restores every video setting you had."), x + 12, ny + 41, Theme.MUTED, false);
		return ny + 56 + 6;
	}

	// ---- home ---------------------------------------------------------------------------

	private int drawHome(GuiGraphicsExtractor g, int x, int y, int w) {
		Minecraft mc = Minecraft.getInstance();
		ServerData current = mc.getCurrentServer();
		boolean swift = Modules.onSwiftSmp(mc);
		ServerPin.Pinned main = mainServer();
		boolean canJoin = mc.level == null;

		int hh = 64;
		if (swift) Theme.glow(g, x, y, w, hh, 9, 0.8f);
		Theme.glass(g, x, y, w, hh, 11, Theme.GLASS_LIGHT);
		Theme.menuLogo(g, x + 12, y + 14, 36);

		String title;
		String subtitle;
		if (current != null) {
			title = swift ? "SwiftSMP" : current.name;
			subtitle = current.ip;
		} else if (mc.hasSingleplayerServer()) {
			title = "Singleplayer";
			subtitle = "Your world, your rules";
		} else {
			title = "Welcome back, " + mc.getUser().getName();
			subtitle = "Pick a server or jump into a world";
		}
		int textW = Math.max(60, w - (w < 300 ? 30 : 170));
		g.text(font, Theme.display(font.plainSubstrByWidth(title, textW)), x + 58, y + 13, Theme.TEXT, false);
		g.text(font, Theme.display(font.plainSubstrByWidth(subtitle, textW)), x + 58, y + 27, Theme.MUTED, false);
		g.text(font, Theme.display("Right Shift opens this menu anywhere"), x + 58, y + 43, Theme.FAINT, false);

		if (current != null) {
			statePill(g, x + w - 12, y + 24, swift ? "CONNECTED" : "IN GAME", Theme.OK);
		} else if (mc.hasSingleplayerServer()) {
			statePill(g, x + w - 12, y + 24, "SINGLEPLAYER", Theme.GOLD);
		} else if (canJoin && main != null && w >= 300) {
			primaryButton(g, "homejoin", x + w - 110, y + 22, 98, 20, "Join " + main.name(), () -> join(main));
		} else {
			statePill(g, x + w - 12, y + 24, "READY", Theme.MUTED);
		}

		int online = mc.getConnection() == null ? -1 : mc.getConnection().getOnlinePlayers().size();
		int ping = -1;
		if (mc.player != null && mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null) ping = info.getLatency();
		}
		String[][] chips = {
			{String.valueOf(mc.getFps()), "fps"},
			{ping < 0 ? "—" : ping + " ms", "ping"},
			{online < 0 ? "—" : String.valueOf(online), "online"},
		};
		int statY = y + hh + 8;
		int cw = (w - 12) / 3;
		for (int i = 0; i < chips.length; i++) {
			int cx = x + i * (cw + 6);
			Theme.glass(g, cx, statY, cw, 34, 7, Theme.GLASS_LIGHT);
			Component value = Theme.num(chips[i][0]);
			g.text(font, value, cx + (cw - font.width(value)) / 2, statY + 7, Theme.GOLD, false);
			g.text(font, Theme.display(chips[i][1]), cx + (cw - font.width(Theme.display(chips[i][1]))) / 2, statY + 20, Theme.MUTED, false);
		}

		int qy = sectionHeader(g, "Quick settings", x, statY + 34 + 12, w);
		int end = drawRows(g, x, qy, w, quickRows());

		int teaY = end + 8;
		float hv = hover("serversteaser", x, teaY, w, 30);
		Theme.glass(g, x, teaY, w, 30, 7, Theme.mix(Theme.GLASS_LIGHT, 0xFF1E2750, hv));
		Component serversLabel = Theme.display("Servers");
		g.text(font, serversLabel, x + 10, teaY + 7, Theme.TEXT, false);
		int count = servers().size();
		g.text(font, Theme.display(count + (count == 1 ? " pinned world" : " pinned worlds")),
			x + 10 + font.width(serversLabel) + 10, teaY + 7, Theme.MUTED, false);
		ghostButton(g, "browse", x + w - 76, teaY + 5, 66, 20, "Browse", () -> select(Tab.SERVERS));
		clickable(x, teaY, w, 30, () -> select(Tab.SERVERS));
		return teaY + 34;
	}

	private List<Row> quickRows() {
		SwiftConfig c = SwiftConfig.get();
		return List.of(
			new Row("qhud", Items.SPYGLASS, "HUD", "All modules (F6)", () -> c.hudVisible, v -> c.hudVisible = v),
			new Row("qboost", Items.FIREWORK_ROCKET, "SwiftBoost", "Adaptive FPS", () -> c.swiftBoost, v -> c.swiftBoost = v),
			new Row("qsprint", Items.SUGAR, "Auto sprint", "Always sprint forward", () -> c.autoSprint, v -> c.autoSprint = v),
			new Row("qbright", Items.BLAZE_POWDER, "Bright mode", "Light up caves and night", () -> c.brightMode, v -> { c.brightMode = v; Tweaks.applyBrightness(); }));
	}

	// ---- servers ------------------------------------------------------------------------

	private List<ServerPin.Pinned> servers() {
		return ServerPin.visibleServers();
	}

	private ServerPin.Pinned mainServer() {
		List<ServerPin.Pinned> all = servers();
		for (ServerPin.Pinned server : all) if (!server.partner()) return server;
		return all.isEmpty() ? null : all.get(0);
	}

	private void join(ServerPin.Pinned server) {
		ServerData data = new ServerData(server.name(), server.address(), ServerData.Type.OTHER);
		ConnectScreen.startConnecting(this, Minecraft.getInstance(), ServerAddress.parseString(server.address()), data, false, null);
	}

	private int drawServers(GuiGraphicsExtractor g, int x, int y, int w) {
		boolean canJoin = Minecraft.getInstance().level == null;
		int yy = y;
		for (ServerPin.Pinned server : servers()) {
			serverCard(g, server, x, yy, w, canJoin);
			yy += 53;
		}
		if (!canJoin) {
			ghostButton(g, "openmp", x, yy, w, 22, "Open the multiplayer list", () -> Compat.setScreen(new JoinMultiplayerScreen(this)));
			yy += 30;
		}
		g.text(font, Theme.display("Pinned at the top of your multiplayer list."), x, yy + 2, Theme.FAINT, false);
		return yy + 18;
	}

	private void serverCard(GuiGraphicsExtractor g, ServerPin.Pinned server, int x, int y, int w, boolean canJoin) {
		int h = 46;
		float hv = hover("srv" + server.address(), x, y, w, h);
		Theme.glass(g, x, y, w, h, 10, Theme.mix(Theme.GLASS_LIGHT, 0xFF1E2750, hv));
		if (Theme.hasServerIcon(server.address())) {
			Theme.panel(g, x + 8, y + 8, 30, 30, 8, 0xFF161C3A, Theme.BORDER);
			Theme.serverIcon(g, Theme.serverIconKey(server.address()), x + 9, y + 9, 28);
		} else if (!server.partner()) {
			Theme.menuLogo(g, x + 8, y + 8, 30);
		} else {
			Theme.panel(g, x + 8, y + 8, 30, 30, 8, 0xFF161C3A, Theme.BORDER);
			String initial = server.name().isBlank() ? "?" : server.name().substring(0, 1).toUpperCase(Locale.ROOT);
			Component letter = Theme.displayPixel(initial);
			g.text(font, letter, x + 23 - font.width(letter) / 2, y + 17, Theme.ACCENT_BRIGHT, false);
		}
		Component name = Theme.display(font.plainSubstrByWidth(server.name(), w - 130));
		g.text(font, name, x + 48, y + 9, Theme.TEXT, false);
		tag(g, x + 48 + font.width(name) + 8, y + 8, server.partner() ? "PARTNER" : "MAIN", server.partner() ? Theme.GOLD : Theme.MUTED);
		g.text(font, Theme.display(server.address()), x + 48, y + 25, Theme.MUTED, false);
		if (canJoin) ghostButton(g, "srvjoin" + server.address(), x + w - 58, y + 13, 50, 20, "Join", () -> join(server));
		else tag(g, x + w - 12 - (font.width(Theme.display("PINNED")) + 10), y + 17, "PINNED", Theme.FAINT);
	}

	/** The site's .pkg-art-tag: Silkscreen, tinted fill, 1px tinted border. */
	private void tag(GuiGraphicsExtractor g, int x, int y, String text, int color) {
		Component label = Theme.num(text);
		int w = font.width(label) + 10;
		Theme.panel(g, x, y, w, 12, 6, Theme.mix(Theme.PANEL_2, color, 0.10f), Theme.mix(Theme.PANEL_2, color, 0.45f));
		g.text(font, label, x + 5, y + 2, color, false);
	}

	/** The site's .chip.live: a tinted pill with a matching hairline. */
	private void statePill(GuiGraphicsExtractor g, int right, int y, String text, int color) {
		Component label = Theme.num(text);
		int w = font.width(label) + 14;
		Theme.panel(g, right - w, y, w, 16, 8, Theme.mix(Theme.PANEL_2, color, 0.08f), Theme.mix(Theme.PANEL_2, color, 0.35f));
		g.text(font, label, right - w + 7, y + 4, color, false);
	}

	private int sectionHeader(GuiGraphicsExtractor g, String text, int x, int y, int w) {
		g.text(font, Theme.displayPixel(text), x, y, Theme.TEXT, false);
		return y + 16;
	}
}
