package net.swiftsmp.swiftclient.lyrics;

/** Owns the now-playing bridge, the LRCLIB request for the current track and the HUD snapshot. */
public final class LyricsService {
	public enum Status { IDLE, LOADING, READY, INSTRUMENTAL, NONE, ERROR }

	public static final class State {
		public final NowPlaying now;
		public final SyncedLyrics lyrics;
		public final Status status;
		public final String error;

		State(NowPlaying now, SyncedLyrics lyrics, Status status, String error) {
			this.now = now;
			this.lyrics = lyrics;
			this.status = status;
			this.error = error == null ? "" : error;
		}

		public long positionMs() {
			return now.positionAt(System.currentTimeMillis());
		}
	}

	private static LyricsService instance;

	private final NowPlayingReader reader;
	private final LyricsClient client;
	private volatile State state = new State(NowPlaying.NONE, null, Status.IDLE, "");
	private String trackKey = "";
	private int generation;
	private long lastFetchMs;
	private boolean closed;

	private LyricsService() {
		reader = new NowPlayingReader();
		client = new LyricsClient(reader.directory());
		Runtime.getRuntime().addShutdownHook(new Thread(this::close, "SwiftClient-Lyrics-Stop"));
	}

	public static synchronized LyricsService init() {
		if (instance == null) instance = new LyricsService();
		return instance;
	}

	public static LyricsService get() {
		return init();
	}

	/** Called from the client tick. Network is only touched when the Lyrics HUD is enabled. */
	public void tick(boolean allowNetwork) {
		if (closed) return;
		NowPlaying nowPlaying = reader.poll();
		if (nowPlaying == null) nowPlaying = NowPlaying.NONE;
		String key = nowPlaying.key();
		long now = System.currentTimeMillis();

		if (!key.equals(trackKey)) {
			trackKey = key;
			generation++;
			// While the HUD is off we do not fetch; the next tick after it is enabled starts the request.
			state = new State(nowPlaying, null, key.isEmpty() || !allowNetwork ? Status.IDLE : Status.LOADING, "");
			if (!key.isEmpty() && allowNetwork) request(nowPlaying, generation, now);
			return;
		}

		State previous = state;
		state = new State(nowPlaying, previous.lyrics, previous.status, previous.error);
		if (!nowPlaying.hasTrack()) {
			if (previous.status != Status.IDLE) state = new State(nowPlaying, null, Status.IDLE, "");
			return;
		}
		if (allowNetwork && previous.lyrics == null && previous.status != Status.LOADING
			&& previous.status != Status.READY && previous.status != Status.INSTRUMENTAL
			&& now - lastFetchMs > 30_000) {
			request(nowPlaying, generation, now);
		}
	}

	private void request(NowPlaying nowPlaying, int requestGeneration, long nowMs) {
		lastFetchMs = nowMs;
		client.fetch(nowPlaying, result -> {
			if (closed || requestGeneration != generation) return;
			Status status = switch (result.status) {
				case READY -> Status.READY;
				case INSTRUMENTAL -> Status.INSTRUMENTAL;
				case NONE -> Status.NONE;
				case ERROR -> Status.ERROR;
			};
			state = new State(state.now, result.lyrics, status, result.error);
		});
	}

	public void command(String command) {
		reader.command(command);
	}

	public State state() {
		return state;
	}

	public boolean hasTrack() {
		return state.now.hasTrack();
	}

	public int accent(int fallback) {
		return state.now.accent == 0 ? fallback : state.now.accent;
	}

	public void close() {
		if (closed) return;
		closed = true;
		client.close();
		reader.stop();
	}
}
