package de.vali.itemfinder.client.mixin;

import de.vali.itemfinder.client.ChestHighlightRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void itemfinder$closeHighlightBuffers(CallbackInfo ci) {
        ChestHighlightRenderer.close();
    }
}
