package de.vali.itemfinder.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/** Client-side snapshots of inventories the player has actually opened. */
public final class ChestMemory {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Comparator<Position> POSITION_ORDER = Comparator.comparingInt(Position::x)
            .thenComparingInt(Position::y).thenComparingInt(Position::z);
    private static final Comparator<ChestRecord> RECORD_ORDER = Comparator.comparing(ChestRecord::dimension)
            .thenComparing(record -> record.positions().getFirst(), POSITION_ORDER);

    private final Path directory;
    private final List<ChestRecord> chests = new ArrayList<>();
    private Path worldFile;
    private boolean dirty;
    private boolean protectedCorruptFile;

    public ChestMemory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    /** Switching worlds always discards the previous world's in-memory state. */
    public synchronized void loadWorld(String worldKey) throws IOException {
        Objects.requireNonNull(worldKey, "worldKey");
        if (worldKey.isBlank()) {
            throw new IllegalArgumentException("A world key must not be blank");
        }
        chests.clear();
        dirty = false;
        protectedCorruptFile = false;
        worldFile = directory.resolve(hash(worldKey) + ".json");
        if (!Files.exists(worldFile)) {
            return;
        }

        String json = Files.readString(worldFile, StandardCharsets.UTF_8);
        try {
            Snapshot snapshot = GSON.fromJson(json, Snapshot.class);
            if (snapshot == null || snapshot.schemaVersion != SCHEMA_VERSION || snapshot.chests == null) {
                throw new IllegalArgumentException("Unsupported or missing memory schema");
            }
            List<ChestRecord> normalized = new ArrayList<>();
            for (ChestRecord record : snapshot.chests) {
                if (record == null || !validIdentifier(record.dimension()) || record.updatedAt() < 0) {
                    throw new IllegalArgumentException("Invalid chest record");
                }
                ChestRecord clean = new ChestRecord(record.dimension(), normalizePositions(record.positions()),
                        normalizeItems(record.items()), record.updatedAt());
                // Later snapshots win when an old file contains overlapping records.
                normalized.removeIf(previous -> overlaps(previous, clean.dimension(), clean.positions()));
                normalized.add(clean);
            }
            normalized.sort(RECORD_ORDER);
            chests.addAll(normalized);
        } catch (JsonParseException | IllegalArgumentException | NullPointerException exception) {
            chests.clear();
            preserveCorruptFile();
        }
    }

    public synchronized void remember(String dimension, List<Position> positions, Map<String, Integer> items) {
        requireLoaded();
        requireIdentifier(dimension);
        List<Position> canonicalPositions = normalizePositions(positions);
        Map<String, Integer> canonicalItems = normalizeItems(items);
        List<ChestRecord> overlapping = chests.stream()
                .filter(record -> overlaps(record, dimension, canonicalPositions)).toList();
        if (overlapping.size() == 1 && overlapping.getFirst().positions().equals(canonicalPositions)
                && overlapping.getFirst().items().equals(canonicalItems)) {
            return;
        }
        chests.removeIf(record -> overlaps(record, dimension, canonicalPositions));
        chests.add(new ChestRecord(dimension, canonicalPositions, canonicalItems, System.currentTimeMillis()));
        chests.sort(RECORD_ORDER);
        dirty = true;
    }

    /** Either remembered half identifies a chest, including after its size changes. */
    public synchronized boolean isKnownChest(String dimension, List<Position> positions) {
        return chests.stream().anyMatch(record -> overlaps(record, dimension, positions));
    }

    public synchronized List<ChestRecord> find(String dimension, String itemId) {
        if (!validIdentifier(itemId)) {
            return List.of();
        }
        return chests.stream().filter(record -> record.dimension().equals(dimension))
                .filter(record -> record.items().getOrDefault(itemId, 0) > 0).toList();
    }

    /** Find each chest once if it contains any of the selected items. */
    public synchronized List<ChestRecord> findAny(String dimension, Collection<String> itemIds) {
        List<String> validItemIds = itemIds.stream().filter(ChestMemory::validIdentifier).distinct().toList();
        if (validItemIds.isEmpty()) {
            return List.of();
        }
        return chests.stream().filter(record -> record.dimension().equals(dimension))
                .filter(record -> validItemIds.stream()
                        .anyMatch(itemId -> record.items().getOrDefault(itemId, 0) > 0)).toList();
    }

    public synchronized List<ChestRecord> records(String dimension) {
        return chests.stream().filter(record -> record.dimension().equals(dimension)).toList();
    }

    /** Forgetting either half of a double chest removes that chest's entire snapshot. */
    public synchronized void forgetAt(String dimension, Position position) {
        if (chests.removeIf(record -> record.dimension().equals(dimension) && record.positions().contains(position))) {
            dirty = true;
        }
    }

    public synchronized int count(String dimension) {
        return (int) chests.stream().filter(record -> record.dimension().equals(dimension)).count();
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    /** Write beside the destination, then replace it atomically where the filesystem supports it. */
    public synchronized void save() throws IOException {
        requireLoaded();
        if (!dirty) {
            return;
        }
        if (protectedCorruptFile) {
            throw new IOException("Cannot safely replace the unreadable chest memory file: " + worldFile);
        }
        Snapshot snapshot = new Snapshot();
        snapshot.schemaVersion = SCHEMA_VERSION;
        snapshot.chests = new ArrayList<>(chests);
        writeAtomically(directory, worldFile, GSON.toJson(snapshot));
        dirty = false;
    }

    private void preserveCorruptFile() {
        Path backup = worldFile.resolveSibling(worldFile.getFileName() + ".corrupt-" + UUID.randomUUID());
        try {
            Files.move(worldFile, backup);
        } catch (IOException exception) {
            // Refuse to overwrite the original if backup creation is blocked.
            protectedCorruptFile = true;
        }
    }

    private void requireLoaded() {
        if (worldFile == null) {
            throw new IllegalStateException("Load a world before remembering or saving chests");
        }
    }

    private static List<Position> normalizePositions(List<Position> positions) {
        Objects.requireNonNull(positions, "positions");
        TreeSet<Position> sorted = new TreeSet<>(POSITION_ORDER);
        for (Position position : positions) {
            sorted.add(Objects.requireNonNull(position, "position"));
        }
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("A chest must have at least one position");
        }
        return List.copyOf(sorted);
    }

    private static Map<String, Integer> normalizeItems(Map<String, Integer> items) {
        Objects.requireNonNull(items, "items");
        TreeMap<String, Integer> sorted = new TreeMap<>();
        items.forEach((itemId, count) -> {
            if (validIdentifier(itemId) && count != null && count > 0) {
                sorted.put(itemId, count);
            }
        });
        return Collections.unmodifiableMap(sorted);
    }

    private static boolean overlaps(ChestRecord record, String dimension, List<Position> positions) {
        return record.dimension().equals(dimension) && record.positions().stream().anyMatch(positions::contains);
    }

    private static boolean validIdentifier(String identifier) {
        return identifier != null && IDENTIFIER.matcher(identifier).matches();
    }

    private static void requireIdentifier(String identifier) {
        if (!validIdentifier(identifier)) {
            throw new IllegalArgumentException("Invalid registry identifier: " + identifier);
        }
    }

    private static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java must provide SHA-256", exception);
        }
    }

    static void writeAtomically(Path directory, Path target, String json) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "item-finder-", ".tmp");
        try {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A failed cleanup must not hide the original write failure.
            }
        }
    }

    public record Position(int x, int y, int z) {
    }

    public record ChestRecord(String dimension, List<Position> positions, Map<String, Integer> items, long updatedAt) {
        public ChestRecord {
            positions = List.copyOf(positions);
            items = Collections.unmodifiableMap(new TreeMap<>(items));
        }
    }

    private static final class Snapshot {
        private int schemaVersion;
        private List<ChestRecord> chests;
    }
}
