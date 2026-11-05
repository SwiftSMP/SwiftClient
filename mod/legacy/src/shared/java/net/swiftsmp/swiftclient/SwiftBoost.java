package net.swiftsmp.swiftclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.swiftsmp.swiftclient.config.SwiftConfig;

/**
 * SwiftClient-exclusive performance mode: a stepped quality ladder. Level 1 trims particles and
 * entity distance; higher levels also cut clouds, entity shadows and smooth lighting, and lower
 * the render distance. Measured FPS (an exponential average) steps the ladder down when the game
 * cannot hold the target and back up when there is headroom. Every original option is restored
 * when SwiftBoost is turned off or the player leaves the world.
 */
public final class SwiftBoost {
	private static final int MAX_LEVEL = 5;
	private static boolean applied;
	private static int level;
	private static long lastAdjust;
	private static float averageFps = 60f;

	private SwiftBoost() {}

	public static void tick(Minecraft mc) {
		SwiftConfig c = SwiftConfig.get();
		if (!c.swiftBoost) {
			if (applied || c.boostSavedDistance > 0) restore(mc, c);
			return;
		}
		if (mc.level == null) {
			if (applied) restore(mc, c);
			return;
		}
		if (!applied) apply(mc, c, 1);
		if (mc.screen != null) return;

		averageFps = averageFps * 0.95f + mc.getFps() * 0.05f;
		long now = System.currentTimeMillis();
		if (now - lastAdjust < 2000) return;
		lastAdjust = now;

		if (averageFps < c.boostTarget - 8 && level < MAX_LEVEL) apply(mc, c, level + 1);
		else if (averageFps > c.boostTarget + 20 && level > 0) apply(mc, c, level - 1);
	}

	private static void apply(Minecraft mc, SwiftConfig c, int newLevel) {
		if (c.boostSavedDistance < 0) saveOriginals(mc, c);
		writeLevel(mc, c, Math.max(0, Math.min(MAX_LEVEL, newLevel)));
		averageFps = Math.max(30f, mc.getFps());
		lastAdjust = System.currentTimeMillis();
		applied = true;
	}

	/** Records every option the ladder can touch, once, so off is a perfect undo. */
	private static void saveOriginals(Minecraft mc, SwiftConfig c) {
		c.boostSavedDistance = mc.options.renderDistance().get();
		c.boostSavedParticles = enumOrdinal(mc.options.particles());
		c.boostSavedEntityDistance = mc.options.entityDistanceScaling().get();
		c.boostSavedClouds = enumOrdinal(mc.options.cloudStatus());
		c.boostSavedShadows = mc.options.entityShadows().get() ? 1 : 0;
		c.boostSavedAo = mc.options.ambientOcclusion().get() ? 1 : 0;
		c.boostSavedFpsLimit = mc.options.framerateLimit().get();
		SwiftConfig.save();
	}

	private static void writeLevel(Minecraft mc, SwiftConfig c, int newLevel) {
		level = newLevel;
		int original = c.boostSavedDistance;
		int rd = switch (level) {
			case 0 -> original;
			case 1 -> Math.min(original, 12);
			case 2 -> Math.min(original, 10);
			case 3 -> Math.min(original, 8);
			case 4 -> Math.min(original, 6);
			default -> 4;
		};
		mc.options.renderDistance().set(rd);
		// Vanilla clamps entity distance to 0.5..1.0, so 0.5 is the floor.
		mc.options.entityDistanceScaling().set(level == 0 ? c.boostSavedEntityDistance : 0.5);
		setEnum(mc.options.particles(), level == 0 ? c.boostSavedParticles : 2, level == 0 ? null : "MINIMAL");
		setEnum(mc.options.cloudStatus(), level >= 2 ? 0 : c.boostSavedClouds, level >= 2 ? "OFF" : null);
		mc.options.entityShadows().set(level >= 2 ? false : c.boostSavedShadows > 0);
		mc.options.ambientOcclusion().set(level >= 3 ? false : c.boostSavedAo > 0);
		// A frame cap below the target would make the target unreachable.
		mc.options.framerateLimit().set(level == 0 ? c.boostSavedFpsLimit : Math.max(c.boostSavedFpsLimit, c.boostTarget));
	}

	private static void restore(Minecraft mc, SwiftConfig c) {
		if (c.boostSavedDistance > 0) writeLevel(mc, c, 0);
		c.boostSavedDistance = -1;
		c.boostSavedParticles = -1;
		c.boostSavedEntityDistance = -1;
		c.boostSavedClouds = -1;
		c.boostSavedShadows = -1;
		c.boostSavedAo = -1;
		c.boostSavedFpsLimit = -1;
		level = 0;
		averageFps = 60f;
		SwiftConfig.save();
		applied = false;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static int enumOrdinal(OptionInstance option) {
		Object value = option.get();
		return value instanceof Enum<?> e ? e.ordinal() : -1;
	}

	/** ParticleStatus and CloudStatus move package between versions, so set them through the option. */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void setEnum(OptionInstance option, int ordinal, String name) {
		try {
			Object[] values = option.get().getClass().getEnumConstants();
			Object target = null;
			if (name != null) {
				for (Object value : values) if (((Enum<?>) value).name().equals(name)) { target = value; break; }
			}
			if (target == null && ordinal >= 0 && ordinal < values.length) target = values[ordinal];
			if (target != null) option.set(target);
		} catch (RuntimeException ignored) {
			// A future Minecraft may change the option; boost still works without that lever.
		}
	}

	public static boolean active() {
		return applied;
	}

	/** 0 when untouched, 1..5 for the current rung of the quality ladder. */
	public static int level() {
		return applied ? level : 0;
	}
}
