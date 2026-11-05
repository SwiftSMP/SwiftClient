package net.swiftsmp.swiftclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

/**
 * Keeps the launcher's pinned servers at the top of the Multiplayer server list, in the
 * order the launcher gives them (SwiftSMP first, then partners such as ValenMC). A pinned
 * server that is missing is added back, so it cannot be deleted, like Lunar's pinned servers.
 * The launcher passes them as -Dswiftclient.servers="name|address|partner|pinned;...".
 */
public final class ServerPin {
	private ServerPin() {}

	/** One pinned entry parsed from the launcher's property. */
	public record Pinned(String name, String address, boolean partner) {}

	public static List<Pinned> pinnedServers() {
		List<Pinned> out = new ArrayList<>();
		String raw = System.getProperty("swiftclient.servers", "");
		if (raw.isBlank()) {
			// Older launchers only pass the featured server.
			out.add(new Pinned(System.getProperty("swiftclient.server.name", "SwiftSMP"),
				System.getProperty("swiftclient.server.address", "swiftsmp.net"), false));
			return out;
		}
		for (String entry : raw.split(";")) {
			String[] f = entry.split("\\|");
			if (f.length < 4 || f[1].isBlank() || !"1".equals(f[3])) continue;
			out.add(new Pinned(f[0], f[1], "1".equals(f[2])));
		}
		return out;
	}

	/** Partner addresses an admin hid; their existing server-list entries are removed. */
	public static List<String> hiddenServers() {
		String raw = System.getProperty("swiftclient.hiddenServers", "");
		if (raw.isBlank()) return List.of();
		List<String> out = new ArrayList<>();
		for (String entry : raw.split(";")) if (!entry.isBlank()) out.add(entry.trim());
		return out;
	}

	/** The pinned servers players may see, i.e. with hidden partner entries removed. */
	public static List<Pinned> visibleServers() {
		List<String> hidden = hiddenServers();
		List<Pinned> out = new ArrayList<>();
		for (Pinned server : pinnedServers()) {
			boolean skip = false;
			for (String address : hidden) {
				if (sameServer(server.address(), address)) { skip = true; break; }
			}
			if (!skip) out.add(server);
		}
		return out;
	}

	public static void pin(Minecraft mc) {
		List<Pinned> pinned = pinnedServers();
		List<String> hidden = hiddenServers();
		if (pinned.isEmpty() && hidden.isEmpty()) return;
		try {
			ServerList list = new ServerList(mc);
			list.load();
			boolean changed = false;
			for (int i = list.size() - 1; i >= 0; i--) {
				for (String address : hidden) {
					if (sameServer(list.get(i).ip, address)) {
						list.remove(list.get(i));
						changed = true;
						break;
					}
				}
			}
			if (pinned.isEmpty()) {
				if (changed) list.save();
				return;
			}
			// Walk backwards so each pinned server ends up at the top, in launcher order.
			for (int p = pinned.size() - 1; p >= 0; p--) {
				Pinned want = pinned.get(p);
				int index = -1;
				for (int i = 0; i < list.size(); i++) {
					if (sameServer(list.get(i).ip, want.address())) { index = i; break; }
				}
				if (index < 0) {
					list.add(new ServerData(want.name(), want.address(), ServerData.Type.OTHER), false);
					index = list.size() - 1;
				} else if (!list.get(index).ip.equalsIgnoreCase(want.address())) {
					// e.g. an old play.swiftsmp.net entry: point it at the current address
					list.get(index).ip = want.address();
				}
				for (int i = index; i > p; i--) list.swap(i, i - 1);
			}
			list.save();
		} catch (Exception e) {
			SwiftClientMod.LOG.warn("Could not pin servers in the server list: {}", e.toString());
		}
	}

	private static boolean sameServer(String ip, String address) {
		if (ip == null) return false;
		String a = ip.toLowerCase(Locale.ROOT).replaceAll(":25565$", "");
		String b = address.toLowerCase(Locale.ROOT).replaceAll(":25565$", "");
		return a.equals(b) || a.endsWith("swiftsmp.net") && b.endsWith("swiftsmp.net");
	}

	/** True when this server entry is one the launcher pins (SwiftSMP or a partner). */
	public static boolean isPinned(ServerData server) {
		if (server == null || server.ip == null) return false;
		for (Pinned pinned : pinnedServers()) {
			if (sameServer(server.ip, pinned.address())) return true;
		}
		return false;
	}
}
