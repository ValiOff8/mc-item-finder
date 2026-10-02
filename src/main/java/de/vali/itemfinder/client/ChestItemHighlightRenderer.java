package de.vali.itemfinder.client;

import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Highlights the live chest contents without changing stacks or click handling. */
public final class ChestItemHighlightRenderer {
    private ChestItemHighlightRenderer() {}

    public static void extract(GuiGraphicsExtractor graphics, ChestMenu menu, Set<String> selectedIds) {
        if (selectedIds.isEmpty()) return;
        for (Slot slot : menu.slots) {
            if (!matches(menu, slot, selectedIds)) continue;
            // The container screen has already translated the pose to the chest's origin.
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x30FFD65A);
            graphics.outline(slot.x - 1, slot.y - 1, 18, 18, 0xFFFFD65A);
        }
    }

    static boolean matches(ChestMenu menu, Slot slot, Set<String> selectedIds) {
        if (slot.container != menu.getContainer() || !slot.isActive()) return false;
        ItemStack stack = slot.getItem();
        return !stack.isEmpty()
            && selectedIds.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
