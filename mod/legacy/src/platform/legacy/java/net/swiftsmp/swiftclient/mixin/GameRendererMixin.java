package net.swiftsmp.swiftclient.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Present so the shared mixin config stays valid. On 1.21 - 1.21.8 zoom changes the
 * field-of-view option instead (getFov returns float on 1.21.4+ and double before that,
 * so one injection signature cannot cover every release).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
}
