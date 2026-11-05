package net.swiftsmp.swiftclient.platform;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.swiftsmp.swiftclient.SwiftClientMod;

/** 1.21.9 - 1.21.10 still call resource names ResourceLocation. */
final class Ids {
	private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "icon.png");
	private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "title/background.png");
	private static final ResourceLocation CYANREALMS = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "servers/cyanrealms.png");
	private static final ResourceLocation VALENMC = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "servers/valenmc.png");
	private static final ResourceLocation MENU_LOGO = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "icon_menu.png");
	private static boolean splashDone;

	private Ids() {}

	static void splash() {
		if (splashDone) return;
		splashDone = true;
		try (InputStream in = SwiftClientMod.class.getResourceAsStream("/assets/swiftclient/title/splash.png")) {
			if (in == null) throw new IllegalStateException("splash.png missing from the jar");
			NativeImage image = NativeImage.read(in);
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/title/mojangstudios.png");
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "SwiftClient splash", image));
		} catch (Exception e) {
			SwiftClientMod.LOG.warn("Could not apply the SwiftClient splash: {}", e.toString());
		}
	}

	static void logo(GuiGraphics g, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0f, 0.0f, size, size, size, size);
	}

	static void serverIcon(GuiGraphics g, String key, int x, int y, int size) {
		ResourceLocation id = key.equals("cyanrealms") ? CYANREALMS : key.equals("valenmc") ? VALENMC : LOGO;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0f, 0.0f, size, size, size, size);
	}

	static void menuLogo(GuiGraphics g, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, MENU_LOGO, x, y, 0.0f, 0.0f, size, size, size, size);
	}

	static boolean uiIcon(GuiGraphics g, String id, int x, int y, int size) {
		if (!UiIcons.known(id)) return false;
		ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "ui/" + id + ".png");
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0f, 0.0f, size, size, size, size);
		return true;
	}

	static void background(GuiGraphics g, int x, int y, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0f, 0.0f, w, h, w, h);
	}

	static Style sansStyle() {
		return Style.EMPTY.withFont(new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "sans")));
	}

	static Style sansLargeStyle() {
		return Style.EMPTY.withFont(new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "sans_big")));
	}

	static Style pixelStyle() {
		return Style.EMPTY.withFont(new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "pixel")));
	}

	static Style pixelLargeStyle() {
		return Style.EMPTY.withFont(new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "pixel_big")));
	}

	static Style numStyle() {
		return Style.EMPTY.withFont(new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "num")));
	}

	static Object registerCape(String capeId, NativeImage image) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "capes/" + capeId);
		DynamicTexture texture = new DynamicTexture(() -> "SwiftClient cape " + capeId, image);
		smooth(texture);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		return new ClientAsset.ResourceTexture(id, id);
	}

	/** Linear filtering keeps HD cloak art clean when it shrinks on screen. */
	private static void smooth(DynamicTexture texture) {
		try {
			texture.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(texture, true, false);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// Keep the renderer's default filtering where setFilter is unavailable.
		}
	}
}
