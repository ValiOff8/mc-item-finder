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
    void captureStartsDisabledAndTogglePersistsAcrossRestarts() throws IOException {
        Path file = directory.resolve("item-finder.json");
        FinderConfig config = FinderConfig.load(file);
        assertFalse(config.captureEnabled());
        assertTrue(config.toggleCaptureEnabled());
        config.save();
        assertTrue(FinderConfig.load(file).captureEnabled());
        config.setCaptureEnabled(false);
        config.save();
        assertFalse(FinderConfig.load(file).captureEnabled());
    }

    @Test
    void corruptConfigDisablesCaptureAndPreservesOriginal() throws IOException {
        Path file = directory.resolve("item-finder.json");
        Files.writeString(file, "broken config");
        FinderConfig config = assertDoesNotThrow(() -> FinderConfig.load(file));
        assertFalse(config.captureEnabled());
        config.save();
        assertFalse(FinderConfig.load(file).captureEnabled());
        try (var files = Files.list(directory)) {
            Path backup = files.filter(path -> path.getFileName().toString().contains(".corrupt-")).findFirst().orElseThrow();
            assertEquals("broken config", Files.readString(backup));
        }
    }
}
