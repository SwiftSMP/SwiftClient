package net.swiftsmp.swiftclient.platform;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.swiftsmp.swiftclient.ui.Gfx;

/** 1.21.9+ screens render through render(GuiGraphics, ...) and take MouseButtonEvent clicks. */
public abstract class ScreenBase extends Screen {
	protected ScreenBase(String title) {
		super(Component.literal(title));
	}

	public abstract void renderFrame(Gfx g, int mouseX, int mouseY, float delta);

	public abstract boolean onClick(double mouseX, double mouseY, int button);

	/** Platform-neutral key/char hooks: 1.21 - 1.21.8 pass ints, 1.21.9+ pass event objects. */
	protected boolean swiftKey(int keyCode) {
		return false;
	}

	protected boolean swiftChar(char codePoint) {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (swiftKey(e.key())) return true;
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		String s = e.codepointAsString();
		if (!s.isEmpty() && swiftChar(s.charAt(0))) return true;
		return super.charTyped(e);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// Without a world (title screen, or the menu opened from it) show the vanilla panorama,
		// exactly as the 26.x screens do; in game the world stays visible instead.
		if (net.minecraft.client.Minecraft.getInstance().level == null) {
			renderPanorama(graphics, delta);
		}
		renderFrame(new GuiGfx(graphics), mouseX, mouseY, delta);
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// SwiftClient screens paint their own backdrop.
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (onClick(event.x(), event.y(), event.button())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
