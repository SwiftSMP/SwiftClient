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

/** config/swiftclient.json: HUD layout and client tweaks. Same file the 26.x build uses. */
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
	/** SwiftBoost: adaptive render distance, particles and entity distance for a higher frame rate. */
	public boolean swiftBoost = false;
	public int boostTarget = 60;
	public int boostSavedDistance = -1;
	public int boostSavedParticles = -1;
	public double boostSavedEntityDistance = -1;
	public int boostSavedClouds = -1;
	public int boostSavedShadows = -1;
	public int boostSavedAo = -1;
	public int boostSavedFpsLimit = -1;
	/** In-world lyrics, shown as 3D billboarded text instead of a screen HUD. */
	public boolean lyricsEnabled = false;
	/** Positive values show lyrics later; negative values show them earlier. */
	public int lyricsOffsetMs = -1500;
	public float lyricsScale = 1f;
	/** Height of the sky word above the player's eyes. */
	public float lyricsSkyHeight = 20f;
	/** Reveal one word at a time instead of the whole karaoke line. */
	public boolean lyricsWordByWord = false;
	/** Pump the screen and lyrics to the music's bass (or the song's rhythm where capture is blocked). */
	public boolean bassMode = false;
	/** Dynamic-Island-style now-playing pill at the top-centre of the screen. */
	public boolean nowPlayingIsland = true;

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
		if (instance.boostTarget < 30 || instance.boostTarget > 240) instance.boostTarget = 60;
		if (instance.lyricsOffsetMs < -10000 || instance.lyricsOffsetMs > 10000) instance.lyricsOffsetMs = 0;
		if (instance.lyricsScale < 0.5f || instance.lyricsScale > 2f) instance.lyricsScale = 1f;
		if (instance.lyricsSkyHeight < 0f || instance.lyricsSkyHeight > 40f) instance.lyricsSkyHeight = 20f;
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
