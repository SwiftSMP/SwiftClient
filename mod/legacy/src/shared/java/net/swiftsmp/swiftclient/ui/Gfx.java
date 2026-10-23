package net.swiftsmp.swiftclient.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The small drawing surface SwiftClient's UI needs, so one implementation of the menu,
 * HUD and title screen builds for both 1.21.x rendering eras. Each platform layer wraps
 * its own GuiGraphics in this.
 */
public interface Gfx {
	void fill(int x1, int y1, int x2, int y2, int color);

	void fillGradient(int x1, int y1, int x2, int y2, int top, int bottom);

	void text(Font font, Component text, int x, int y, int color, boolean shadow);

	void text(Font font, String text, int x, int y, int color, boolean shadow);

	void centeredText(Font font, Component text, int centerX, int y, int color);

	void item(ItemStack stack, int x, int y);

	/** Draws the SwiftClient mascot (swiftclient:icon.png) as a square. */
	void logo(int x, int y, int size);

	int guiWidth();

	int guiHeight();

	/** Matrix stack helpers, named after the 26.x API so the UI code reads the same. */
	void pushMatrix();

	void translate(float x, float y);

	void scale(float factor);

	void popMatrix();

	void enableScissor(int x1, int y1, int x2, int y2);

	void disableScissor();
}
