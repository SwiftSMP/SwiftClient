package net.swiftsmp.swiftclient;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.hud.HudRenderer;
import net.swiftsmp.swiftclient.ui.SwiftMenuScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwiftClientMod implements ClientModInitializer {
	public static final String MOD_ID = "swiftclient";
	public static final Logger LOG = LoggerFactory.getLogger("SwiftClient");

	/** Lines starting with this marker are read by the launcher (Discord presence). */
	private static final String PRESENCE_MARKER = "[SwiftClient:presence] ";

	private static KeyMapping menuKey;
	private static KeyMapping zoomKey;
	private static KeyMapping hudKey;

	@Override
	public void onInitializeClient() {
		SwiftConfig.load();
		String capeApi = System.getProperty("swiftclient.capeApi", "");
		CapeManager.init(capeApi);
		LOG.info("SwiftClient {} loaded (capes: {})", version(), capeApi.isEmpty() ? "off" : capeApi);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "swiftclient"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.swiftclient.menu", InputConstants.KEY_RSHIFT, category));
		zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.swiftclient.zoom", InputConstants.KEY_C, category));
		hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.swiftclient.hud", InputConstants.KEY_F6, category));

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HudRenderer::render);
		// Visibility tab: vanilla HUD parts the player can hide.
		hideable(VanillaHudElements.SCOREBOARD, () -> SwiftConfig.get().hideScoreboard);
		hideable(VanillaHudElements.BOSS_BAR, () -> SwiftConfig.get().hideBossBar);
		hideable(VanillaHudElements.MOB_EFFECTS, () -> SwiftConfig.get().hideEffects);
		hideable(VanillaHudElements.TITLE_AND_SUBTITLE, () -> SwiftConfig.get().hideTitles);
		hideable(VanillaHudElements.HELD_ITEM_TOOLTIP, () -> SwiftConfig.get().hideHeldItemName);

		// SwiftSMP always first in Multiplayer: on start-up and whenever that screen opens.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(ServerPin::pin);
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
			if (screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen) ServerPin.pin(client);
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (menuKey.consumeClick()) {
				if (client.gui.screen() == null) client.gui.setScreen(new SwiftMenuScreen());
			}
			while (hudKey.consumeClick()) {
				SwiftConfig.get().hudVisible = !SwiftConfig.get().hudVisible;
				SwiftConfig.save();
			}
			Tweaks.tick(client);
			debugTick(client);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
			reportPresence(client);
			if (SwiftConfig.get().brightMode) Tweaks.applyBrightness();
			debugTicks = 0;
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			CapeManager.clear();
			presence("menu", null);
		});
		presence("menu", null);
	}

	private static void hideable(Identifier id, java.util.function.BooleanSupplier hidden) {
		HudElementRegistry.replaceElement(id, original -> (g, delta) -> {
			if (!hidden.getAsBoolean()) original.extractRenderState(g, delta);
		});
	}

	private static int debugTicks = -1;

	/** Dev/test hooks: -Dswiftclient.debug=cape shows your own cape after 2 s, "menu" opens the menu after 10 s. */
	private static void debugTick(Minecraft client) {
		String debug = System.getProperty("swiftclient.debug", "");
		if (debug.isEmpty() || debugTicks < 0 || client.player == null) return;
		debugTicks++;
		if (debugTicks == 40 && debug.contains("cape")) client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		if (debugTicks == 200 && debug.contains("menu")) client.gui.setScreen(new SwiftMenuScreen());
		if (debugTicks == 300 && debug.contains("menu")) client.gui.setScreen(new SwiftMenuScreen("visibility"));
		if (debugTicks == 400 && debug.contains("editor")) client.gui.setScreen(new net.swiftsmp.swiftclient.ui.HudEditorScreen(null));
	}

	public static KeyMapping zoomKey() {
		return zoomKey;
	}

	public static String zoomKeyName() {
		return KeyMappingHelper.getBoundKeyOf(zoomKey).getDisplayName().getString();
	}

	public static boolean isMenuKey(int key) {
		return KeyMappingHelper.getBoundKeyOf(menuKey).getValue() == key;
	}

	private static void reportPresence(Minecraft client) {
		if (client.hasSingleplayerServer()) {
			presence("singleplayer", null);
			return;
		}
		ServerData server = client.getCurrentServer();
		presence("multiplayer", server == null ? null : server.ip);
	}

	private static void presence(String state, String server) {
		JsonObject json = new JsonObject();
		json.addProperty("state", state);
		if (server != null) json.addProperty("server", server);
		// stdout, not the logger: the launcher reads the raw process output.
		System.out.println(PRESENCE_MARKER + json);
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}
}
