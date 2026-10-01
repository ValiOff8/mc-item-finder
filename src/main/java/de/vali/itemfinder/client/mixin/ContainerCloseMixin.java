package de.vali.itemfinder.client.mixin;

import de.vali.itemfinder.client.ItemFinderClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class ContainerCloseMixin {
    @Inject(method = "clientSideCloseContainer", at = @At("HEAD"))
    private void itemfinder$saveBeforeClosing(CallbackInfo callback) {
        ItemFinderClient.getInstance().onMenuClosing();
    }
}
