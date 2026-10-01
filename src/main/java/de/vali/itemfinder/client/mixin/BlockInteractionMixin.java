package de.vali.itemfinder.client.mixin;

import de.vali.itemfinder.client.ItemFinderClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class BlockInteractionMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void itemfinder$trackChest(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> callback) {
        ItemFinderClient.getInstance().onBlockUse(hit.getBlockPos());
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void itemfinder$discardFailedUse(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                              CallbackInfoReturnable<InteractionResult> callback) {
        if (!callback.getReturnValue().consumesAction()) ItemFinderClient.getInstance().cancelPendingInteraction();
    }

    @Inject(method = {"useItem", "interact"}, at = @At("HEAD"))
    private void itemfinder$discardUnrelatedUse(CallbackInfoReturnable<InteractionResult> callback) {
        ItemFinderClient.getInstance().cancelPendingInteraction();
    }
}
