package de.vali.itemfinder.client;

import de.vali.itemfinder.storage.ChestMemory.Position;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureGateTest {
    private static final String WORLD = "server:example";
    private static final String DIMENSION = "minecraft:overworld";
    private final CaptureGate gate = new CaptureGate();

    private void offerSingle() {
        gate.offer(new CaptureGate.Target(WORLD, DIMENSION, List.of(new Position(1, 2, 3)), 100));
    }

    @Test void unopenedAndUninitializedMenusNeverGetSaved() {
        assertFalse(gate.open(WORLD, DIMENSION, 1, 27, 5));
        assertFalse(gate.initialize(1));
        offerSingle();
        assertTrue(gate.open(WORLD, DIMENSION, 1, 27, 5));
        assertNull(gate.ready(WORLD, DIMENSION, 1));
        assertFalse(gate.initialize(2));
        assertTrue(gate.initialize(1));
        assertNotNull(gate.ready(WORLD, DIMENSION, 1));
    }

    @Test void interactionExpiresAndIsConsumedByUnrelatedMenu() {
        offerSingle();
        assertFalse(gate.open(WORLD, DIMENSION, 1, 27, 101));
        offerSingle();
        assertFalse(gate.open(WORLD, DIMENSION, 1, 9, 5));
        assertFalse(gate.open(WORLD, DIMENSION, 2, 27, 6));
    }

    @Test void doubleChestNeedsFiftyFourSlots() {
        List<Position> positions = List.of(new Position(1, 2, 3), new Position(2, 2, 3));
        gate.offer(new CaptureGate.Target(WORLD, DIMENSION, positions, 100));
        assertFalse(gate.open(WORLD, DIMENSION, 1, 27, 5));
        gate.offer(new CaptureGate.Target(WORLD, DIMENSION, positions, 100));
        assertTrue(gate.open(WORLD, DIMENSION, 2, 54, 5));
        gate.initialize(2);
        assertEquals(positions, gate.ready(WORLD, DIMENSION, 2).positions());
    }

    @Test void worldAndDimensionMustMatch() {
        offerSingle();
        assertFalse(gate.open("server:other", DIMENSION, 1, 27, 5));
        offerSingle();
        assertTrue(gate.open(WORLD, DIMENSION, 1, 27, 5));
        gate.initialize(1);
        assertNull(gate.ready(WORLD, "minecraft:the_nether", 1));
        assertNull(gate.ready("server:other", DIMENSION, 1));
    }

    @Test void closingDropsPendingAndActiveCaptures() {
        offerSingle();
        gate.clear();
        assertFalse(gate.open(WORLD, DIMENSION, 1, 27, 5));
        offerSingle();
        gate.open(WORLD, DIMENSION, 1, 27, 5);
        gate.initialize(1);
        gate.clear();
        assertNull(gate.ready(WORLD, DIMENSION, 1));
        assertFalse(gate.initialize(1));
    }
}
