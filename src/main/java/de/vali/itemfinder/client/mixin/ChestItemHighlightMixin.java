package de.vali.itemfinder.client.mixin;

import de.vali.itemfinder.client.ChestItemHighlightRenderer;
import de.vali.itemfinder.client.ItemFinderClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ChestItemHighlightMixin {
    @Shadow @Final protected AbstractContainerMenu menu;

    @Inject(method = "extractSlotHighlightFront", at = @At("TAIL"))
    private void itemfinder$highlightSearchedItems(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        ItemFinderClient finder = ItemFinderClient.getInstance();
        if (finder != null && menu instanceof ChestMenu chest && finder.isOpenChestMenu(chest)) {
            ChestItemHighlightRenderer.extract(graphics, chest, finder.selectedItemIds());
        }
    }
}
