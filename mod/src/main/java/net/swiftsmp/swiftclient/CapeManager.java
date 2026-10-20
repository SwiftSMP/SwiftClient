package net.swiftsmp.swiftclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * Looks up each player's SwiftClient cape on the cape server and registers it as a texture.
 * Lookups are asynchronous and cached; until one completes the player keeps their normal cape.
 */
public final class CapeManager {
	private static final long PLAYER_TTL_MS = 5 * 60_000;
	private static final long RETRY_MS = 60_000;

	private static String baseUrl = "";
	private static HttpClient http;
	private static ExecutorService pool;

	/** uuid -> lookup result; texture == null means "no SwiftClient cape". */
	private static final Map<UUID, Entry> players = new ConcurrentHashMap<>();
	/** cape id -> registered texture asset (shared by all players wearing it). */
	private static final Map<String, ClientAsset.Texture> textures = new ConcurrentHashMap<>();
	private static final Map<String, Boolean> loadingCapes = new ConcurrentHashMap<>();

	private record Entry(String capeId, long fetchedAt, boolean pending) {}

	private CapeManager() {}

	static void init(String url) {
		baseUrl = url == null ? "" : url.replaceAll("/+$", "");
		if (baseUrl.isEmpty()) return;
		http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
		pool = Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "SwiftClient-Capes");
			t.setDaemon(true);
			return t;
		});
	}

	/** Drop player lookups (on disconnect) so selections made meanwhile show up on rejoin. */
	static void clear() {
		players.clear();
	}

	/** Returns the cape to render for this player, or null to keep the vanilla one. Never blocks. */
	public static ClientAsset.Texture capeFor(UUID uuid) {
		if (baseUrl.isEmpty() || uuid == null) return null;
		long now = System.currentTimeMillis();
		Entry e = players.get(uuid);
		if (e == null || (!e.pending() && now - e.fetchedAt() > PLAYER_TTL_MS)) {
			players.put(uuid, new Entry(e == null ? null : e.capeId(), now, true));
			pool.execute(() -> lookup(uuid));
		}
		if (e == null || e.capeId() == null) return null;
		ClientAsset.Texture tex = textures.get(e.capeId());
		if (tex == null) loadCape(e.capeId());
		return tex;
	}

	private static void lookup(UUID uuid) {
		String id = uuid.toString().replace("-", "");
		String capeId = null;
		long fetchedAt = System.currentTimeMillis();
		try {
			HttpResponse<String> res = http.send(request("/players/" + id + ".json"), HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() == 200) {
				JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
				if (json.has("cape")) capeId = json.get("cape").getAsString();
			} else if (res.statusCode() != 404) {
				fetchedAt -= PLAYER_TTL_MS - RETRY_MS;
			}
		} catch (Exception ex) {
			// Server unreachable: try again in a minute instead of every frame.
			fetchedAt -= PLAYER_TTL_MS - RETRY_MS;
		}
		if (capeId != null && !capeId.matches("[a-z0-9_-]+")) capeId = null;
		players.put(uuid, new Entry(capeId, fetchedAt, false));
		if (capeId != null) loadCape(capeId);
	}

	private static void loadCape(String capeId) {
		if (textures.containsKey(capeId) || loadingCapes.putIfAbsent(capeId, true) != null) return;
		pool.execute(() -> {
			try {
				HttpResponse<byte[]> res = http.send(request("/capes/" + capeId + ".png"), HttpResponse.BodyHandlers.ofByteArray());
				if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
				NativeImage image = NativeImage.read(res.body());
				Minecraft.getInstance().execute(() -> {
					Identifier id = Identifier.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "capes/" + capeId);
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "SwiftClient cape " + capeId, image));
					textures.put(capeId, new ClientAsset.ResourceTexture(id, id));
					loadingCapes.remove(capeId);
				});
			} catch (Exception ex) {
				SwiftClientMod.LOG.warn("Could not load cape {}: {}", capeId, ex.toString());
				// Allow a retry later.
				new Thread(() -> {
					try { Thread.sleep(RETRY_MS); } catch (InterruptedException ignored) { }
					loadingCapes.remove(capeId);
				}, "SwiftClient-CapeRetry").start();
			}
		});
	}

	private static HttpRequest request(String path) {
		return HttpRequest.newBuilder(URI.create(baseUrl + path))
			.timeout(Duration.ofSeconds(8))
			.header("User-Agent", "SwiftClient/" + SwiftClientMod.version())
			.GET().build();
	}
}
