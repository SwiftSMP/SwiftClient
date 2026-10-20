package net.swiftsmp.swiftclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.swiftsmp.swiftclient.SwiftClientMod;

/** config/swiftclient.json: HUD layout and client tweaks. */
public final class SwiftConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("swiftclient.json");
	private static SwiftConfig instance;

	public static final class Module {
		public boolean enabled;
		/** Top-left position as a fraction of the screen, so layouts survive resolution changes. */
		public float x;
		public float y;
		public float scale = 1f;

		public Module() {}

		public Module(boolean enabled, float x, float y) {
			this.enabled = enabled;
			this.x = x;
			this.y = y;
		}
	}

	public Map<String, Module> modules = new LinkedHashMap<>();
	public boolean hudBackground = true;
	/** Master switch for every SwiftClient HUD module (F6). */
	public boolean hudVisible = true;
	public boolean hideScoreboard = false;
	public boolean hideBossBar = false;
	public boolean hideEffects = false;
	public boolean hideTitles = false;
	public boolean hideHeldItemName = false;
	public boolean autoSprint = false;
	public boolean brightMode = false;
	public double savedGamma = 0.5;
	public boolean smoothZoom = true;
	public float zoomFactor = 4f;
	public boolean showOwnCape = true;
	/** Turn on the SwiftSMP badge automatically on SwiftSMP. */
	public boolean swiftsmpHud = true;

	public static SwiftConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				instance = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), SwiftConfig.class);
			}
		} catch (Exception e) {
			SwiftClientMod.LOG.warn("Could not read {}, using defaults: {}", FILE, e.toString());
		}
		if (instance == null) instance = new SwiftConfig();
		if (instance.modules == null) instance.modules = new LinkedHashMap<>();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(get()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SwiftClientMod.LOG.warn("Could not save {}: {}", FILE, e.toString());
		}
	}

	public Module module(String id, boolean enabled, float x, float y) {
		return modules.computeIfAbsent(id, k -> new Module(enabled, x, y));
	}
}
