package net.swiftsmp.swiftclient.hud;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.ui.Gfx;
import net.swiftsmp.swiftclient.ui.Theme;

/** The built-in HUD modules. */
public final class Modules {
	private Modules() {}

	public static final List<HudModule> ALL = List.of(
		new Fps(), new Cps(), new Ping(), new Coords(), new Clock(), new Keystrokes(), new Armor(), new SwiftSmpBadge());

	public static boolean onSwiftSmp(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		return server != null && server.ip != null && server.ip.toLowerCase().contains("swiftsmp");
	}

	// ---- clicks per second --------------------------------------------------------

	public static final class CpsTracker {
		/** GLFW button ids: 0 = left, 1 = right. */
		private static final Deque<Long> LEFT = new ArrayDeque<>();
		private static final Deque<Long> RIGHT = new ArrayDeque<>();

		public static synchronized void click(int button) {
			if (button == 0) LEFT.add(System.currentTimeMillis());
			else if (button == 1) RIGHT.add(System.currentTimeMillis());
		}

		public static synchronized int left() { return count(LEFT); }

		public static synchronized int right() { return count(RIGHT); }

		private static int count(Deque<Long> q) {
			long cutoff = System.currentTimeMillis() - 1000;
			while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
			return q.size();
		}
	}

	// ---- simple one-line modules ------------------------------------------------------

	/** A one-line pill: value in white, unit in muted grey. */
	abstract static class TextModule extends HudModule {
		TextModule(String id, String name, String desc, boolean on, float x, float y) { super(id, name, desc, on, x, y); }

		abstract Component text(Minecraft mc);

		@Override public int width(Minecraft mc) { return mc.font.width(text(mc)) + 10; }

		@Override public int height(Minecraft mc) { return 16; }

		@Override public void render(Gfx g, Minecraft mc, boolean preview) {
			int w = width(mc);
			boolean bg = SwiftConfig.get().hudBackground;
			if (bg) Theme.glass(g, 0, 0, w, 16, 4, Theme.alpha(Theme.BG, 0.55f));
			g.text(mc.font, text(mc), 5, 4, Theme.TEXT, !bg);
		}

		static Component pair(String value, String unit) {
			return Component.literal(value).withColor(Theme.TEXT).append(Component.literal(unit).withColor(Theme.MUTED));
		}
	}

	static final class Fps extends TextModule {
		Fps() { super("fps", "FPS", "Frames per second", true, 0.006f, 0.01f); }

		@Override Component text(Minecraft mc) { return pair(String.valueOf(mc.getFps()), " FPS"); }

		@Override public net.minecraft.world.item.Item icon() { return Items.GLOWSTONE_DUST; }
	}

	static final class Cps extends TextModule {
		Cps() { super("cps", "CPS", "Left and right clicks per second", false, 0.006f, 0.055f); }

		@Override Component text(Minecraft mc) { return pair(CpsTracker.left() + " | " + CpsTracker.right(), " CPS"); }

		@Override public net.minecraft.world.item.Item icon() { return Items.STONE_BUTTON; }
	}

	static final class Ping extends TextModule {
		Ping() { super("ping", "Ping", "Your latency to the server", true, 0.006f, 0.1f); }

		@Override public net.minecraft.world.item.Item icon() { return Items.ENDER_PEARL; }

		@Override Component text(Minecraft mc) {
			int ms = 0;
			if (mc.player != null && mc.getConnection() != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (info != null) ms = info.getLatency();
			}
			int color = ms < 80 ? Theme.OK : ms < 180 ? Theme.GOLD : Theme.DANGER;
			return Component.literal(String.valueOf(ms)).withColor(color).append(Component.literal(" ms").withColor(Theme.MUTED));
		}
	}

	static final class Coords extends TextModule {
		Coords() { super("coords", "Coordinates", "Where you are and which way you face", false, 0.006f, 0.145f); }

		@Override public net.minecraft.world.item.Item icon() { return Items.COMPASS; }

		@Override Component text(Minecraft mc) {
			if (mc.player == null) return pair("0, 64, 0", "  N");
			String xyz = (int) Math.floor(mc.player.getX()) + ", " + (int) Math.floor(mc.player.getY()) + ", " + (int) Math.floor(mc.player.getZ());
			String dir = switch (mc.player.getDirection()) {
				case NORTH -> "N";
				case SOUTH -> "S";
				case EAST -> "E";
				case WEST -> "W";
				default -> "";
			};
			return Component.literal(xyz).withColor(Theme.TEXT).append(Component.literal("  " + dir).withColor(Theme.ACCENT_BRIGHT));
		}
	}

	static final class Clock extends TextModule {
		private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

		Clock() { super("clock", "Clock", "Your local time", false, 0.006f, 0.19f); }

		@Override public net.minecraft.world.item.Item icon() { return Items.CLOCK; }

		@Override Component text(Minecraft mc) { return pair(LocalTime.now().format(TIME), ""); }
	}

	// ---- keystrokes -------------------------------------------------------------------

	static final class Keystrokes extends HudModule {
		private static final int K = 18;
		private static final int GAP = 2;

		Keystrokes() { super("keystrokes", "Keystrokes", "WASD, mouse buttons and jump", false, 0.006f, 0.6f); }

		@Override public net.minecraft.world.item.Item icon() { return Items.LEVER; }

		@Override public int width(Minecraft mc) { return K * 3 + GAP * 2; }

		@Override public int height(Minecraft mc) { return K * 3 + GAP * 3 + 10; }

		@Override public void render(Gfx g, Minecraft mc, boolean preview) {
			var o = mc.options;
			key(g, mc, K + GAP, 0, K, K, "W", o.keyUp);
			key(g, mc, 0, K + GAP, K, K, "A", o.keyLeft);
			key(g, mc, K + GAP, K + GAP, K, K, "S", o.keyDown);
			key(g, mc, (K + GAP) * 2, K + GAP, K, K, "D", o.keyRight);
			int half = (width(mc) - GAP) / 2;
			int row = (K + GAP) * 2;
			key(g, mc, 0, row, half, K, "LMB " + CpsTracker.left(), o.keyAttack);
			key(g, mc, half + GAP, row, half, K, "RMB " + CpsTracker.right(), o.keyUse);
			key(g, mc, 0, row + K + GAP, width(mc), 10, "", o.keyJump);
			int bar = width(mc) / 3;
			g.fill(bar, row + K + GAP + 4, width(mc) - bar, row + K + GAP + 5, o.keyJump.isDown() ? 0xFFFFFFFF : Theme.MUTED);
		}

		private void key(Gfx g, Minecraft mc, int x, int y, int w, int h, String label, KeyMapping km) {
			boolean down = km.isDown();
			if (down) {
				Theme.glow(g, x, y, w, h, 4, 1f);
				Theme.round(g, x, y, w, h, 4, Theme.ACCENT);
			} else if (SwiftConfig.get().hudBackground) {
				Theme.glass(g, x, y, w, h, 4, Theme.alpha(Theme.BG, 0.55f));
			}
			if (!label.isEmpty()) {
				Font f = mc.font;
				g.text(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : Theme.TEXT, false);
			}
		}
	}

	// ---- armor ------------------------------------------------------------------------

	static final class Armor extends HudModule {
		private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};

		Armor() { super("armor", "Armor status", "Armor and held item durability", false, 0.92f, 0.62f); }

		@Override public net.minecraft.world.item.Item icon() { return Items.DIAMOND_CHESTPLATE; }

		private static List<ItemStack> worn(Minecraft mc, boolean preview) {
			List<ItemStack> out = new ArrayList<>();
			for (EquipmentSlot slot : SLOTS) {
				ItemStack s = mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(slot);
				if (!s.isEmpty()) out.add(s);
			}
			if (out.isEmpty() && preview && mc.level != null) out = List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE));
			return out;
		}

		@Override public int width(Minecraft mc) { return 50; }

		@Override public int height(Minecraft mc) { return Math.max(1, worn(mc, true).size()) * 17 + 2; }

		@Override public void render(Gfx g, Minecraft mc, boolean preview) {
			List<ItemStack> items = worn(mc, preview);
			if (items.isEmpty()) return;
			if (SwiftConfig.get().hudBackground) Theme.glass(g, 0, 0, width(mc), items.size() * 17 + 2, 4, Theme.alpha(Theme.BG, 0.55f));
			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				int y = 1 + i * 17;
				g.item(stack, 3, y);
				String label;
				int color = Theme.TEXT;
				if (stack.isDamageableItem()) {
					int left = stack.getMaxDamage() - stack.getDamageValue();
					float frac = left / (float) stack.getMaxDamage();
					label = String.valueOf(left);
					color = frac > 0.5f ? Theme.OK : frac > 0.2f ? Theme.GOLD : Theme.DANGER;
				} else {
					label = "x" + stack.getCount();
				}
				g.text(mc.font, label, 22, y + 5, color, false);
			}
		}
	}

	// ---- SwiftSMP badge -------------------------------------------------------------------

	static final class SwiftSmpBadge extends HudModule {
		SwiftSmpBadge() { super("swiftsmp", "SwiftSMP badge", "Server badge with players online, shown on SwiftSMP", true, 0.84f, 0.01f); }

		@Override public boolean visible(Minecraft mc) {
			return SwiftConfig.get().swiftsmpHud && onSwiftSmp(mc);
		}

		private Component online(Minecraft mc) {
			int n = mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
			return Component.literal(String.valueOf(n)).withColor(Theme.GOLD).append(Component.literal(" online").withColor(Theme.MUTED));
		}

		@Override public int width(Minecraft mc) {
			return 28 + Math.max(mc.font.width(Theme.display("SwiftSMP")), mc.font.width(online(mc))) + 8;
		}

		@Override public int height(Minecraft mc) { return 26; }

		@Override public net.minecraft.world.item.Item icon() { return Items.FIREWORK_ROCKET; }

		@Override public void render(Gfx g, Minecraft mc, boolean preview) {
			int w = width(mc);
			Theme.glass(g, 0, 0, w, 26, 5, Theme.alpha(Theme.BG, 0.6f));
			g.fill(5, 0, w - 5, 1, Theme.ACCENT);
			Theme.logo(g, 5, 4, 18);
			g.text(mc.font, Theme.display("Swift").withColor(Theme.TEXT).append(Theme.display("SMP").withColor(Theme.ACCENT)), 28, 4, Theme.TEXT, true);
			g.text(mc.font, online(mc), 28, 14, Theme.TEXT, true);
		}
	}
}
