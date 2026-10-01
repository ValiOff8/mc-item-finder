package de.vali.itemfinder.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChestMemoryTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String DIAMOND = "minecraft:diamond";
    private static final String IRON = "minecraft:iron_ingot";
    private static final ChestMemory.Position LEFT = new ChestMemory.Position(10, 64, 20);
    private static final ChestMemory.Position RIGHT = new ChestMemory.Position(11, 64, 20);

    @TempDir
    Path directory;

    @Test
    void persistsSnapshotsAndKeepsWorldsAndDimensionsSeparate() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("server.example:25565");
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 7));
        memory.remember(NETHER, List.of(LEFT), Map.of("minecraft:gold_ingot", 3));
        memory.save();
        assertFalse(memory.isDirty());

        memory.loadWorld("singleplayer:another-world");
        assertEquals(0, memory.count(OVERWORLD));
        memory.remember(OVERWORLD, List.of(RIGHT), Map.of(DIAMOND, 1));
        memory.save();

        memory.loadWorld("server.example:25565");
        assertEquals(1, memory.count(OVERWORLD));
        assertEquals(7, memory.find(OVERWORLD, DIAMOND).getFirst().items().get(DIAMOND));
        assertEquals(LEFT, memory.find(OVERWORLD, DIAMOND).getFirst().positions().getFirst());
        assertTrue(memory.find(NETHER, DIAMOND).isEmpty());
        assertEquals(1, memory.count(NETHER));

        ChestMemory reloaded = new ChestMemory(directory);
        reloaded.loadWorld("singleplayer:another-world");
        assertEquals(List.of(RIGHT), reloaded.find(OVERWORLD, DIAMOND).getFirst().positions());
        try (var files = Files.list(directory)) {
            assertTrue(files.allMatch(path -> path.getFileName().toString().matches("[0-9a-f]{64}\\.json")));
        }
    }

    @Test
    void loadingAnotherWorldDiscardsUnsavedState() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("first");
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 64));
        memory.loadWorld("second");
        assertEquals(0, memory.count(OVERWORLD));
        assertFalse(memory.isDirty());
    }

    @Test
    void emptySnapshotClearsSearchResultsAndPersistsEmptyChest() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("empty-chest");
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 9));
        memory.save();
        memory.remember(OVERWORLD, List.of(LEFT), Map.of());
        assertTrue(memory.find(OVERWORLD, DIAMOND).isEmpty());
        assertEquals(1, memory.count(OVERWORLD));
        memory.save();
        memory.loadWorld("empty-chest");
        assertTrue(memory.records(OVERWORLD).getFirst().items().isEmpty());
    }

    @Test
    void findsUnionOfSelectedItemsOnceAndOnlyWithinCurrentDimension() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("multiple-items");
        ChestMemory.Position both = new ChestMemory.Position(12, 64, 20);
        ChestMemory.Position unrelated = new ChestMemory.Position(13, 64, 20);
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 3));
        memory.remember(OVERWORLD, List.of(RIGHT), Map.of(IRON, 4));
        memory.remember(OVERWORLD, List.of(both), Map.of(DIAMOND, 5, IRON, 6));
        memory.remember(OVERWORLD, List.of(unrelated), Map.of("minecraft:stone", 7));
        memory.remember(NETHER, List.of(LEFT), Map.of(DIAMOND, 8, IRON, 9));

        List<ChestMemory.ChestRecord> matches = memory.findAny(OVERWORLD, List.of(DIAMOND, IRON, DIAMOND));
        assertEquals(List.of(List.of(LEFT), List.of(RIGHT), List.of(both)),
                matches.stream().map(ChestMemory.ChestRecord::positions).toList());
        assertTrue(matches.stream().allMatch(record -> record.dimension().equals(OVERWORLD)));
    }

    @Test
    void emptyInvalidAndUnmatchedSelectionsDoNotInterfereWithValidMatches() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("selection-edges");
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 3));
        memory.remember(OVERWORLD, List.of(RIGHT), Map.of(IRON, 4));

        assertTrue(memory.findAny(OVERWORLD, List.of()).isEmpty());
        assertTrue(memory.findAny(OVERWORLD, Arrays.asList("bad id", null)).isEmpty());
        assertTrue(memory.findAny(OVERWORLD, List.of("minecraft:emerald")).isEmpty());
        assertEquals(memory.find(OVERWORLD, DIAMOND), memory.findAny(OVERWORLD, List.of(DIAMOND)));
        assertEquals(memory.find(OVERWORLD, DIAMOND),
                memory.findAny(OVERWORLD, Arrays.asList("minecraft:emerald", "bad id", null, DIAMOND)));
    }

    @Test
    void updatedZeroCountsAndEmptySnapshotsRemoveMultiItemMatches() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("updated-selection");
        List<String> selection = List.of(DIAMOND, IRON);
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 3, IRON, 4));
        memory.remember(OVERWORLD, List.of(RIGHT), Map.of(IRON, 5));

        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 0, IRON, 4));
        assertEquals(2, memory.findAny(OVERWORLD, selection).size());
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 0, IRON, 0));
        assertEquals(List.of(List.of(RIGHT)), memory.findAny(OVERWORLD, selection).stream()
                .map(ChestMemory.ChestRecord::positions).toList());
        memory.remember(OVERWORLD, List.of(RIGHT), Map.of());
        assertTrue(memory.findAny(OVERWORLD, selection).isEmpty());
    }

    @Test
    void mergesDoubleChestsRegardlessOfPositionOrderAndHandlesResizing() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("double-chests");
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 1));
        memory.remember(OVERWORLD, List.of(RIGHT), Map.of(DIAMOND, 2));
        assertEquals(2, memory.count(OVERWORLD));
        memory.remember(OVERWORLD, List.of(RIGHT, LEFT, RIGHT), Map.of(DIAMOND, 3));
        assertEquals(1, memory.count(OVERWORLD));
        assertEquals(List.of(LEFT, RIGHT), memory.records(OVERWORLD).getFirst().positions());
        memory.save();
        memory.remember(OVERWORLD, List.of(LEFT, RIGHT), Map.of(DIAMOND, 3));
        assertFalse(memory.isDirty());

        memory.remember(OVERWORLD, List.of(RIGHT), Map.of("minecraft:emerald", 5));
        assertEquals(1, memory.count(OVERWORLD));
        assertTrue(memory.find(OVERWORLD, DIAMOND).isEmpty());
        assertEquals(List.of(RIGHT), memory.records(OVERWORLD).getFirst().positions());
        memory.forgetAt(OVERWORLD, RIGHT);
        assertEquals(0, memory.count(OVERWORLD));
    }

    @Test
    void forgettingEitherDoubleChestHalfDoesNotAffectAnotherDimension() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("forget");
        memory.remember(OVERWORLD, List.of(LEFT, RIGHT), Map.of(DIAMOND, 5));
        memory.remember(NETHER, List.of(LEFT), Map.of(DIAMOND, 6));
        memory.forgetAt(OVERWORLD, RIGHT);
        assertTrue(memory.find(OVERWORLD, DIAMOND).isEmpty());
        assertEquals(1, memory.find(NETHER, DIAMOND).size());
        memory.save();
        memory.loadWorld("forget");
        assertEquals(0, memory.count(OVERWORLD));
        assertEquals(1, memory.count(NETHER));
    }

    @Test
    void onlyKeepsPositiveCountsWithValidIdentifiersAndDefendsSnapshots() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("sanitization");
        Map<String, Integer> items = new HashMap<>();
        items.put(DIAMOND, 2);
        items.put("minecraft:air", 0);
        items.put("minecraft:dirt", -1);
        items.put("Minecraft:Invalid", 3);
        items.put("not an identifier", 4);
        items.put(null, 8);
        items.put("minecraft:stone", null);
        memory.remember(OVERWORLD, List.of(LEFT), items);
        items.put(DIAMOND, 999);
        ChestMemory.ChestRecord record = memory.records(OVERWORLD).getFirst();
        assertEquals(Map.of(DIAMOND, 2), record.items());
        assertThrows(UnsupportedOperationException.class, () -> record.items().put(DIAMOND, 0));
        assertThrows(UnsupportedOperationException.class, () -> record.positions().clear());
        assertThrows(UnsupportedOperationException.class, () -> memory.records(OVERWORLD).clear());
        assertTrue(memory.find(OVERWORLD, "bad id").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> memory.remember(OVERWORLD, List.of(), Map.of()));
    }

    @Test
    void corruptJsonIsPreservedBeforeNewSnapshotsAreWritten() throws IOException {
        ChestMemory memory = new ChestMemory(directory);
        memory.loadWorld("corrupt");
        memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 1));
        memory.save();
        Path original;
        try (var files = Files.list(directory)) {
            original = files.findFirst().orElseThrow();
        }
        String corruptJson = "{broken JSON";
        Files.writeString(original, corruptJson);
        assertDoesNotThrow(() -> memory.loadWorld("corrupt"));
        assertEquals(0, memory.count(OVERWORLD));
        Path backup;
        try (var files = Files.list(directory)) {
            backup = files.filter(path -> path.getFileName().toString().contains(".corrupt-")).findFirst().orElseThrow();
        }
        assertEquals(corruptJson, Files.readString(backup));
        memory.remember(OVERWORLD, List.of(RIGHT), Map.of(DIAMOND, 4));
        memory.save();
        memory.loadWorld("corrupt");
        assertEquals(4, memory.find(OVERWORLD, DIAMOND).getFirst().items().get(DIAMOND));
        assertEquals(corruptJson, Files.readString(backup));
    }

    @Test
    void unsupportedSchemaAndInvalidRecordsArePreserved() throws IOException {
        for (String json : List.of("{\"schemaVersion\":99,\"chests\":[]}",
                "{\"schemaVersion\":1,\"chests\":[null]}",
                "{\"schemaVersion\":1,\"chests\":[{\"dimension\":\"minecraft:overworld\",\"positions\":[],\"items\":{},\"updatedAt\":1}]}")) {
            ChestMemory memory = new ChestMemory(directory);
            memory.loadWorld("invalid-record");
            memory.remember(OVERWORLD, List.of(LEFT), Map.of(DIAMOND, 1));
            memory.save();
            Path original;
            try (var files = Files.list(directory)) {
                original = files.filter(path -> path.getFileName().toString().endsWith(".json")).findFirst().orElseThrow();
            }
            Files.writeString(original, json);
            assertDoesNotThrow(() -> memory.loadWorld("invalid-record"));
            assertEquals(0, memory.count(OVERWORLD));
            assertFalse(Files.exists(original));
            try (var files = Files.list(directory)) {
                assertTrue(files.anyMatch(path -> {
                    try {
                        return Files.readString(path).equals(json);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                }));
            }
        }
    }
}
