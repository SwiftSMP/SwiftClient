package net.swiftsmp.swiftclient.lyrics;

/**
 * A 0-1 pulse that tracks the music. Prefers the bridge's real WASAPI bass level; where that is
 * unavailable (a third-party audio driver can block WASAPI capture) it falls back to the karaoke
 * word onsets, so the screen still pumps in time with the song.
 */
public final class BassPulse {
	public static final BassPulse INSTANCE = new BassPulse();

	private double envelope;
	private double level;
	private long lastNanos;
	private int lastIndex = -1;
	private String lastKey = "";

	/** Advance the envelope once per render frame. */
	public void update(boolean enabled, double realBass, int wordIndex, String lineKey) {
		long now = System.nanoTime();
		double dt = lastNanos == 0 ? 0.0 : Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
		lastNanos = now;
		if (!enabled) {
			envelope = 0.0;
			level = 0.0;
			lastIndex = -1;
			lastKey = "";
			return;
		}
		boolean onset = wordIndex != lastIndex || !lineKey.equals(lastKey);
		lastIndex = wordIndex;
		lastKey = lineKey;
		if (onset) envelope = 1.0;
		// ~180 ms decay so each hit pops then falls away.
		envelope *= Math.exp(-dt * 6.0);
		double bass = realBass > 0 ? Math.min(1.0, realBass) : 0.0;
		level = Math.max(envelope, bass);
	}

	public double level() {
		return level;
	}
}
