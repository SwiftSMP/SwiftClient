package net.swiftsmp.swiftclient.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.swiftsmp.swiftclient.SwiftClientMod;

/**
 * The 1.21 - 1.21.8 platform: ResourceLocation names, GLFW handle key lookups and
 * PlayerSkin textures addressed by ResourceLocation.
 */
public final class Platform {
	private static int baseFov = -1;
	private static boolean zoomActive;

	private Platform() {}

	public static Screen screen() {
		return Minecraft.getInstance().screen;
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static Style pixelStyle() {
		return Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "pixel"));
	}

	public static boolean isKeyDown(int key) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
	}

	public static int boundKey(KeyMapping mapping) {
		return InputConstants.getKey(mapping.saveString()).getValue();
	}

	public static Object registerCapeTexture(String capeId, NativeImage image) {
		return Textures.cape(capeId, image);
	}

	public static PlayerSkin withCape(PlayerSkin skin, Object cape) {
		ResourceLocation texture = (ResourceLocation) cape;
		ResourceLocation elytra = skin.elytraTexture() != null ? skin.elytraTexture() : texture;
		return new PlayerSkin(skin.texture(), skin.textureUrl(), texture, elytra, skin.model(), skin.secure());
	}

	public static Screen optionsScreen(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options);
	}

	public static void joinServer(Screen parent) {
		String address = System.getProperty("swiftclient.server.address", "swiftsmp.net");
		String name = System.getProperty("swiftclient.server.name", "SwiftSMP");
		ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(address),
			new ServerData(name, address, ServerData.Type.OTHER), false, null);
	}

	public static String serverName() {
		return System.getProperty("swiftclient.server.name", "SwiftSMP");
	}

	public static String serverAddress() {
		return System.getProperty("swiftclient.server.address", "swiftsmp.net");
	}

	/**
	 * Zoom without a GameRenderer mixin: the field of view option is divided while the
	 * key is held and restored afterwards. Works unchanged across 1.21.x.
	 */
	public static void applyZoom(float divider) {
		OptionInstance<Integer> fov = Minecraft.getInstance().options.fov();
		boolean zooming = divider > 1.001f;
		if (zooming) {
			if (!zoomActive) baseFov = fov.get();
			fov.set(Math.max(1, Math.round(baseFov / divider)));
		} else if (zoomActive) {
			fov.set(baseFov);
		}
		zoomActive = zooming;
	}

	/** The drag-and-resize HUD editor is 26.x only for now. */
	public static boolean canEditHud() {
		return false;
	}
}
