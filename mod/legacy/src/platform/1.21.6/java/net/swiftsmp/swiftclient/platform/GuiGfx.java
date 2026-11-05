package net.swiftsmp.swiftclient.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.swiftsmp.swiftclient.SwiftClientMod;
import net.swiftsmp.swiftclient.ui.Gfx;

/** 1.21.6 - 1.21.8 drawing surface: GuiGraphics with the JOML Matrix3x2fStack pose. */
public final class GuiGfx implements Gfx {
	private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "icon.png");
	private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "title/background.png");
	private static final ResourceLocation CYANREALMS = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "servers/cyanrealms.png");
	private static final ResourceLocation VALENMC = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "servers/valenmc.png");
	private static final ResourceLocation MENU_LOGO = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "icon_menu.png");

	private final GuiGraphics g;

	public GuiGfx(GuiGraphics g) {
		this.g = g;
	}

	@Override public void fill(int x1, int y1, int x2, int y2, int color) { g.fill(x1, y1, x2, y2, color); }

	@Override public void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom) { g.fillGradient(x1, y1, x2, y2, top, bottom); }

	@Override public void text(Font font, Component text, int x, int y, int color, boolean shadow) { g.drawString(font, text, x, y, color, shadow); }

	@Override public void text(Font font, String text, int x, int y, int color, boolean shadow) { g.drawString(font, text, x, y, color, shadow); }

	@Override public void centeredText(Font font, Component text, int centerX, int y, int color) { g.drawCenteredString(font, text, centerX, y, color); }

	@Override public void item(ItemStack stack, int x, int y) { g.renderItem(stack, x, y); }

	@Override public void logo(int x, int y, int size) { g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0f, 0.0f, size, size, size, size); }

	@Override public void serverIcon(String key, int x, int y, int size) {
		ResourceLocation id = key.equals("cyanrealms") ? CYANREALMS : key.equals("valenmc") ? VALENMC : LOGO;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0f, 0.0f, size, size, size, size);
	}

	@Override public void menuLogo(int x, int y, int size) { g.blit(RenderPipelines.GUI_TEXTURED, MENU_LOGO, x, y, 0.0f, 0.0f, size, size, size, size); }

	@Override public boolean uiIcon(String id, int x, int y, int size) {
		if (!UiIcons.known(id)) return false;
		ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "ui/" + id + ".png");
		g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0f, 0.0f, size, size, size, size);
		return true;
	}

	@Override public void background(int x, int y, int w, int h) { g.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0f, 0.0f, w, h, w, h); }

	@Override public int guiWidth() { return g.guiWidth(); }

	@Override public int guiHeight() { return g.guiHeight(); }

	@Override public void pushMatrix() { g.pose().pushMatrix(); }

	@Override public void translate(float x, float y) { g.pose().translate(x, y); }

	@Override public void scale(float factor) { g.pose().scale(factor, factor); }

	@Override public void popMatrix() { g.pose().popMatrix(); }

	@Override public void enableScissor(int x1, int y1, int x2, int y2) { g.enableScissor(x1, y1, x2, y2); }

	@Override public void disableScissor() { g.disableScissor(); }
}
