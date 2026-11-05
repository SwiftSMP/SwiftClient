package net.swiftsmp.swiftclient.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.swiftsmp.swiftclient.SwiftClientMod;

/**
 * LRCLIB client with a disk cache. LRCLIB is a free, keyless lyrics service; only the track
 * title, artist, album and duration are sent, and only while the Lyrics HUD is enabled.
 */
public final class LyricsClient {
	public enum Status { READY, INSTRUMENTAL, NONE, ERROR }

	public static final class Result {
		public static final Result MISS = new Result(SyncedLyrics.EMPTY, Status.NONE, "");
		public final SyncedLyrics lyrics;
		public final Status status;
		public final String error;

		Result(SyncedLyrics lyrics, Status status, String error) {
			this.lyrics = lyrics;
			this.status = status;
			this.error = error;
		}

		static Result of(SyncedLyrics lyrics, Status status) {
			return new Result(lyrics, status, "");
		}

		static Result error(String message) {
			return new Result(null, Status.ERROR, message == null ? "" : message);
		}
	}

	private static final String BASE = "https://lrclib.net/api";
	private static final String USER_AGENT = "SwiftClient/1.5 (+https://swiftclient.swiftsmp.net)";
	private static final long HIT_TTL_MS = 30L * 24 * 3600 * 1000;
	private static final long MISS_TTL_MS = 6L * 3600 * 1000;
	private static final long MIN_REQUEST_GAP_MS = 750;

	private final Path cacheDir;
	private final ExecutorService executor;
	private final Map<String, Result> memory = new ConcurrentHashMap<>();
	private final Set<String> inflight = ConcurrentHashMap.newKeySet();
	private volatile long nextAllowedRequestMs;
	private volatile boolean closed;

	public LyricsClient(Path dir) {
		cacheDir = dir.resolve("lyrics-cache");
		try {
			Files.createDirectories(cacheDir);
		} catch (IOException e) {
			SwiftClientMod.LOG.warn("Could not create lyrics cache: {}", e.toString());
		}
		executor = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "SwiftClient-Lyrics");
			thread.setDaemon(true);
			return thread;
		});
	}

	public void fetch(NowPlaying nowPlaying, Consumer<Result> callback) {
		if (closed || !nowPlaying.hasTrack()) return;
		String cacheKey = cacheKey(nowPlaying);
		Result cached = memory.get(cacheKey);
		if (cached != null) {
			callback.accept(cached);
			return;
		}
		Result disk = readCache(cacheKey);
		if (disk != null) {
			memory.put(cacheKey, disk);
			callback.accept(disk);
			return;
		}
		if (!inflight.add(cacheKey)) return;
		executor.execute(() -> {
			Download download = null;
			Result result;
			try {
				download = download(nowPlaying);
				result = download.result;
				if (result.status != Status.ERROR) writeCache(cacheKey, download);
			} catch (Exception e) {
				result = Result.error(e.getMessage());
			} finally {
				inflight.remove(cacheKey);
			}
			memory.put(cacheKey, result);
			if (!closed) callback.accept(result);
		});
	}

	private Download download(NowPlaying nowPlaying) throws IOException {
		long now = System.currentTimeMillis();
		if (now < nextAllowedRequestMs) {
			return new Download(Result.error("Waiting for LRCLIB"), null);
		}
		nextAllowedRequestMs = now + MIN_REQUEST_GAP_MS;

		String exact = BASE + "/get?" + query(
			"artist_name", nowPlaying.artist,
			"track_name", nowPlaying.title,
			"album_name", nowPlaying.album,
			"duration", nowPlaying.durationMs > 0 ? String.valueOf(Math.round(nowPlaying.durationMs / 1000.0)) : ""
		);
		Response response = get(exact);
		if (response.code == 200) {
			JsonObject item = JsonParser.parseString(response.body).getAsJsonObject();
			return new Download(parse(item), item);
		}
		if (response.code == 429) {
			nextAllowedRequestMs = now + 60_000;
			return new Download(Result.error("LRCLIB rate limit"), null);
		}
		if (response.code != 404 && response.code != 400) {
			return new Download(Result.error("LRCLIB returned " + response.code), null);
		}

		// Exact lookup is strict about album and duration; search is the forgiving fallback.
		JsonObject best = search(nowPlaying);
		if (best == null) return new Download(Result.MISS, null);
		return new Download(parse(best), best);
	}

	private JsonObject search(NowPlaying nowPlaying) throws IOException {
		String url = BASE + "/search?" + query(
			"track_name", nowPlaying.title,
			"artist_name", nowPlaying.artist
		);
		Response response = get(url);
		if (response.code == 200) {
			JsonObject best = choose(response.body, nowPlaying);
			if (best != null) return best;
		}

		String q = (nowPlaying.artist + " " + nowPlaying.title).trim();
		if (q.isEmpty()) return null;
		url = BASE + "/search?" + query("q", q);
		response = get(url);
		return response.code == 200 ? choose(response.body, nowPlaying) : null;
	}

	private static JsonObject choose(String body, NowPlaying nowPlaying) {
		JsonElement parsed = JsonParser.parseString(body);
		if (!parsed.isJsonArray()) return null;
		JsonArray array = parsed.getAsJsonArray();
		String title = normalize(nowPlaying.title);
		String artist = normalize(nowPlaying.artist);
		long duration = nowPlaying.durationMs;
		JsonObject best = null;
		double bestScore = 0;
		for (JsonElement element : array) {
			if (!element.isJsonObject()) continue;
			JsonObject item = element.getAsJsonObject();
			String itemTitle = normalize(string(item, "trackName"));
			String itemArtist = normalize(string(item, "artistName"));
			boolean hasSynced = !string(item, "syncedLyrics").isBlank();
			boolean instrumental = bool(item, "instrumental");
			if (!hasSynced && !instrumental && string(item, "plainLyrics").isBlank()) continue;

			double score = 0;
			if (title.equals(itemTitle)) score += 0.55;
			else if (!title.isEmpty() && (itemTitle.contains(title) || title.contains(itemTitle))) score += 0.32;
			if (artist.equals(itemArtist)) score += 0.30;
			else if (!artist.isEmpty() && (itemArtist.contains(artist) || artist.contains(itemArtist))) score += 0.16;
			long itemDuration = Math.round(doubleValue(item, "duration") * 1000);
			if (duration > 0 && itemDuration > 0) {
				long diff = Math.abs(duration - itemDuration);
				if (diff <= 2000) score += 0.15;
				else if (diff <= 8000) score += 0.06;
			}
			if (hasSynced) score += 0.10;
			if (score > bestScore) {
				bestScore = score;
				best = item;
			}
		}
		return bestScore >= 0.35 ? best : null;
	}

	private static Result parse(JsonObject item) {
		if (item == null) return Result.MISS;
		boolean instrumental = bool(item, "instrumental");
		String synced = string(item, "syncedLyrics");
		String plain = string(item, "plainLyrics");
		SyncedLyrics lyrics = SyncedLyrics.parse(synced, instrumental, plain);
		if (instrumental) return Result.of(lyrics, Status.INSTRUMENTAL);
		if (lyrics.hasLines()) return Result.of(lyrics, Status.READY);
		return Result.of(lyrics, Status.NONE);
	}

	private static final class Download {
		final Result result;
		final JsonObject item;

		Download(Result result, JsonObject item) {
			this.result = result;
			this.item = item;
		}
	}

	private static final class Response {
		final int code;
		final String body;

		Response(int code, String body) {
			this.code = code;
			this.body = body;
		}
	}

	private static Response get(String url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setConnectTimeout(6000);
		connection.setReadTimeout(8000);
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setRequestProperty("Accept", "application/json");
		try {
			int code = connection.getResponseCode();
			InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
			StringBuilder body = new StringBuilder();
			if (stream != null) {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
					char[] buffer = new char[4096];
					int read;
					while ((read = reader.read(buffer)) >= 0) body.append(buffer, 0, read);
				}
			}
			return new Response(code, body.toString());
		} finally {
			connection.disconnect();
		}
	}

	private static String query(String... pairs) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			String value = pairs[i + 1];
			if (value == null || value.isBlank()) continue;
			if (out.length() > 0) out.append('&');
			out.append(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8));
			out.append('=');
			out.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
		}
		return out.toString();
	}

	private Result readCache(String key) {
		Path file = cacheDir.resolve(key + ".json");
		try {
			if (!Files.isRegularFile(file)) return null;
			long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
			JsonObject wrapper = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			boolean hit = bool(wrapper, "hit");
			if (age > (hit ? HIT_TTL_MS : MISS_TTL_MS)) return null;
			if (!hit) return Result.MISS;
			JsonElement data = wrapper.get("data");
			if (data == null || !data.isJsonObject()) return null;
			return parse(data.getAsJsonObject());
		} catch (Exception ignored) {
			return null;
		}
	}

	private void writeCache(String key, Download download) {
		JsonObject wrapper = new JsonObject();
		wrapper.addProperty("at", System.currentTimeMillis());
		wrapper.addProperty("hit", download.item != null);
		if (download.item != null) wrapper.add("data", download.item);
		Path file = cacheDir.resolve(key + ".json");
		Path tmp = cacheDir.resolve(key + ".json.tmp");
		try {
			Files.writeString(tmp, wrapper.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			SwiftClientMod.LOG.warn("Could not cache lyrics: {}", e.toString());
		}
	}

	private static String cacheKey(NowPlaying nowPlaying) {
		String value = normalize(nowPlaying.artist) + '|' + normalize(nowPlaying.title) + '|'
			+ normalize(nowPlaying.album) + '|' + Math.round(nowPlaying.durationMs / 1000.0);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) hex.append(String.format("%02x", b));
			return hex.toString();
		} catch (Exception e) {
			return Integer.toHexString(value.hashCode());
		}
	}

	private static String normalize(String value) {
		if (value == null) return "";
		String text = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		StringBuilder out = new StringBuilder(text.length());
		boolean space = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				out.append(Character.toLowerCase(c));
				space = false;
			} else if (!space && out.length() > 0) {
				out.append(' ');
				space = true;
			}
		}
		return out.toString().trim().toLowerCase(Locale.ROOT);
	}

	private static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static boolean bool(JsonObject object, String key) {
		JsonElement element = object.get(key);
		try {
			return element != null && !element.isJsonNull() && element.getAsBoolean();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static double doubleValue(JsonObject object, String key) {
		JsonElement element = object.get(key);
		try {
			return element == null || element.isJsonNull() ? 0 : element.getAsDouble();
		} catch (RuntimeException ignored) {
			return 0;
		}
	}

	public void close() {
		closed = true;
		executor.shutdownNow();
	}
}
