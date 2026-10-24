package net.swiftsmp.swiftclient.platform;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.PlayerSkin;
import net.swiftsmp.swiftclient.SwiftClientMod;

/**
 * The 1.21.9+ platform: Window-based key lookups, ClientAsset skins, MouseButtonEvent clicks.
 * Resource names are the one thing that changed in 1.21.11 (ResourceLocation -> Identifier),
 * so each of those two eras supplies its own tiny {@link Ids} helper.
 */
public final class Platform {
	private Platform() {}

	public static Screen screen() {
		return Minecraft.getInstance().screen;
	}

	public static void setScreen(Screen screen) {
		Minecraft.getInstance().setScreen(screen);
	}

	public static Style pixelStyle() {
		return Ids.pixelStyle();
	}

	public static boolean isKeyDown(int key) {
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
	}

	public static int boundKey(KeyMapping mapping) {
		return InputConstants.getKey(mapping.saveString()).getValue();
	}

	public static Object registerCapeTexture(String capeId, NativeImage image) {
		return Ids.registerCape(capeId, image);
	}

	public static PlayerSkin withCape(PlayerSkin skin, Object cape) {
		ClientAsset.Texture texture = (ClientAsset.Texture) cape;
		return new PlayerSkin(skin.body(), texture, skin.elytra() != null ? skin.elytra() : texture, skin.model(), skin.secure());
	}

	public static Screen optionsScreen(Screen parent) {
		return new OptionsScreen(parent, Minecraft.getInstance().options);
	}

	public static void joinServer(Screen parent, String name, String address) {
		ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(address),
			new ServerData(name, address, ServerData.Type.OTHER), false, null);
	}

	public static void joinServer(Screen parent) {
		String address = System.getProperty("swiftclient.server.address", "swiftsmp.net");
		String name = System.getProperty("swiftclient.server.name", "SwiftSMP");
		ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(address), new ServerData(name, address, ServerData.Type.OTHER), false, null);
	}

	public static String serverName() {
		return System.getProperty("swiftclient.server.name", "SwiftSMP");
	}

	public static String serverAddress() {
		return System.getProperty("swiftclient.server.address", "swiftsmp.net");
	}

	public static void applyZoom(float divider) {
		// The GameRenderer mixin narrows the field of view directly.
	}

	/** The drag-and-resize HUD editor is 26.x only for now. */
	public static boolean canEditHud() {
		return false;
	}
}
