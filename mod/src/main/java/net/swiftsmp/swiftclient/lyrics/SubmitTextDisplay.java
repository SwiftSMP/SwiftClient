package net.swiftsmp.swiftclient.lyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.swiftsmp.swiftclient.config.SwiftConfig;
import net.swiftsmp.swiftclient.ui.Theme;

/**
 * 26.x sky lyrics: the current line in Instrument Sans, floating in the sky in SwiftClient orange
 * with a soft shadow and no panel behind it, leaning slightly away from the camera so it reads as 3D
 * rather than a flat HUD. A new line fades in without drawing the outgoing one, the anchor tracks
 * the camera instantly so lines never swim behind when you look around, and a long line shrinks to
 * stay inside the view.
 */
public final class SubmitTextDisplay implements WorldLyrics.Display {
	private static final int LIGHT = 0xF000F0;
	private static final float WORD_GAP = 4.0f;
	private static final long ENTER_NANOS = 120_000_000L;

	/** Cache baked glyphs by text to avoid re-rasterizing on every word change. */
	private static final Map<String, CachedGlyph> GLYPH_CACHE = new HashMap<>(64);
	private static final int MAX_CACHE_SIZE = 256;

	private static final class CachedGlyph {
		final FormattedCharSequence sequence;
		final int width;

		CachedGlyph(String text) {
			// Use default Minecraft font
			this.sequence = net.minecraft.network.chat.Component.literal(text).getVisualOrderText();
			this.width = Minecraft.getInstance().font.width(this.sequence);
		}
	}

	private static final class Glyph {
		final CachedGlyph cached;
		final float x;

		Glyph(String text, float x) {
			// Check cache first to avoid re-baking the same text
			CachedGlyph hit = GLYPH_CACHE.get(text);
			if (hit == null) {
				if (GLYPH_CACHE.size() >= MAX_CACHE_SIZE) {
					// Evict oldest entries when cache is full
					List<String> keys = new ArrayList<>(GLYPH_CACHE.keySet());
					for (int i = 0; i < MAX_CACHE_SIZE / 4 && i < keys.size(); i++) {
						GLYPH_CACHE.remove(keys.get(i));
					}
				}
				hit = new CachedGlyph(text);
				GLYPH_CACHE.put(text, hit);
			}
			this.cached = hit;
			this.x = x;
		}

		FormattedCharSequence sequence() { return cached.sequence; }
		int width() { return cached.width; }
	}

	private static final class Line {
		final List<Glyph> glyphs;
		final float width;

		Line(List<String> words) {
			List<Glyph> out = new ArrayList<>(words.size());
			float x = 0;
			for (String word : words) {
				if (word == null || word.isEmpty()) continue;
				Glyph glyph = new Glyph(word, x);
				out.add(glyph);
				x += glyph.width() + WORD_GAP;
			}
			glyphs = out;
			width = out.isEmpty() ? 0 : x - WORD_GAP;
		}
	}

	private boolean visible;
	private Line current;
	private int activeIndex;

	private String currentKey = "";
	private double centerX;
	private double centerY;
	private double centerZ;
	private long changedNanos;
	/** Last valid camera orientation, reused if a frame reports a degenerate one. */
	private final org.joml.Quaternionf billboard = new org.joml.Quaternionf();

	@Override
	public void show(WorldLyrics.Frame frame, double x, double y, double z) {
		if (frame == null || frame.words.isEmpty()) {
			hide();
			return;
		}
		activeIndex = frame.active;
		// Only rebuild the line when the words change, not when the highlighted word moves.
		String wordsKey = String.join(" ", frame.words);
		if (current == null || !wordsKey.equals(currentKey)) {
			current = new Line(frame.words);
			currentKey = wordsKey;
			// Only reset the scale-in when the line changes, never on word highlight updates.
			changedNanos = System.nanoTime();
		}
		visible = true;
	}

	@Override
	public void move(double x, double y, double z) {
		// The sky lyrics are centred above the player.
	}

	@Override
	public void hide() {
		visible = false;
		current = null;
		currentKey = "";
	}

	public void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!visible || player == null || mc.level == null) return;
		CameraRenderState cameraState = context.levelState().cameraRenderState;
		if (cameraState == null || !cameraState.initialized) return;
		// Flat billboard: cache a valid camera orientation so the text always faces the screen
		// squarely; a one-frame degenerate quaternion would otherwise collapse the transform.
		org.joml.Quaternionf orientation = cameraState.orientation;
		if (orientation != null && isFinite(orientation)) {
			billboard.set(orientation);
		}

		// mc.getDeltaTracker() is stable across 26.1-26.3; the level-state partial-tick field
		// was renamed between these snapshots, so it cannot be referenced directly.
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (!Float.isFinite(partial)) partial = 0.0f;
		partial = Math.max(0.0f, Math.min(1.0f, partial));
		Vec3 eye = player.getEyePosition(partial);
		if (!Double.isFinite(eye.x) || !Double.isFinite(eye.y) || !Double.isFinite(eye.z)) return;
		SwiftConfig config = SwiftConfig.get();
		float height = Math.max(2.0f, config.lyricsSkyHeight);

		// Use the player's raw body yaw: it is always horizontal and well-defined (unlike the
		// camera forward, which degenerates when looking straight up). A smoothed yaw lagged
		// behind fast turns and left the anchor behind the camera, so the text got culled.
		double yaw = Math.toRadians(player.getYRot(partial));
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		// ~27 degrees above the horizon: inside the forward view when walking, yet clearly in the sky.
		double ahead = height * 2.0;
		double tx = eye.x + fx * ahead;
		double ty = eye.y + height;
		double tz = eye.z + fz * ahead;
		// Only commit a valid target; an invalid frame keeps the last good anchor.
		if (Double.isFinite(tx) && Double.isFinite(ty) && Double.isFinite(tz)) {
			centerX = tx;
			centerY = ty;
			centerZ = tz;
		}

		float distance = (float) Math.hypot(ahead, height);
		float baseScale = clamp(0.75f * height * config.lyricsScale, 0.6f, 20.0f);
		// cameraState.pos is the partial-ticked camera position and exists on 26.1-26.3; the
		// GameRenderer.mainCamera() accessor is gone on 26.1, so read the render state directly.
		Vec3 cameraPos = cameraState.pos;
		if (!Double.isFinite(cameraPos.x) || !Double.isFinite(cameraPos.y) || !Double.isFinite(cameraPos.z)) return;

		// Karaoke: full opacity always (no blinking). A line only gets a short, subtle scale-in
		// when the words change; the word highlight itself never animates.
		long now = System.nanoTime();
		double enter = Math.min(1.0, (now - changedNanos) / (double) ENTER_NANOS);
		double eased = 1.0 - Math.pow(1.0 - enter, 2.5);
		float pop = 0.97f + 0.03f * (float) eased;

		// Advance the bass pulse and add a scale kick so the words pump with the music.
		BassPulse.INSTANCE.update(config.bassMode, LyricsService.get().state().now.bass, activeIndex, currentKey);
		float kick = 1.0f + (float) BassPulse.INSTANCE.level() * 0.16f;
		if (current != null) {
			submitLine(context, context.poseStack(), cameraState, cameraPos, current, baseScale * kick, distance,
				0.0, 255, pop);
		}
	}

	private void submitLine(LevelRenderContext context, PoseStack pose, CameraRenderState cameraState, Vec3 cameraPos,
			Line line, float baseScale, float viewDistance, double slide, int alpha, float pop) {
		if (line == null || line.glyphs.isEmpty()) return;
		pose.pushPose();
		pose.translate(centerX - cameraPos.x, centerY - cameraPos.y + slide, centerZ - cameraPos.z);
		float scale = scaleFor(line, baseScale, viewDistance) * pop;
		// Snap to whole pixels for sharp text
		pose.scale(scale, scale, scale);
		// Billboard: face camera directly for crisp pixel-perfect text (no oblique lean).
		// PoseStack.rotate(Quaternion) only exists on 26.3; mulPose(Matrix4f) is present on every
		// 26.x, so drive the billboard through a rotation matrix built from the camera quaternion.
		pose.mulPose(new org.joml.Matrix4f().rotation(billboard));
		pose.scale(0.025f, -0.025f, 0.025f);

		float px = -line.width / 2.0f;
		// Karaoke: the active word in white, the rest in orange.
		int inactiveColor = (alpha << 24) | (Theme.ACCENT & 0xFFFFFF);
		int activeColor = (alpha << 24) | 0xFFFFFF;
		// Crisp fully opaque black outline so the white word stays readable against the bright sky.
		int outline = (alpha << 24);
		int wordIndex = 0;
		for (Glyph glyph : line.glyphs) {
			pose.pushPose();
			// Snap to integer pixel positions for sharp rendering
			pose.translate(Math.round(px + glyph.x), 0, 0);
			int color = (wordIndex == activeIndex) ? activeColor : inactiveColor;
			context.submitNodeCollector().submitText(pose, 0, 0, glyph.sequence(), false,
				Font.DisplayMode.SEE_THROUGH, LIGHT, color, 0, outline);
			pose.popPose();
			wordIndex++;
		}
		pose.popPose();
	}

	private static float scaleFor(Line line, float base, float distance) {
		if (line == null || line.width <= 0) return base;
		float maxWorldWidth = 1.4f * distance;
		float worldWidth = line.width * 0.025f * base;
		if (worldWidth <= maxWorldWidth) return base;
		return Math.max(0.5f, base * (maxWorldWidth / worldWidth));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static boolean isFinite(org.joml.Quaternionf q) {
		return Float.isFinite(q.x) && Float.isFinite(q.y) && Float.isFinite(q.z) && Float.isFinite(q.w);
	}
}
