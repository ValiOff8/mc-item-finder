package de.vali.itemfinder.client;

import de.vali.itemfinder.storage.ChestMemory;
import de.vali.itemfinder.storage.ChestMemory.Position;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ItemFinderClientTest {
    private static final String WORLD = "server:example";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String IRON = "minecraft:iron_ingot";
    private static final Position LEFT = new Position(10, 64, 20);
    private static final Position RIGHT = new Position(11, 64, 20);

    @TempDir
    Path directory;

    @BeforeAll
    static void initializeItems() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
            .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    private ChestMemory memory() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld(WORLD);
        return memory;
    }

    private static ChestMenu singleChest() {
        return ChestMenu.threeRows(1, new Inventory(null, new EntityEquipment()));
    }

    private static ChestMenu doubleChest() {
        return ChestMenu.sixRows(2, new Inventory(null, new EntityEquipment()));
    }

    private static CaptureGate.Target target(String dimension, Position... positions) {
        return new CaptureGate.Target(WORLD, dimension, List.of(positions), 100);
    }

    @Test
    void knownChestUpdatesReducedAndEmptyContentsWithRecordingDisabledAndPersistsThem() throws IOException {
        ChestMemory memory = memory();
        ChestMenu menu = singleChest();
        CaptureGate.Target target = target(OVERWORLD, LEFT);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 12));
        menu.getSlot(26).set(new ItemStack(Items.IRON_INGOT, 8));
        ItemFinderClient.rememberMenu(memory, target, menu, true);
        memory.save();

        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 5));
        ItemFinderClient.rememberMenu(memory, target, menu, false);
        assertEquals(Map.of(DIAMOND, 5, IRON, 8), memory.records(OVERWORLD).getFirst().items());
        memory.save();
        memory.loadWorld(WORLD);
        assertEquals(Map.of(DIAMOND, 5, IRON, 8), memory.records(OVERWORLD).getFirst().items());

        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(26).set(ItemStack.EMPTY);
        ItemFinderClient.rememberMenu(memory, target, menu, false);
        assertTrue(memory.findAny(OVERWORLD, List.of(DIAMOND, IRON)).isEmpty());
        memory.save();
        memory.loadWorld(WORLD);
        assertEquals(1, memory.count(OVERWORLD));
        assertTrue(memory.records(OVERWORLD).getFirst().items().isEmpty());
    }

    @Test
    void removedItemsInPlayerInventoryAndCursorAreExcludedFromKnownChestUpdates() throws IOException {
        ChestMemory memory = memory();
        ChestMenu menu = singleChest();
        CaptureGate.Target target = target(OVERWORLD, LEFT);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 12));
        menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 5));
        ItemFinderClient.rememberMenu(memory, target, menu, true);

        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(27).set(new ItemStack(Items.DIAMOND, 7));
        menu.getSlot(menu.slots.size() - 1).set(new ItemStack(Items.IRON_INGOT, 64));
        menu.setCarried(new ItemStack(Items.DIAMOND, 5));
        ItemFinderClient.rememberMenu(memory, target, menu, false);

        assertTrue(memory.find(OVERWORLD, DIAMOND).isEmpty());
        assertEquals(Map.of(IRON, 5), memory.records(OVERWORLD).getFirst().items());
    }

    @Test
    void unknownChestsAreNotRecordedWhileRecordingIsDisabledEvenWhenEmpty() throws IOException {
        ChestMemory memory = memory();
        ChestMenu menu = singleChest();
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 12));
        ItemFinderClient.rememberMenu(memory, target(OVERWORLD, LEFT), menu, false);
        assertEquals(0, memory.count(OVERWORLD));

        menu.getSlot(0).set(ItemStack.EMPTY);
        ItemFinderClient.rememberMenu(memory, target(OVERWORLD, LEFT), menu, false);
        ItemFinderClient.rememberMenu(memory, target(OVERWORLD, LEFT, RIGHT), doubleChest(), false);
        assertEquals(0, memory.count(OVERWORLD));
        assertFalse(memory.isDirty());
    }

    @Test
    void newlyRecordedChestContinuesUpdatingAfterRecordingIsDisabled() throws IOException {
        ChestMemory memory = memory();
        ChestMenu menu = singleChest();
        CaptureGate gate = new CaptureGate();
        gate.offer(target(OVERWORLD, LEFT));
        assertTrue(gate.open(WORLD, OVERWORLD, menu.containerId, 27, 1));
        assertTrue(gate.initialize(menu.containerId));
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 12));
        ItemFinderClient.rememberMenu(memory, gate.ready(WORLD, OVERWORLD, menu.containerId), menu, true);
        assertEquals(12, memory.records(OVERWORLD).getFirst().items().get(DIAMOND));

        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 3));
        ItemFinderClient.rememberMenu(memory, gate.ready(WORLD, OVERWORLD, menu.containerId), menu, false);
        assertEquals(3, memory.records(OVERWORLD).getFirst().items().get(DIAMOND));

        menu.getSlot(0).set(ItemStack.EMPTY);
        ItemFinderClient.rememberMenu(memory, gate.ready(WORLD, OVERWORLD, menu.containerId), menu, false);
        assertTrue(memory.find(OVERWORLD, DIAMOND).isEmpty());
    }

    @Test
    void chestKnowledgeDoesNotCrossDimensionsOrWorldsWhenRecordingIsDisabled() throws IOException {
        ChestMemory memory = memory();
        ChestMenu menu = singleChest();
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 4));
        ItemFinderClient.rememberMenu(memory, target(OVERWORLD, LEFT), menu, true);
        memory.save();

        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 9));
        ItemFinderClient.rememberMenu(memory, target(NETHER, LEFT), menu, false);
        assertEquals(0, memory.count(NETHER));
        assertEquals(4, memory.records(OVERWORLD).getFirst().items().get(DIAMOND));

        memory.loadWorld("server:other");
        ItemFinderClient.rememberMenu(memory,
            new CaptureGate.Target("server:other", OVERWORLD, List.of(LEFT), 100), menu, false);
        assertEquals(0, memory.count(OVERWORLD));
        memory.loadWorld(WORLD);
        assertEquals(4, memory.records(OVERWORLD).getFirst().items().get(DIAMOND));
    }

    @Test
    void knownDoubleChestUpdatesBothHalvesAndChangedSizeWithRecordingDisabled() throws IOException {
        ChestMemory memory = memory();
        ChestMenu menu = doubleChest();
        CaptureGate.Target target = target(OVERWORLD, LEFT, RIGHT);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 12));
        menu.getSlot(27).set(new ItemStack(Items.DIAMOND, 8));
        menu.getSlot(53).set(new ItemStack(Items.IRON_INGOT, 6));
        ItemFinderClient.rememberMenu(memory, target, menu, true);

        menu.getSlot(0).set(ItemStack.EMPTY);
        menu.getSlot(27).set(new ItemStack(Items.DIAMOND, 3));
        menu.getSlot(53).set(new ItemStack(Items.IRON_INGOT, 2));
        menu.getSlot(54).set(new ItemStack(Items.DIAMOND, 17));
        ItemFinderClient.rememberMenu(memory, target, menu, false);
        assertEquals(Map.of(DIAMOND, 3, IRON, 2), memory.records(OVERWORLD).getFirst().items());

        ChestMenu resized = singleChest();
        resized.getSlot(26).set(new ItemStack(Items.IRON_INGOT, 5));
        ItemFinderClient.rememberMenu(memory, target(OVERWORLD, LEFT), resized, false);
        assertEquals(1, memory.count(OVERWORLD));
        assertEquals(List.of(LEFT), memory.records(OVERWORLD).getFirst().positions());
        assertEquals(Map.of(IRON, 5), memory.records(OVERWORLD).getFirst().items());

        ItemFinderClient.rememberMenu(memory, target, menu, false);
        assertEquals(1, memory.count(OVERWORLD));
        assertEquals(List.of(LEFT, RIGHT), memory.records(OVERWORLD).getFirst().positions());
        assertEquals(Map.of(DIAMOND, 3, IRON, 2), memory.records(OVERWORLD).getFirst().items());
    }

    @Test
    void uninitializedOrMismatchedMenusDoNotOverwriteKnownContents() throws IOException {
        ChestMemory memory = memory();
        ChestMenu menu = singleChest();
        CaptureGate gate = new CaptureGate();
        gate.offer(target(OVERWORLD, LEFT));
        assertTrue(gate.open(WORLD, OVERWORLD, menu.containerId, 27, 1));
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 4));

        ItemFinderClient.rememberMenu(memory, gate.ready(WORLD, OVERWORLD, menu.containerId), menu, false);
        assertEquals(Map.of(DIAMOND, 4), memory.records(OVERWORLD).getFirst().items());
        assertTrue(gate.initialize(menu.containerId));
        ItemFinderClient.rememberMenu(memory,
            gate.ready(WORLD, OVERWORLD, menu.containerId), doubleChest(), false);
        assertEquals(Map.of(DIAMOND, 4), memory.records(OVERWORLD).getFirst().items());
    }
}
