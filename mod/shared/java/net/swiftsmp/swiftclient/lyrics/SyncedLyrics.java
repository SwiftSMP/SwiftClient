package net.swiftsmp.swiftclient.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed LRC lyrics: sorted timestamped lines with word spans, and LRCLIB's offset tag applied. */
public final class SyncedLyrics {
	private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
	private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");
	private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

	/** A word lights up slightly before its stamped time so it lands with the voice. */
	private static final long WORD_LEAD_MS = 400;
	private static final long DEFAULT_LINE_MS = 4000;
	private static final long MIN_LINE_MS = 1000;
	private static final long MAX_LINE_MS = 12000;

	/** One word of a line and the window it is sung in. */
	public static final class Word {
		public final String text;
		public final long startMs;
		public final long endMs;

		Word(String text, long startMs, long endMs) {
			this.text = text;
			this.startMs = startMs;
			this.endMs = endMs;
		}
	}

	public static final class Line {
		public final long timeMs;
		public final String text;
		/** Word timings from enhanced LRC (`<mm:ss.xx>` tags); empty when the file has none. */
		private final List<Word> words;
		/** Resolved windows for every word, filled once the next line's time is known. */
		private List<Word> spans = Collections.emptyList();
		/** Lead-adjusted sung window for the whole line; used for the smooth per-letter fill. */
		public long windowStartMs;
		public long windowEndMs;

		Line(long timeMs, String text, List<Word> words) {
			this.timeMs = timeMs;
			this.text = text;
			this.words = words == null ? Collections.emptyList() : words;
		}
	}

	public static final SyncedLyrics EMPTY = new SyncedLyrics(Collections.emptyList(), false, "");

	private final List<Line> lines;
	private final boolean instrumental;
	private final String plain;

	private SyncedLyrics(List<Line> lines, boolean instrumental, String plain) {
		this.lines = lines;
		this.instrumental = instrumental;
		this.plain = plain;
		for (int i = 0; i < lines.size(); i++) resolveSpans(i);
	}

	public static SyncedLyrics parse(String lrc, boolean instrumental, String plainLyrics) {
		String plain = plainLyrics == null ? "" : plainLyrics;
		if (lrc == null || lrc.isBlank()) return new SyncedLyrics(Collections.emptyList(), instrumental, plain);

		int offset = 0;
		Matcher offsetMatcher = OFFSET.matcher(lrc);
		if (offsetMatcher.find()) {
			try {
				offset = Integer.parseInt(offsetMatcher.group(1));
			} catch (NumberFormatException ignored) {
				offset = 0;
			}
		}

		// TreeMap keeps the lines sorted and lets a blank line at the same timestamp clear a line.
		TreeMap<Long, Line> sorted = new TreeMap<>();
		for (String raw : lrc.split("\\R")) {
			String withoutWords = WORD_TIME.matcher(raw).replaceAll("");
			Matcher matcher = TIME.matcher(withoutWords);
			List<Long> times = new ArrayList<>(2);
			while (matcher.find()) times.add(time(matcher, offset));
			if (times.isEmpty()) continue;
			String text = TIME.matcher(withoutWords).replaceAll("").trim();
			List<Word> words = parseWords(raw, offset);
			for (long time : times) sorted.put(time, new Line(time, text, words));
		}

		return new SyncedLyrics(new ArrayList<>(sorted.values()), instrumental, plain);
	}

	public SyncedLyrics withOffset(int offsetMs) {
		if (offsetMs == 0 || lines.isEmpty()) return this;
		List<Line> shifted = new ArrayList<>(lines.size());
		for (Line line : lines) {
			List<Word> words = new ArrayList<>(line.words.size());
			for (Word word : line.words) words.add(new Word(word.text, Math.max(0, word.startMs + offsetMs), 0));
			shifted.add(new Line(Math.max(0, line.timeMs + offsetMs), line.text, words));
		}
		return new SyncedLyrics(shifted, instrumental, plain);
	}

	public boolean instrumental() {
		return instrumental;
	}

	public String plain() {
		return plain;
	}

	public int size() {
		return lines.size();
	}

	public boolean hasLines() {
		for (Line line : lines) if (!line.text.isEmpty()) return true;
		return false;
	}

	public Line line(int index) {
		return index >= 0 && index < lines.size() ? lines.get(index) : null;
	}

	/** Every word of a line with the window it is sung in; empty for blank or unknown lines. */
	public List<Word> wordsAt(int index) {
		if (index < 0 || index >= lines.size()) return Collections.emptyList();
		return lines.get(index).spans;
	}

	/** Index of the word being sung at a position, or -1 before the line's first word. */
	public int activeWordAt(int index, long positionMs) {
		List<Word> spans = wordsAt(index);
		int active = -1;
		for (int i = 0; i < spans.size(); i++) {
			if (positionMs >= spans.get(i).startMs) active = i;
			else break;
		}
		return active;
	}

	/**
	 * The line to keep on screen. Instrumental gaps are stored as empty LRC lines, so during
	 * a gap this keeps the last non-empty line instead of flashing the text off.
	 */
	public String displayTextAt(int index) {
		int at = nearestTextIndex(index);
		return at < 0 ? "" : lines.get(at).text;
	}

	/**
	 * The word active at a position. Enhanced LRC word stamps are used when present; otherwise
	 * the line duration is split across its words by length. Gaps hold the last word.
	 */
	public String displayWordAt(int index, long positionMs) {
		int at = nearestTextIndex(index);
		if (at < 0) return "";
		List<Word> spans = lines.get(at).spans;
		if (spans.isEmpty()) return lines.get(at).text;
		int active = activeWordAt(at, positionMs);
		return spans.get(Math.max(0, Math.min(spans.size() - 1, active))).text;
	}

	/** Index of the last line whose timestamp has passed; 0 before the first line. */
	public int indexAt(long positionMs) {
		if (lines.isEmpty()) return -1;
		int lo = 0, hi = lines.size() - 1, best = 0;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs <= positionMs) {
				best = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return best;
	}

	/** Nearest line with text: the current one, else the one before it, else the one after. */
	public int nearestTextIndex(int index) {
		if (lines.isEmpty()) return -1;
		int at = Math.max(0, Math.min(lines.size() - 1, index));
		for (int i = at; i >= 0; i--) {
			if (!lines.get(i).text.isEmpty()) return i;
		}
		for (int i = at + 1; i < lines.size(); i++) {
			if (!lines.get(i).text.isEmpty()) return i;
		}
		return -1;
	}

	/** Word windows for one line, from real word stamps when the file has them. */
	private void resolveSpans(int index) {
		Line line = lines.get(index);
		if (line.text.isEmpty() && line.words.isEmpty()) return;

		long nextStart = Long.MAX_VALUE;
		for (int i = index + 1; i < lines.size(); i++) {
			if (lines.get(i).timeMs > line.timeMs) {
				nextStart = lines.get(i).timeMs;
				break;
			}
		}
		long duration = nextStart == Long.MAX_VALUE
			? DEFAULT_LINE_MS
			: Math.max(MIN_LINE_MS, Math.min(MAX_LINE_MS, nextStart - line.timeMs));
		long start = Math.max(0, line.timeMs - WORD_LEAD_MS);
		line.windowStartMs = start;
		line.windowEndMs = start + duration;

		if (!line.words.isEmpty()) {
			List<Word> spans = new ArrayList<>(line.words.size());
			for (int i = 0; i < line.words.size(); i++) {
				Word word = line.words.get(i);
				long wordStart = Math.max(start, word.startMs);
				long end = i + 1 < line.words.size()
					? Math.max(wordStart, line.words.get(i + 1).startMs)
					: start + duration;
				if (end <= wordStart) end = wordStart + 200;
				spans.add(new Word(word.text, wordStart, end));
			}
			line.spans = Collections.unmodifiableList(spans);
			return;
		}

		String[] tokens = line.text.split("\\s+");
		long total = 0;
		for (String token : tokens) total += Math.max(1, token.length());
		if (total == 0) return;

		List<Word> spans = new ArrayList<>(tokens.length);
		long cursor = start;
		for (int i = 0; i < tokens.length; i++) {
			long length = duration * Math.max(1, tokens[i].length()) / total;
			spans.add(new Word(tokens[i], cursor, cursor + length));
			cursor += length;
		}
		line.spans = Collections.unmodifiableList(spans);
	}

	/** Words written as `<mm:ss.xx>word` inside an enhanced LRC line. */
	private static List<Word> parseWords(String raw, int offset) {
		List<Word> words = new ArrayList<>(8);
		Matcher matcher = WORD_TIME.matcher(raw);
		long start = -1;
		int last = 0;
		while (matcher.find()) {
			String text = TIME.matcher(raw.substring(last, matcher.start())).replaceAll("").trim();
			if (start >= 0 && !text.isEmpty()) words.add(new Word(text, start, 0));
			start = time(matcher, offset);
			last = matcher.end();
		}
		String tail = TIME.matcher(raw.substring(last)).replaceAll("").trim();
		if (start >= 0 && !tail.isEmpty()) words.add(new Word(tail, start, 0));
		return words.isEmpty() ? Collections.emptyList() : words;
	}

	private static long time(Matcher matcher, int offset) {
		long minutes = Long.parseLong(matcher.group(1));
		long seconds = Long.parseLong(matcher.group(2));
		long fraction = 0;
		if (matcher.group(3) != null) {
			String f = matcher.group(3);
			fraction = Long.parseLong(f);
			if (f.length() == 1) fraction *= 100;
			else if (f.length() == 2) fraction *= 10;
		}
		return Math.max(0, minutes * 60000 + seconds * 1000 + fraction + offset);
	}
}
