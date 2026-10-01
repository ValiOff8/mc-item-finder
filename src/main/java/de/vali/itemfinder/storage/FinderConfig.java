package de.vali.itemfinder.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** The recording switch is deliberately off until the player enables it. */
public final class FinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private boolean captureEnabled;
    private boolean protectedCorruptFile;

    public FinderConfig(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public static FinderConfig load(Path file) throws IOException {
        FinderConfig config = new FinderConfig(file);
        if (!Files.exists(config.file)) {
            return config;
        }
        String json = Files.readString(config.file, StandardCharsets.UTF_8);
        try {
            ConfigData data = GSON.fromJson(json, ConfigData.class);
            if (data == null || data.schemaVersion != 1 || data.captureEnabled == null) {
                throw new IllegalArgumentException("Invalid item finder configuration");
            }
            config.captureEnabled = data.captureEnabled;
        } catch (JsonParseException | IllegalArgumentException exception) {
            Path backup = config.file.resolveSibling(config.file.getFileName() + ".corrupt-" + UUID.randomUUID());
            try {
                Files.move(config.file, backup);
            } catch (IOException exceptionDuringBackup) {
                config.protectedCorruptFile = true;
            }
        }
        return config;
    }

    public boolean captureEnabled() {
        return captureEnabled;
    }

    public void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
    }

    public boolean toggleCaptureEnabled() {
        captureEnabled = !captureEnabled;
        return captureEnabled;
    }

    public void save() throws IOException {
        if (protectedCorruptFile) {
            throw new IOException("Cannot safely replace the unreadable configuration: " + file);
        }
        ConfigData data = new ConfigData();
        data.schemaVersion = 1;
        data.captureEnabled = captureEnabled;
        ChestMemory.writeAtomically(file.getParent(), file, GSON.toJson(data));
    }

    private static final class ConfigData {
        private int schemaVersion;
        private Boolean captureEnabled;
    }
}
