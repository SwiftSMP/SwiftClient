package net.swiftsmp.swiftclient.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.swiftsmp.swiftclient.SwiftClientMod;
import net.swiftsmp.swiftclient.ui.Gfx;

/** 1.21 - 1.21.5 drawing surface: GuiGraphics with the old PoseStack matrices. */
public final class GuiGfx implements Gfx {
	private static final ResourceLocation LOGO = ResourceLocation.fromNamespaceAndPath(SwiftClientMod.MOD_ID, "icon.png");

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

	@Override public void logo(int x, int y, int size) { g.blit(RenderType::guiTextured, LOGO, x, y, 0.0f, 0.0f, size, size, size, size); }

	@Override public int guiWidth() { return g.guiWidth(); }

	@Override public int guiHeight() { return g.guiHeight(); }

	@Override public void pushMatrix() { g.pose().pushPose(); }

	@Override public void translate(float x, float y) { g.pose().translate(x, y, 0); }

	@Override public void scale(float factor) { g.pose().scale(factor, factor, 1); }

	@Override public void popMatrix() { g.pose().popPose(); }

	@Override public void enableScissor(int x1, int y1, int x2, int y2) { g.enableScissor(x1, y1, x2, y2); }

	@Override public void disableScissor() { g.disableScissor(); }
}
