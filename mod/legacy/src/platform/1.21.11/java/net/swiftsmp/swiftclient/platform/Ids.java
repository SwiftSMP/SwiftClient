package net.swiftsmp.swiftclient.platform;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.swiftsmp.swiftclient.SwiftClientMod;

/** 1.21.11 renamed ResourceLocation to Identifier. */
final class Ids {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "icon.png");

	private Ids() {}

	static void logo(GuiGraphics g, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0f, 0.0f, size, size, size, size);
	}

	static Style pixelStyle() {
		return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "pixel")));
	}

	static Object registerCape(String capeId, NativeImage image) {
		Identifier id = Identifier.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "capes/" + capeId);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "SwiftClient cape " + capeId, image));
		return new ClientAsset.ResourceTexture(id, id);
	}
}
