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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.swiftsmp.swiftclient.SwiftClientMod;
import net.swiftsmp.swiftclient.Tweaks;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.hud.HudModule;
import net.swiftsmp.swiftclient.hud.Modules;

/**
 * The SwiftClient menu (Right Shift), Lunar-style: frosted glass, a sidebar, a search bar,
 * big mod cards with ENABLED/DISABLED bars, all in SwiftSMP orange.
 */
public final class SwiftMenuScreen extends SwiftScreen {
	private enum Tab {
		MODS("Mods", Items.COMPASS, "HUD mods you can place anywhere"),
		VISIBILITY("Visibility", Items.SPYGLASS, "Show or hide parts of your screen"),
		TWEAKS("Tweaks", Items.SUGAR, "Gameplay quality of life"),
		SERVER("Server", Items.FIREWORK_ROCKET, "Where you are playing");

		final String label;
		final Item icon;
		final String subtitle;

		Tab(String label, Item icon, String subtitle) {
			this.label = label;
			this.icon = icon;
			this.subtitle = subtitle;
		}
	}

	/** A switch row used by the Visibility and Tweaks tabs. */
	private record Row(String key, Item icon, String title, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {}

	private static Tab lastTab = Tab.MODS;
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
		super.onClose();
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
		int pw = Math.min(470, width - 16);
		int ph = Math.min(272, height - 16);
		int px = (width - pw) / 2;
		int py = (height - ph) / 2;

		// open animation: grow from 96% around the centre
		float s = 0.96f + 0.04f * open;
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-width / 2f, -height / 2f);

		Theme.glow(g, px, py, pw, ph, 10, 0.6f * open);
		Theme.glass(g, px, py, pw, ph, 10, Theme.GLASS);

		int sw = 96;
		drawSidebar(g, px, py, sw, ph);

		int cx = px + sw + 12, cy = py + 10, cw = pw - sw - 22;
		drawHeader(g, cx, cy, cw);
		drawTopBar(g, cx, cy + 22, cw);
		int bodyY = cy + 46;
		int bodyH = py + ph - 8 - bodyY;
		body = new int[] {cx - 4, bodyY - 4, cw + 8, bodyH + 4};
		viewHeight = bodyH;
		scroll += (scrollTarget - scroll) * 0.35f;
		int sy = bodyY - Math.round(scroll);
		// a few px of padding so card glows are not cut off at the top
		clip(g, cx - 4, bodyY - 4, cx + cw + 4, bodyY + bodyH);
		int end = switch (tab) {
			case MODS -> drawMods(g, cx, sy, cw);
			case VISIBILITY -> drawRows(g, cx, sy, cw, visibilityRows());
			case TWEAKS -> drawTweaks(g, cx, sy, cw);
			case SERVER -> drawServer(g, cx, sy, cw);
		};
		unclip(g);
		contentHeight = end - sy;
		scrollTarget = Math.max(0, Math.min(Math.max(0, contentHeight - viewHeight), scrollTarget));
		// scrollbar
		if (contentHeight > viewHeight) {
			int trackX = cx + cw + 5;
			int thumbH = Math.max(16, viewHeight * viewHeight / contentHeight);
			int thumbY = bodyY + Math.round((viewHeight - thumbH) * (scroll / (contentHeight - viewHeight)));
			Theme.round(g, trackX, bodyY, 2, viewHeight, 1, 0x14FFFFFF);
			Theme.round(g, trackX, thumbY, 2, thumbH, 1, Theme.alpha(Theme.ACCENT, 0.8f));
		}
		// fade the tab body in when switching
		if (tabFade < 0.98f) g.fill(cx - 4, bodyY - 4, cx + cw + 4, bodyY + bodyH, Theme.alpha(Theme.BG, (1f - tabFade) * 0.85f));
		g.pose().popMatrix();
	}

	private void drawSidebar(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		Theme.round(g, x + 1, y + 1, w, h - 2, 9, 0x40080A16);
		g.fill(x + w, y + 8, x + w + 1, y + h - 8, Theme.STROKE);

		Theme.logo(g, x + 9, y + 9, 18);
		g.text(font, Theme.wordmark(), x + 31, y + 14, Theme.TEXT, false);

		int ny = y + 40;
		for (Tab t : Tab.values()) {
			boolean active = tab == t;
			float hv = hover("nav" + t, x + 6, ny, w - 12, 22);
			if (active) {
				Theme.glow(g, x + 6, ny, w - 12, 22, 6, 0.8f);
				Theme.round(g, x + 6, ny, w - 12, 22, 6, Theme.alpha(Theme.ACCENT, 0.22f));
				Theme.round(g, x + 6, ny + 5, 2, 12, 1, Theme.ACCENT);
			} else if (hv > 0.02f) {
				Theme.round(g, x + 6, ny, w - 12, 22, 6, Theme.alpha(0xFFFFFFFF, 0.06f * hv));
			}
			icon(g, t.icon, x + 12, ny + 3);
			g.text(font, Theme.display(t.label), x + 32, ny + 7, active ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, hv), false);
			clickable(x + 6, ny, w - 12, 22, () -> select(t));
			ny += 25;
		}

		int by = y + h - 40;
		primaryButton(g, "edit", x + 8, by, w - 16, 18, "Edit HUD", () -> Compat.setScreen(new HudEditorScreen(this)));
		String ver = "v" + SwiftClientMod.version().replaceAll("\\+.*", "");
		g.text(font, ver, x + (w - font.width(ver)) / 2, by + 24, Theme.FAINT, false);
	}

	/** Lunar-style row of tab pills in the header; the sidebar stays for the same tabs. */
	private void drawTopBar(GuiGraphicsExtractor g, int x, int y, int w) {
		int pillW = Math.min(64, (w - 8) / Tab.values().length - 4);
		int right = x + w;
		for (int i = Tab.values().length - 1; i >= 0; i--) {
			Tab t = Tab.values()[i];
			int px0 = right - pillW;
			boolean active = tab == t;
			float hv = hover("top" + t, px0, y, pillW, 16);
			Theme.glass(g, px0, y, pillW, 16, 8, active ? Theme.alpha(Theme.ACCENT, 0.55f) : Theme.mix(Theme.GLASS, Theme.alpha(Theme.ACCENT, 0.35f), hv));
			String label = t.label;
			g.text(font, label, px0 + (pillW - font.width(label)) / 2, y + 4, active ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, hv), false);
			clickable(px0, y, pillW, 16, () -> select(t));
			right = px0 - 4;
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, int x, int y, int w) {
		g.text(font, Theme.display(tab.label).withColor(Theme.TEXT), x, y + 2, Theme.TEXT, false);
		g.text(font, tab.subtitle, x, y + 14, Theme.FAINT, false);

		int sw = Math.min(120, w / 2 - 6);
		int sx = x + w - sw;
		boolean focused = !search.isEmpty();
		Theme.glass(g, sx, y + 2, sw, 18, 9, focused ? Theme.alpha(Theme.SURFACE_3, 0.85f) : Theme.GLASS_LIGHT);
		if (focused) Theme.round(g, sx + 1, y + 19, sw - 2, 1, 0, Theme.alpha(Theme.ACCENT, 0.8f));
		// magnifier
		Theme.round(g, sx + 7, y + 7, 6, 6, 3, Theme.MUTED);
		Theme.round(g, sx + 8, y + 8, 4, 4, 2, Theme.SURFACE_2);
		g.fill(sx + 12, y + 12, sx + 14, y + 14, Theme.MUTED);
		if (search.isEmpty()) {
			g.text(font, "Type to search", sx + 18, y + 7, Theme.FAINT, false);
		} else {
			String shown = font.plainSubstrByWidth(search, sw - 26);
			g.text(font, shown, sx + 18, y + 7, Theme.TEXT, false);
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
		if (s.enabled) Theme.glow(g, x, y, w, h, 8, 0.35f + 0.4f * hv);
		Theme.glass(g, x, y, w, h, 8, Theme.mix(Theme.GLASS_LIGHT, 0xD01E2750, hv));

		// icon tile
		int tx = x + (w - 26) / 2, ty = y + 7;
		Theme.round(g, tx, ty, 26, 26, 7, s.enabled ? Theme.alpha(Theme.ACCENT, 0.22f) : 0x14FFFFFF);
		Item icon = m.icon();
		// Item icons need a loaded world; on the title screen show the logo instead.
		if (icon == null || Minecraft.getInstance().level == null) Theme.logo(g, tx + 5, ty + 5, 16);
		else icon(g, icon, tx + 5, ty + 5);

		Component name = Theme.display(font.plainSubstrByWidth(m.name, w - 8));
		g.text(font, name, x + (w - font.width(name)) / 2, y + 37, s.enabled ? Theme.TEXT : Theme.MUTED, false);

		// ENABLED / DISABLED bar
		int by = y + h - 15;
		Theme.round(g, x + 5, by, w - 10, 11, 5, s.enabled ? Theme.mix(Theme.ACCENT, Theme.ACCENT_BRIGHT, hv) : Theme.alpha(0xFFFFFFFF, 0.07f + 0.05f * hv));
		String label = s.enabled ? "ENABLED" : "DISABLED";
		g.text(font, label, x + (w - font.width(label)) / 2, by + 2, s.enabled ? 0xFFFFFFFF : Theme.MUTED, false);
		clickable(x, y, w, h, () -> { s.enabled = !s.enabled; SwiftConfig.save(); });
	}

	private void empty(GuiGraphicsExtractor g, int x, int y, int w) {
		String t = "Nothing matches \"" + search + "\"";
		g.text(font, t, x + (w - font.width(t)) / 2, y + 40, Theme.MUTED, false);
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
			new Row("cape", Items.FEATHER, "Show my cape", "Your SwiftClient cape", () -> c.showOwnCape, v -> c.showOwnCape = v),
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
		Theme.glass(g, x, y, w, h, 7, Theme.mix(Theme.GLASS_LIGHT, 0xD01E2750, hv));
		Theme.round(g, x + 5, y + 5, 18, 18, 5, on ? Theme.alpha(Theme.ACCENT, 0.22f) : 0x14FFFFFF);
		icon(g, r.icon, x + 6, y + 6);
		int textW = w - 60;
		g.text(font, Theme.display(font.plainSubstrByWidth(r.title, textW)), x + 28, y + 5, on ? Theme.TEXT : Theme.MUTED, false);
		g.text(font, font.plainSubstrByWidth(r.desc, textW), x + 28, y + 16, Theme.FAINT, false);
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

	// ---- server ------------------------------------------------------------------------

	private int drawServer(GuiGraphicsExtractor g, int x, int y, int w) {
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		boolean swift = Modules.onSwiftSmp(mc);

		int hh = 52;
		if (swift) Theme.glow(g, x, y, w, hh, 9, 0.8f);
		Theme.glass(g, x, y, w, hh, 9, Theme.GLASS_LIGHT);
		Theme.logo(g, x + 10, y + 10, 32);
		if (server == null) {
			g.text(font, Theme.display(mc.hasSingleplayerServer() ? "Singleplayer" : "Not connected"), x + 50, y + 14, Theme.TEXT, false);
			g.text(font, "Join SwiftSMP from the launcher in one click.", x + 50, y + 28, Theme.MUTED, false);
		} else {
			Component name = swift ? Theme.display("Swift").withColor(Theme.TEXT).append(Theme.display("SMP").withColor(Theme.ACCENT)) : Theme.display(server.name);
			g.text(font, name, x + 50, y + 13, Theme.TEXT, false);
			g.text(font, server.ip, x + 50, y + 27, Theme.MUTED, false);
		}

		// stat chips
		int online = mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
		int ping = 0;
		if (mc.player != null && mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null) ping = info.getLatency();
		}
		String[][] chips = {{String.valueOf(online), "online"}, {ping + " ms", "ping"}, {String.valueOf(mc.getFps()), "fps"}};
		int cw = (w - 12) / 3;
		for (int i = 0; i < chips.length; i++) {
			int cx = x + i * (cw + 6), cy = y + hh + 8;
			Theme.glass(g, cx, cy, cw, 32, 7, Theme.GLASS_LIGHT);
			Component v = Theme.display(chips[i][0]);
			g.text(font, v, cx + (cw - font.width(v)) / 2, cy + 6, Theme.GOLD, false);
			g.text(font, chips[i][1], cx + (cw - font.width(chips[i][1])) / 2, cy + 19, Theme.MUTED, false);
		}

		int ty = y + hh + 50;
		String[] tips = {"Right Shift opens this menu anywhere.", "Edit HUD: drag to move, scroll to resize.", "Capes and mods live in the SwiftClient launcher."};
		for (int i = 0; i < tips.length; i++) {
			Theme.round(g, x + 2, ty + 3 + i * 12, 3, 3, 1, Theme.ACCENT);
			g.text(font, font.plainSubstrByWidth(tips[i], w - 10), x + 9, ty + i * 12, Theme.MUTED, false);
		}
		return ty + tips.length * 12 + 4;
	}
}
