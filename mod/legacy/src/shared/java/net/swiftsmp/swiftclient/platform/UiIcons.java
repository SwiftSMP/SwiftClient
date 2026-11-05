package net.swiftsmp.swiftclient.platform;

import java.util.Set;

/** The smooth UI icon ids shipped under assets/swiftclient/ui. */
public final class UiIcons {
	private static final Set<String> IDS = Set.of(
		"fps", "cps", "ping", "coords", "clock", "keystrokes", "armor", "swiftsmp",
		"home", "home_dim", "mods", "mods_dim", "visibility", "visibility_dim",
		"tweaks", "tweaks_dim", "boost", "boost_dim", "servers", "servers_dim");

	public static boolean known(String id) {
		return IDS.contains(id);
	}

	private UiIcons() {}
}
