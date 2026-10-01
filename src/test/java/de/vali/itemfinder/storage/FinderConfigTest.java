package de.vali.itemfinder.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FinderConfigTest {
    @TempDir
    Path directory;

    @Test
    void settingsStartDisabledAndPersistIndependentlyAcrossRestarts() throws IOException {
        Path file = directory.resolve("item-finder.json");
        FinderConfig config = FinderConfig.load(file);
        assertFalse(config.captureEnabled());
        assertFalse(config.hideUnknownItems());
        assertTrue(config.toggleCaptureEnabled());
        assertTrue(config.toggleHideUnknownItems());
        config.save();
        FinderConfig reloaded = FinderConfig.load(file);
        assertTrue(reloaded.captureEnabled());
        assertTrue(reloaded.hideUnknownItems());
        config.setCaptureEnabled(false);
        config.save();
        reloaded = FinderConfig.load(file);
        assertFalse(reloaded.captureEnabled());
        assertTrue(reloaded.hideUnknownItems());
        reloaded.setHideUnknownItems(false);
        reloaded.save();
        FinderConfig reset = FinderConfig.load(file);
        assertFalse(reset.captureEnabled());
        assertFalse(reset.hideUnknownItems());
    }

    @Test
    void existingConfigKeepsRecordingEnabledAndDefaultsToShowingAllItems() throws IOException {
        Path file = directory.resolve("item-finder.json");
        String original = "{\"schemaVersion\":1,\"captureEnabled\":true}";
        Files.writeString(file, original);

        FinderConfig config = FinderConfig.load(file);
        assertTrue(config.captureEnabled());
        assertFalse(config.hideUnknownItems());
        assertEquals(original, Files.readString(file));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }

        config.setHideUnknownItems(true);
        config.save();
        FinderConfig migrated = FinderConfig.load(file);
        assertTrue(migrated.captureEnabled());
        assertTrue(migrated.hideUnknownItems());
    }

    @Test
    void corruptConfigDisablesCaptureAndPreservesOriginal() throws IOException {
        Path file = directory.resolve("item-finder.json");
        Files.writeString(file, "broken config");
        FinderConfig config = assertDoesNotThrow(() -> FinderConfig.load(file));
        assertFalse(config.captureEnabled());
        assertFalse(config.hideUnknownItems());
        config.save();
        assertFalse(FinderConfig.load(file).captureEnabled());
        try (var files = Files.list(directory)) {
            Path backup = files.filter(path -> path.getFileName().toString().contains(".corrupt-")).findFirst().orElseThrow();
            assertEquals("broken config", Files.readString(backup));
        }
    }
}
