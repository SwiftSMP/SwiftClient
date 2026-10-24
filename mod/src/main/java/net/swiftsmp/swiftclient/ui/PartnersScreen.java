package net.swiftsmp.swiftclient.ui;

import net.swiftsmp.swiftclient.Compat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.swiftsmp.swiftclient.ServerPin;

/** Partnered servers from the launcher, one click to join. Opened from the title screen. */
public final class PartnersScreen extends SwiftScreen {
	private final Screen parent;

	public PartnersScreen(Screen parent) {
		super("Servers");
		this.parent = parent;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float delta) {
		g.fillGradient(0, 0, width, height, Theme.alpha(Theme.BG_DEEP, 0.35f), Theme.alpha(Theme.BG_DEEP, 0.85f));
		g.fillGradient(0, 0, width, height / 2, Theme.alpha(Theme.ACCENT, 0.10f), 0x00000000);
		int cx = width / 2;
		int bw = Math.min(260, width - 40);
		int bx = cx - bw / 2;
		int top = Math.max(20, height / 2 - 100);

		String title = "Servers";
		g.text(font, Theme.display(title), cx - font.width(Theme.display(title)) / 2, top, Theme.TEXT, true);
		String sub = "Partnered servers and SwiftSMP";
		g.text(font, sub, cx - font.width(sub) / 2, top + 14, Theme.alpha(Theme.GOLD, 0.9f), false);

		int y = top + 36;
		List<ServerPin.Pinned> servers = ServerPin.pinnedServers();
		for (ServerPin.Pinned s : servers) {
			int rowH = 34;
			float hv = hover("row:" + s.address(), bx, y, bw, rowH);
			Theme.glass(g, bx, y, bw, rowH, 8, Theme.mix(Theme.GLASS, 0xE0222A58, hv));
			if (hv > 0.05f) Theme.round(g, bx + 8, y + rowH - 1, bw - 16, 1, 0, Theme.alpha(Theme.ACCENT, hv));
			g.text(font, Theme.display(s.name()), bx + 10, y + 6, 0xFFFFFFFF, false);
			g.text(font, s.address(), bx + 10, y + 18, 0xCCFFFFFF, false);
			if (s.partner()) {
				String tag = "PARTNER";
				int tw = font.width(tag) + 10;
				Theme.round(g, bx + bw - tw - 58, y + 6, tw, 12, 6, Theme.alpha(Theme.GOLD, 0.35f));
				g.text(font, tag, bx + bw - tw - 53, y + 8, Theme.GOLD, false);
			}
			int jw = 46;
			float jv = hover("join:" + s.address(), bx + bw - jw - 8, y + 9, jw, 16);
			Theme.round(g, bx + bw - jw - 8, y + 9, jw, 16, 8, Theme.mix(Theme.ACCENT, Theme.ACCENT_BRIGHT, jv));
			g.text(font, Theme.display("Join"), bx + bw - jw - 8 + (jw - font.width(Theme.display("Join"))) / 2, y + 13, 0xFFFFFFFF, false);
			clickable(bx + bw - jw - 8, y + 9, jw, 16, () -> join(s));
			y += rowH + 6;
		}

		ghostButton(g, "back", bx, y + 6, bw, 22, "Back", () -> Compat.setScreen(parent));
	}

	private void join(ServerPin.Pinned s) {
		Minecraft mc = Minecraft.getInstance();
		ServerData data = new ServerData(s.name(), s.address(), ServerData.Type.OTHER);
		ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(s.address()), data, false, null);
	}
}
