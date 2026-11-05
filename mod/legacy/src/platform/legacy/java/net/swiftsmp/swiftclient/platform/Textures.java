package net.swiftsmp.swiftclient.platform;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.swiftsmp.swiftclient.SwiftClientMod;

/**
 * Cape texture registration. 1.21 - 1.21.4 construct DynamicTexture from an image alone,
 * 1.21.5 - 1.21.8 require a name supplier too. Both constructors are resolved at runtime
 * so one platform layer covers the whole range.
 */
final class Textures {
	private static boolean splashDone;

	private Textures() {}

	static Object cape(String capeId, NativeImage image) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "capes/" + capeId);
		DynamicTexture texture = create(capeId, image);
		smooth(texture);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		return id;
	}

	/** Linear filtering keeps HD cloak art clean when it shrinks on screen. */
	private static void smooth(DynamicTexture texture) {
		try {
			texture.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(texture, true, false);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Keep the renderer's default filtering where setFilter is unavailable.
		}
	}

	/** Replaces the splash logo with SwiftClient art under the vanilla resource id. */
	static void splash() {
		if (splashDone) return;
		splashDone = true;
		try (InputStream in = SwiftClientMod.class.getResourceAsStream("/assets/swiftclient/title/splash.png")) {
			if (in == null) throw new IllegalStateException("splash.png missing from the jar");
			NativeImage image = NativeImage.read(in);
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/title/mojangstudios.png");
			Minecraft.getInstance().getTextureManager().register(id, create("splash", image));
		} catch (Exception e) {
			SwiftClientMod.LOG.warn("Could not apply the SwiftClient splash: {}", e.toString());
		}
	}

	private static DynamicTexture create(String capeId, NativeImage image) {
		try {
			Constructor<DynamicTexture> plain = DynamicTexture.class.getConstructor(NativeImage.class);
			return plain.newInstance(image);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			try {
				Constructor<DynamicTexture> named = DynamicTexture.class.getConstructor(Supplier.class, NativeImage.class);
				Supplier<String> name = () -> "SwiftClient cape " + capeId;
				return named.newInstance(name, image);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("No DynamicTexture constructor for this Minecraft version", e);
			}
		}
	}
}
