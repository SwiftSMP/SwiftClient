package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * PauseScreen inherits addRenderableWidget from Screen, and a shadow only resolves methods
 * declared by the mixin's own target, so the pause mixin calls it through this invoker.
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
	@Invoker("addRenderableWidget")
	<T extends GuiEventListener & Renderable & NarratableEntry> T swiftclient$addRenderableWidget(T widget);
}
