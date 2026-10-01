package de.vali.itemfinder.client.mixin;

import de.vali.itemfinder.client.ItemFinderClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ContainerPacketsMixin {
    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void itemfinder$menuOpened(ClientboundOpenScreenPacket packet, CallbackInfo callback) {
        ItemFinderClient.getInstance().onMenuOpened(packet.getContainerId());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void itemfinder$contentsReceived(ClientboundContainerSetContentPacket packet, CallbackInfo callback) {
        ItemFinderClient.getInstance().onMenuContents(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void itemfinder$slotReceived(ClientboundContainerSetSlotPacket packet, CallbackInfo callback) {
        ItemFinderClient.getInstance().onSlotUpdated(packet.getContainerId());
    }
}
