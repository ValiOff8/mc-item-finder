package de.vali.itemfinder.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChestItemHighlightRendererTest {
    private static final String DIAMOND = "minecraft:diamond";
    private static final String IRON = "minecraft:iron_ingot";

    @BeforeAll
    static void initializeItems() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Minecraft 26.3 binds item defaults after the world registries are available.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
            .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    private static Inventory playerInventory() {
        // SimpleContainer does not need a player to construct the client-side chest menu.
        return new Inventory(null, new EntityEquipment());
    }

    private static List<Integer> highlightedSlots(ChestMenu menu, Set<String> selectedIds) {
        return menu.slots.stream()
            .filter(slot -> ChestItemHighlightRenderer.matches(menu, slot, selectedIds))
            .map(slot -> slot.index).toList();
    }

    @Test
    void highlightsEverySelectedStackButExcludesPlayerInventory() {
        ChestMenu menu = ChestMenu.threeRows(1, playerInventory());
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 64));
        menu.getSlot(13).set(new ItemStack(Items.IRON_INGOT, 7));
        menu.getSlot(26).set(new ItemStack(Items.DIAMOND, 1));
        menu.getSlot(14).set(new ItemStack(Items.EMERALD, 3));
        menu.getSlot(27).set(new ItemStack(Items.DIAMOND, 10));
        menu.getSlot(menu.slots.size() - 1).set(new ItemStack(Items.IRON_INGOT, 4));

        assertEquals(List.of(0, 13, 26), highlightedSlots(menu, Set.of(DIAMOND, IRON)));
    }

    @Test
    void includesBothDoubleChestHalvesWithoutIncludingThePlayerSlots() {
        ChestMenu menu = ChestMenu.sixRows(2, playerInventory());
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND));
        menu.getSlot(27).set(new ItemStack(Items.DIAMOND));
        menu.getSlot(53).set(new ItemStack(Items.IRON_INGOT));
        menu.getSlot(54).set(new ItemStack(Items.DIAMOND));

        assertEquals(List.of(0, 27, 53), highlightedSlots(menu, Set.of(DIAMOND, IRON)));
    }

    @Test
    void followsLiveContentsWhenStacksMoveChangeOrBecomeEmpty() {
        ChestMenu menu = ChestMenu.threeRows(1, playerInventory());
        Slot source = menu.getSlot(0);
        Slot destination = menu.getSlot(1);
        source.set(new ItemStack(Items.DIAMOND, 64));
        Set<String> selection = Set.of(DIAMOND);
        assertEquals(List.of(0), highlightedSlots(menu, selection));

        destination.set(source.getItem());
        source.set(ItemStack.EMPTY);
        assertEquals(List.of(1), highlightedSlots(menu, selection));

        destination.set(new ItemStack(Items.IRON_INGOT));
        assertTrue(highlightedSlots(menu, selection).isEmpty());
        destination.set(ItemStack.EMPTY);
        assertTrue(highlightedSlots(menu, selection).isEmpty());
    }

    @Test
    void deselectingAndClearingItemsRemovesTheirHighlights() {
        ChestMenu menu = ChestMenu.threeRows(1, playerInventory());
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND));
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
        Set<String> selection = new LinkedHashSet<>(Set.of(DIAMOND, IRON));
        assertEquals(List.of(0, 1), highlightedSlots(menu, selection));

        selection.remove(IRON);
        assertEquals(List.of(0), highlightedSlots(menu, selection));
        selection.clear();
        assertTrue(highlightedSlots(menu, selection).isEmpty());
    }
}
