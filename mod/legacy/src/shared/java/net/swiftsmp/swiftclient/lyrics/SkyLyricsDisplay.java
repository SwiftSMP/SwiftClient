package net.swiftsmp.swiftclient.lyrics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.ui.Gfx;
import net.swiftsmp.swiftclient.ui.Theme;

/**
 * 1.21.x sky lyrics. The 26.x build submits world-space text through a level render event, but
 * the 1.21.x mod deliberately avoids Fabric API and the level renderer's signature differs across
 * the five eras, so here the current karaoke line is drawn as a billboard that rides high on the
 * screen. Same look as the sky text: the sung word in white, the rest in SwiftClient orange, a
 * short scale-in when the line changes and a subtle pump on every bass hit.
 */
public final class SkyLyricsDisplay implements WorldLyrics.Display {
	public static final SkyLyricsDisplay INSTANCE = new SkyLyricsDisplay();

	private static final long ENTER_NANOS = 120_000_000L;

	private boolean visible;
	private final List<String> words = new ArrayList<>();
	private int activeIndex;
	private String currentKey = "";
	private long changedNanos;

	@Override
	public void show(WorldLyrics.Frame frame, double x, double y, double z) {
		if (frame == null || frame.words.isEmpty()) {
			hide();
			return;
		}
		activeIndex = frame.active;
		String wordsKey = String.join(" ", frame.words);
		if (!wordsKey.equals(currentKey)) {
			words.clear();
			for (String word : frame.words) {
				if (word != null && !word.isEmpty()) words.add(word);
			}
			currentKey = wordsKey;
			changedNanos = System.nanoTime();
		}
		visible = true;
	}

	@Override
	public void move(double x, double y, double z) {
		// The line rides at a fixed screen height, so the world anchor is unused here.
	}

	@Override
	public void hide() {
		visible = false;
		words.clear();
		currentKey = "";
	}

	public void render(Gfx g, Minecraft mc) {
		if (!visible || words.isEmpty() || mc.player == null || mc.level == null) return;
		SwiftConfig config = SwiftConfig.get();
		if (!config.lyricsEnabled) return;

		Font font = mc.font;
		BassPulse.INSTANCE.update(config.bassMode, LyricsService.get().state().now.bass, activeIndex, currentKey);

		// A line only gets a short, subtle scale-in when the words change; the highlight never animates.
		long now = System.nanoTime();
		double enter = Math.min(1.0, (now - changedNanos) / (double) ENTER_NANOS);
		float pop = (float) (0.97 + 0.03 * (1.0 - Math.pow(1.0 - enter, 2.5)));
		float kick = 1.0f + (float) BassPulse.INSTANCE.level() * 0.06f;

		float space = 8f;
		float scale = 0.5f * config.lyricsScale * pop * kick;
		if (scale < 0.05f) scale = 0.05f;

		int[] widths = new int[words.size()];
		float total = 0f;
		for (int i = 0; i < words.size(); i++) {
			widths[i] = (int) (font.width(Theme.displayLarge(words.get(i)).getVisualOrderText()) * scale);
			total += widths[i] + (i + 1 < words.size() ? space : 0);
		}
		// Shrink a long line so it still fits the view.
		float maxW = g.guiWidth() * 0.86f;
		if (total > maxW && total > 0) {
			float shrink = maxW / total;
			scale *= shrink;
			for (int i = 0; i < words.size(); i++) widths[i] = (int) (widths[i] * shrink);
			total = maxW;
		}

		float hf = Math.max(0f, Math.min(40f, config.lyricsSkyHeight));
		int y = (int) (g.guiHeight() * (0.60f - 0.0115f * hf));
		float px = (g.guiWidth() - total) / 2f;

		int inactiveColor = Theme.ACCENT;
		int activeColor = 0xFFFFFFFF;
		int i = 0;
		for (String word : words) {
			int color = i == activeIndex ? activeColor : inactiveColor;
			g.pushMatrix();
			g.translate(px, y);
			g.scale(scale);
			g.text(font, Theme.displayLarge(word), 0, 0, color, true);
			g.popMatrix();
			px += widths[i] + space;
			i++;
		}
	}
}
