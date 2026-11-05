package net.swiftsmp.swiftclient.lyrics;

import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.swiftsmp.swiftclient.config.SwiftConfig;

/** Tracks the active lyric line and word, and hands them to the platform's sky renderer. */
public final class WorldLyrics {
	/** One frame of the sky lyrics: the line to draw and which word is being sung. */
	public static final class Frame {
		public final String header;
		public final List<String> words;
		public final int active;
		public final boolean singleWord;
		/** Start time of each word (already lead-adjusted); empty for single-word or untimed lines. */
		public final long[] wordStarts;
		/** Lead-adjusted sung window for the whole line, for the smooth per-letter fill. */
		public final long lineStartMs;
		public final long lineEndMs;

		Frame(String header, List<String> words, int active, boolean singleWord, long[] wordStarts,
				long lineStartMs, long lineEndMs) {
			this.header = header;
			this.words = words;
			this.active = active;
			this.singleWord = singleWord;
			this.wordStarts = wordStarts == null ? new long[0] : wordStarts;
			this.lineStartMs = lineStartMs;
			this.lineEndMs = lineEndMs;
		}

		/** Index of the word being sung at a position, or the last word before the first start. */
		public int activeAt(long positionMs) {
			if (wordStarts.length == 0) return active;
			int best = 0;
			for (int i = 0; i < wordStarts.length; i++) {
				if (positionMs >= wordStarts[i]) best = i;
				else break;
			}
			return best;
		}

		/** What is on screen; changes when the line or the active word changes. */
		public String key() {
			return String.join(" ", words) + "#" + active;
		}
	}

	public interface Display {
		void show(Frame frame, double x, double y, double z);

		void move(double x, double y, double z);

		void hide();
	}

	private static WorldLyrics instance;

	private final Display display;
	private boolean visible;
	private Object lastLevel;
	/** Consecutive ticks with no track; tolerates brief stale reads before hiding the sky text. */
	private int missingTicks;
	private static final int MISSING_GRACE_TICKS = 24;

	private WorldLyrics(Display display) {
		this.display = display;
	}

	public static synchronized void init(Display display) {
		if (instance == null) instance = new WorldLyrics(display);
	}

	public static WorldLyrics get() {
		return instance;
	}

	public void tick() {
		if (display == null) return;
		Minecraft mc = Minecraft.getInstance();
		SwiftConfig config = SwiftConfig.get();
		LyricsService.State state = LyricsService.get().state();
		if (!config.lyricsEnabled || mc.player == null || mc.level == null) {
			hide();
			return;
		}
		// Don't hide on level change during movement - just update the reference
		if (mc.level != lastLevel) {
			lastLevel = mc.level;
		}
		// A single tick without a track is usually a stale-read race in the bridge, not a real
		// stop; keep the last line on screen until it has been gone for a moment.
		if (!state.now.hasTrack()) {
			if (++missingTicks > MISSING_GRACE_TICKS) hide();
			return;
		}
		missingTicks = 0;

		// The line and the sung word are chosen together here at tick rate; the line rebuild bakes
		// glyphs, which must happen outside the render pass, so they stay in sync (no word/line drift).
		Frame frame = frame(state, config);
		if (frame == null || frame.words.isEmpty()) {
			// Keep showing last frame during brief gaps instead of hiding
			return;
		}
		display.show(frame, 0.0, 0.0, 0.0);
		visible = true;
	}

	private static Frame frame(LyricsService.State state, SwiftConfig config) {
		String header = header(state.now);
		if (state.lyrics != null && state.lyrics.hasLines()) {
			long position = Math.max(0, state.positionMs() - config.lyricsOffsetMs);
			int first = state.lyrics.nearestTextIndex(0);
			if (first >= 0 && position < state.lyrics.line(first).timeMs && !header.isEmpty()) {
				// Intro: before the first sung line, show a title/artist card (no word highlight).
				return new Frame(header, java.util.Arrays.asList(header.split("\\s+")), -1, false, null, 0, 0);
			}
			int index = state.lyrics.indexAt(position);
			List<SyncedLyrics.Word> spans = state.lyrics.wordsAt(index);
			if (spans.isEmpty()) {
				// Instrumental gaps are blank LRC lines: keep the last line that has words.
				int text = state.lyrics.nearestTextIndex(index);
				if (text >= 0 && text != index) {
					index = text;
					spans = state.lyrics.wordsAt(text);
				}
			}
			if (!spans.isEmpty()) {
				int active = Math.max(0, state.lyrics.activeWordAt(index, position));
				long[] starts = new long[spans.size()];
				for (int i = 0; i < spans.size(); i++) starts[i] = spans.get(i).startMs;
				if (config.lyricsWordByWord) {
					return new Frame(header, List.of(spans.get(active).text), 0, true, null, 0, 0);
				}
				SyncedLyrics.Line ln = state.lyrics.line(index);
				List<String> words = new java.util.ArrayList<>(spans.size());
				for (SyncedLyrics.Word word : spans) words.add(word.text);
				return new Frame(header, Collections.unmodifiableList(words), active, false, starts,
					ln.windowStartMs, ln.windowEndMs);
			}
		}
		// While the LRCLIB fetch is still in flight, show the title/artist card instead of the
		// "Finding lyrics..." placeholder; the lyrics are cached to disk after, so replays are instant.
		if (state.status == LyricsService.Status.LOADING && !header.isEmpty()) {
			return new Frame(header, java.util.Arrays.asList(header.split("\\s+")), -1, false, null, 0, 0);
		}
		String status = statusText(state);
		if (status.isEmpty()) return null;
		return new Frame(header, List.of(status), 0, true, null, 0, 0);
	}

	private void hide() {
		if (visible) display.hide();
		visible = false;
		missingTicks = 0;
	}

	private static String statusText(LyricsService.State state) {
		return switch (state.status) {
			case LOADING -> "Finding lyrics...";
			case INSTRUMENTAL -> "Instrumental";
			case ERROR -> "Lyrics offline, retrying";
			case NONE -> "No synced lyrics";
			default -> "";
		};
	}

	private static String header(NowPlaying now) {
		String header = now.title;
		if (!now.artist.isEmpty()) header = header + "  -  " + now.artist;
		return header.length() > 90 ? header.substring(0, 87) + "..." : header;
	}
}
