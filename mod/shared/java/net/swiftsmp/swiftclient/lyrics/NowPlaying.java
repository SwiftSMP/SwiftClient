package net.swiftsmp.swiftclient.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

/** One immutable snapshot of the track the system media session is playing. */
public final class NowPlaying {
	public static final NowPlaying NONE = new NowPlaying(false, "", "", "", "", false, "Stopped", 0, 0, 0, 0, 0L);

	public final boolean active;
	public final String title;
	public final String artist;
	public final String album;
	public final String app;
	public final boolean playing;
	public final String status;
	public final long positionMs;
	public final long durationMs;
	/** 0 means no album-art colour was available. */
	public final int accent;
	/** Low-frequency audio level 0-1 from the bridge's WASAPI capture; 0 when capture is unavailable. */
	public final double bass;
	/** Local clock time when this snapshot was read, used to advance the position between updates. */
	public final long receivedAtMs;

	public NowPlaying(boolean active, String title, String artist, String album, String app, boolean playing,
			String status, long positionMs, long durationMs, int accent, double bass, long receivedAtMs) {
		this.active = active;
		this.title = clean(title);
		this.artist = cleanArtist(clean(artist), this.title);
		this.album = clean(album);
		this.app = cleanApp(clean(app));
		this.playing = playing;
		this.status = clean(status);
		this.positionMs = Math.max(0, positionMs);
		this.durationMs = Math.max(0, durationMs);
		this.accent = accent & 0xFFFFFF;
		this.bass = Math.max(0.0, Math.min(1.0, bass));
		this.receivedAtMs = receivedAtMs;
	}

	public boolean hasTrack() {
		return active && !title.isEmpty();
	}

	/** Changes only when the song changes; pause/resume does not reset the lyrics. */
	public String key() {
		if (!hasTrack()) return "";
		return (title + '\u0000' + artist + '\u0000' + album).toLowerCase(Locale.ROOT);
	}

	public long positionAt(long nowMs) {
		long pos = positionMs;
		if (playing && nowMs > receivedAtMs) pos += nowMs - receivedAtMs;
		if (durationMs > 0 && pos > durationMs) pos = durationMs;
		return Math.max(0, pos);
	}

	public static NowPlaying fromJson(JsonObject o, long receivedAtMs) {
		if (o == null) return NONE;
		// Prefer the writer's own timestamp so time spent in the file/read path is not counted
		// twice; fall back to the read time for older writers that do not send one.
		long stamp = longValue(o, "generatedAt");
		if (stamp > 0 && stamp <= receivedAtMs && receivedAtMs - stamp < 5000) receivedAtMs = stamp;
		boolean active = bool(o, "active");
		if (!active) return new NowPlaying(false, "", "", "", "", false, str(o, "status"), 0, 0, 0, 0, receivedAtMs);
		return new NowPlaying(
			true,
			str(o, "title"),
			str(o, "artist"),
			str(o, "album"),
			str(o, "app"),
			bool(o, "playing"),
			str(o, "status"),
			longValue(o, "positionMs"),
			longValue(o, "durationMs"),
			parseColor(str(o, "accent")),
			doubleValue(o, "bass"),
			receivedAtMs
		);
	}

	private static double doubleValue(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e == null || e.isJsonNull() ? 0.0 : e.getAsDouble();
		} catch (RuntimeException ignored) {
			return 0.0;
		}
	}

	public static int parseColor(String hex) {
		if (hex == null) return 0;
		String s = hex.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.length() != 6) return 0;
		try {
			return Integer.parseInt(s, 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String cleanApp(String app) {
		String lower = app.toLowerCase(Locale.ROOT);
		if (lower.contains("applemusic")) return "Apple Music";
		if (lower.contains("spotify")) return "Spotify";
		if (lower.contains("chrome")) return "Chrome";
		if (lower.contains("firefox")) return "Firefox";
		if (lower.contains("msedge") || lower.contains("edge")) return "Edge";
		if (lower.contains("brave")) return "Brave";
		if (lower.contains("youtubemusic")) return "YouTube Music";
		return app.length() > 28 ? app.substring(0, 25) + "..." : app;
	}

	/** Apple Music reports artist as "Artist - Song - Single"; strip that so LRCLIB can match. */
	private static String cleanArtist(String artist, String title) {
		if (artist == null || artist.isEmpty()) return "";
		String clean = artist.replaceAll("(?i)\\s+[-\\u2013\\u2014]\\s+(Single|EP|Album|Deluxe|Remaster(ed)?|Explicit)\\s*$", "").trim();
		if (!title.isEmpty()) {
			clean = clean.replaceAll("(?i)\\s+[-\\u2013\\u2014]\\s+" + java.util.regex.Pattern.quote(title) + "\\s*$", "").trim();
		}
		return clean;
	}

	private static String clean(String s) {
		if (s == null) return "";
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\r' || c == '\n' || c == '\t') out.append(' ');
			else if (c >= 32) out.append(c);
		}
		return out.toString().replaceAll(" {2,}", " ").trim();
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static boolean bool(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e != null && !e.isJsonNull() && e.getAsBoolean();
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private static long longValue(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e == null || e.isJsonNull() ? 0L : e.getAsLong();
		} catch (RuntimeException ignored) {
			return 0L;
		}
	}
}
