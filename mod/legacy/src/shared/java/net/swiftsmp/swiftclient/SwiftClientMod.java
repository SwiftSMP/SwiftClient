package net.swiftsmp.swiftclient;

import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.platform.Platform;
import net.swiftsmp.swiftclient.ui.SwiftMenuScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The 1.21.x build keeps to vanilla hooks only (no Fabric API): the mixins call these
 * methods for ticks, key edges and presence, and the platform layer maps the few APIs
 * that moved between 1.21.x releases.
 */
public final class SwiftClientMod implements ClientModInitializer {
	public static final String MOD_ID = "swiftclient";
	public static final Logger LOG = LoggerFactory.getLogger("SwiftClient");

	/** Lines starting with this marker are read by the launcher (Discord presence). */
	private static final String PRESENCE_MARKER = "[SwiftClient:presence] ";

	public static final int KEY_MENU = 344; // GLFW_KEY_RIGHT_SHIFT
	public static final int KEY_HUD = 297;  // GLFW_KEY_F6
	public static final int KEY_ZOOM = 67;  // GLFW_KEY_C

	private static boolean menuDown;
	private static boolean hudDown;
	private static boolean wasPlaying;
	private static String lastPresence;
	private static int debugTicks = -1;

	@Override
	public void onInitializeClient() {
		SwiftConfig.load();
		String capeApi = System.getProperty("swiftclient.capeApi", "");
		CapeManager.init(capeApi);
		LOG.info("SwiftClient {} loaded for 1.21.x (capes: {})", version(), capeApi.isEmpty() ? "off" : capeApi);
		presence("menu", null);
	}

	/** Called from the Minecraft tick mixin. */
	public static void onTick(Minecraft mc) {
		boolean playing = mc.player != null;
		if (playing && !wasPlaying) {
			CapeManager.clear();
			reportPresence(mc);
			if (SwiftConfig.get().brightMode) Tweaks.applyBrightness();
			debugTicks = 0;
		} else if (!playing && wasPlaying) {
			CapeManager.clear();
			presence("menu", null);
		}
		wasPlaying = playing;

		boolean menu = Platform.isKeyDown(KEY_MENU);
		if (menu && !menuDown && Platform.screen() == null) Platform.setScreen(new SwiftMenuScreen());
		menuDown = menu;

		boolean hud = Platform.isKeyDown(KEY_HUD);
		if (hud && !hudDown) {
			SwiftConfig.get().hudVisible = !SwiftConfig.get().hudVisible;
			SwiftConfig.save();
		}
		hudDown = hud;

		Tweaks.tick(mc);
		debugTick(mc);
	}

	private static void debugTick(Minecraft mc) {
		String debug = System.getProperty("swiftclient.debug", "");
		if (debug.isEmpty() || debugTicks < 0 || mc.player == null) return;
		debugTicks++;
		if (debugTicks == 40 && debug.contains("cape")) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		if (debugTicks == 200 && debug.contains("menu")) Platform.setScreen(new SwiftMenuScreen());
		if (debugTicks == 300 && debug.contains("menu")) Platform.setScreen(new SwiftMenuScreen("visibility"));
	}

	private static void reportPresence(Minecraft mc) {
		if (mc.hasSingleplayerServer()) {
			presence("singleplayer", null);
			return;
		}
		ServerData server = mc.getCurrentServer();
		presence("multiplayer", server == null ? null : server.ip);
	}

	static void presence(String state, String server) {
		// Only print on change: this runs from the tick loop.
		String key = state + "|" + (server == null ? "" : server);
		if (key.equals(lastPresence)) return;
		lastPresence = key;
		JsonObject json = new JsonObject();
		json.addProperty("state", state);
		if (server != null) json.addProperty("server", server);
		// stdout, not the logger: the launcher reads the raw process output.
		System.out.println(PRESENCE_MARKER + json);
	}

	public static String zoomKeyName() {
		return "C";
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	/** The running Minecraft version, read from Loader so it works on every 1.21.x. */
	public static String minecraftVersion() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("1.21.x");
	}
}
