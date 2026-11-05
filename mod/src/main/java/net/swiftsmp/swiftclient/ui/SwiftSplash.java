package net.swiftsmp.swiftclient.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.swiftsmp.swiftclient.SwiftClientMod;

/** Replaces the early Mojang splash texture under the same resource id with SwiftClient art. */
public final class SwiftSplash {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/title/mojangstudios.png");
	private static boolean done;

	private SwiftSplash() {}

	public static void ensure() {
		if (done) return;
		done = true;
		try (InputStream in = SwiftSplash.class.getResourceAsStream("/assets/swiftclient/title/splash.png")) {
			if (in == null) throw new IllegalStateException("splash.png missing from the jar");
			NativeImage image = NativeImage.read(in);
			Minecraft.getInstance().getTextureManager().register(LOGO, new DynamicTexture(() -> "SwiftClient splash", image));
		} catch (Exception e) {
			SwiftClientMod.LOG.warn("Could not apply the SwiftClient splash: {}", e.toString());
		}
	}
}
