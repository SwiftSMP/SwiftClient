package net.swiftsmp.swiftclient;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

/**
 * Keeps SwiftSMP as the first entry of the Multiplayer server list: added if missing,
 * moved back to the top if the player reordered it. The launcher passes the address.
 */
public final class ServerPin {
	private ServerPin() {}

	public static void pin(Minecraft mc) {
		String address = System.getProperty("swiftclient.server.address", "swiftsmp.net");
		String name = System.getProperty("swiftclient.server.name", "SwiftSMP");
		if (address.isBlank()) return;
		try {
			ServerList list = new ServerList(mc);
			list.load();
			int index = -1;
			for (int i = 0; i < list.size(); i++) {
				if (sameServer(list.get(i).ip, address)) { index = i; break; }
			}
			if (index >= 0 && !list.get(index).ip.equalsIgnoreCase(address)) {
				// e.g. an old play.swiftsmp.net entry: point it at the current address
				list.get(index).ip = address;
				list.save();
			}
			if (index == 0) return;
			if (index < 0) {
				list.add(new ServerData(name, address, ServerData.Type.OTHER), false);
				index = list.size() - 1;
			}
			// Bubble it to the top so the other servers keep their order.
			for (int i = index; i > 0; i--) list.swap(i, i - 1);
			list.save();
		} catch (Exception e) {
			SwiftClientMod.LOG.warn("Could not pin {} in the server list: {}", address, e.toString());
		}
	}

	private static boolean sameServer(String ip, String address) {
		if (ip == null) return false;
		String a = ip.toLowerCase(Locale.ROOT).replaceAll(":25565$", "");
		String b = address.toLowerCase(Locale.ROOT).replaceAll(":25565$", "");
		return a.equals(b) || a.endsWith("swiftsmp.net") && b.endsWith("swiftsmp.net");
	}
}
