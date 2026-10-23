package net.swiftsmp.swiftclient.platform;

import com.mojang.blaze3d.platform.NativeImage;
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
	private Textures() {}

	static Object cape(String capeId, NativeImage image) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "capes/" + capeId);
		Minecraft.getInstance().getTextureManager().register(id, create(capeId, image));
		return id;
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
