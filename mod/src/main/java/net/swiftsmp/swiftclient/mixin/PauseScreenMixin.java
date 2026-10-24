package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.swiftsmp.swiftclient.Compat;
import net.swiftsmp.swiftclient.ui.SwiftMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lunar-style pause menu entry: a SwiftClient button under the vanilla buttons. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
	@Shadow
	protected abstract <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget);

	@Shadow
	protected int width;

	@Shadow
	protected int height;

	@Inject(method = "init", at = @At("TAIL"))
	private void swiftclient$menuButton(CallbackInfo ci) {
		// Sits under the vanilla button grid, centred, like the title screen's SwiftClient row.
		int bw = 120;
		int bx = width / 2 - bw / 2;
		int by = height - 28;
		addRenderableWidget(Button.builder(Component.literal("SwiftClient"), b -> Compat.setScreen(new SwiftMenuScreen()))
			.bounds(bx, by, bw, 20)
			.build());
	}
}
