package de.vali.itemfinder.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.vali.itemfinder.storage.ChestMemory;
import de.vali.itemfinder.storage.ChestMemory.ChestRecord;
import de.vali.itemfinder.storage.ChestMemory.Position;
import de.vali.itemfinder.storage.FinderConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "itemfinder";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ItemFinderClient instance;
    private final CaptureGate capture = new CaptureGate();
    // Track the open chest independently so item highlights work with memory disabled.
    private final CaptureGate openChest = new CaptureGate();
    private ChestMemory memory;
    private FinderConfig config;
    private String worldKey;
    private final Set<String> selectedItems = new LinkedHashSet<>();
    private KeyMapping searchKey;
    private KeyMapping captureKey;
    private long ticks;
    private boolean storageError;

    public static ItemFinderClient getInstance() { return instance; }

    @Override
    public void onInitializeClient() {
        instance = this;
        Path directory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        memory = new ChestMemory(directory.resolve("worlds"));
        try {
            config = FinderConfig.load(directory.resolve("config.json"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Item Finder settings", exception);
        }
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keys"));
        searchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.itemfinder.search", InputConstants.KEY_I, category));
        captureKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.itemfinder.capture", InputConstants.KEY_O, category));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> join(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flush());
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ChestHighlightRenderer.register();
    }

    private void join(Minecraft client) {
        flush();
        capture.clear();
        openChest.clear();
        clearSelection();
        if (client.getSingleplayerServer() != null) {
            worldKey = "singleplayer:" + client.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else if (client.getCurrentServer() != null) {
            worldKey = "server:" + client.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        } else if (client.getConnection() != null) {
            worldKey = "server:" + client.getConnection().getConnection().getRemoteAddress();
        } else {
            worldKey = null;
            return;
        }
        try {
            memory.loadWorld(worldKey);
        } catch (IOException exception) {
            LOGGER.error("Could not load chest memory", exception);
            worldKey = null;
            notifyPlayer("itemfinder.error.load");
        }
    }

    private void disconnect() {
        snapshotCurrentMenu();
        flush();
        capture.clear();
        openChest.clear();
        clearSelection();
        worldKey = null;
    }

    private void tick(Minecraft client) {
        ticks++;
        if (client.player == null || client.level == null || worldKey == null) return;
        while (captureKey.consumeClick()) toggleCapture();
        while (searchKey.consumeClick()) {
            if (client.gui.screen() == null) client.gui.setScreen(new ItemSearchScreen());
        }
        if (capture.hasActive()) {
            if (client.player.containerMenu.containerId == capture.menuId()) {
                snapshotCurrentMenu();
            } else {
                capture.clear();
                flush();
            }
        }
        if (openChest.hasActive() && client.player.containerMenu.containerId != openChest.menuId()) {
            openChest.clear();
        }
        if (ticks % 40 == 0) flush();
    }

    public boolean captureEnabled() { return config.captureEnabled(); }

    public void toggleCapture() {
        snapshotCurrentMenu();
        config.toggleCaptureEnabled();
        capture.clear();
        flush();
        saveConfig();
        notifyPlayer(captureEnabled() ? "itemfinder.capture.enabled" : "itemfinder.capture.disabled");
    }

    public boolean hideUnknownItems() { return config.hideUnknownItems(); }

    public void toggleHideUnknownItems() {
        config.toggleHideUnknownItems();
        saveConfig();
    }

    private void saveConfig() {
        try {
            config.save();
        } catch (IOException exception) {
            LOGGER.error("Could not save settings", exception);
            notifyPlayer("itemfinder.error.save");
        }
    }

    /** Called on the client thread before sending a block-use request. */
    public void onBlockUse(BlockPos position) {
        capture.offer(null);
        openChest.offer(null);
        Minecraft client = Minecraft.getInstance();
        if (worldKey == null || client.level == null || client.player == null
                || !client.level.getWorldBorder().isWithinBounds(position)) return;
        boolean spectator = client.gameMode != null && client.gameMode.isSpectator();
        // Vanilla skips the container interaction when sneaking with either hand occupied.
        if (!spectator && client.player.isSecondaryUseActive()
                && (!client.player.getMainHandItem().isEmpty() || !client.player.getOffhandItem().isEmpty())) return;
        BlockState state = client.level.getBlockState(position);
        if (!(state.getBlock() instanceof ChestBlock)) return;
        if (!spectator && ChestBlock.isChestBlockedAt(client.level, position)) return;
        List<Position> positions = new ArrayList<>();
        positions.add(positionOf(position));
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos neighbor = ChestBlock.getConnectedBlockPos(position, state);
            BlockState neighborState = client.level.getBlockState(neighbor);
            if (neighborState.getBlock() != state.getBlock()
                    || neighborState.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                    || !ChestBlock.getConnectedBlockPos(neighbor, neighborState).equals(position)) return;
            if (!spectator && ChestBlock.isChestBlockedAt(client.level, neighbor)) return;
            positions.add(positionOf(neighbor));
        }
        CaptureGate.Target target = new CaptureGate.Target(worldKey, dimension(), positions, ticks + 100);
        openChest.offer(target);
        if (captureEnabled()) capture.offer(target);
    }

    public void cancelPendingInteraction() {
        capture.offer(null);
        openChest.offer(null);
    }

    public void onMenuOpened(int containerId) {
        Minecraft client = Minecraft.getInstance();
        if (worldKey == null || client.level == null || client.player == null
                || !(client.player.containerMenu instanceof ChestMenu menu)
                || menu.containerId != containerId) {
            capture.clear();
            openChest.clear();
            return;
        }
        int slotCount = menu.getRowCount() * 9;
        openChest.open(worldKey, dimension(), containerId, slotCount, ticks);
        if (captureEnabled()) {
            capture.open(worldKey, dimension(), containerId, slotCount, ticks);
        } else {
            capture.clear();
        }
    }

    public void onMenuContents(int containerId) {
        openChest.initialize(containerId);
        if (!captureEnabled() || !capture.initialize(containerId)) return;
        snapshotCurrentMenu();
        flush();
    }

    public void onSlotUpdated(int containerId) {
        if (containerId == capture.menuId()) snapshotCurrentMenu();
    }

    /** Save the final visible inventory before the player closes the container. */
    public void onMenuClosing() {
        snapshotCurrentMenu();
        capture.clear();
        openChest.clear();
        flush();
    }

    public boolean isOpenChestMenu(ChestMenu menu) {
        Minecraft client = Minecraft.getInstance();
        return worldKey != null && client.level != null && client.player != null
            && client.player.containerMenu == menu
            && openChest.ready(worldKey, dimension(), menu.containerId) != null;
    }

    private void snapshotCurrentMenu() {
        Minecraft client = Minecraft.getInstance();
        if (!captureEnabled() || worldKey == null || client.level == null || client.player == null
                || !(client.player.containerMenu instanceof ChestMenu menu)) return;
        CaptureGate.Target target = capture.ready(worldKey, dimension(), menu.containerId);
        if (target == null || menu.getRowCount() * 9 != target.slotCount()) return;
        Map<String, Integer> items = new LinkedHashMap<>();
        // Only chest slots; the player's inventory and cursor are never included.
        for (int index = 0; index < target.slotCount(); index++) {
            ItemStack stack = menu.getSlot(index).getItem();
            if (!stack.isEmpty()) {
                items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        memory.remember(target.dimension(), target.positions(), items);
    }

    public Set<String> selectedItemIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selectedItems));
    }

    public boolean isItemSelected(String id) { return selectedItems.contains(id); }

    public void selectItem(String id) {
        Objects.requireNonNull(id, "id");
        selectedItems.clear();
        selectedItems.add(id);
    }

    public void toggleSelectedItem(String id) {
        Objects.requireNonNull(id, "id");
        if (!selectedItems.remove(id)) selectedItems.add(id);
    }

    public void clearSelection() { selectedItems.clear(); }

    public List<ChestRecord> matchingChests() {
        if (worldKey == null || selectedItems.isEmpty() || Minecraft.getInstance().level == null) return List.of();
        return memory.findAny(dimension(), selectedItemIds());
    }

    public int knownChestCount() {
        return worldKey == null || Minecraft.getInstance().level == null ? 0 : memory.count(dimension());
    }

    public int knownItemCount(String itemId) {
        if (worldKey == null || Minecraft.getInstance().level == null) return 0;
        return memory.find(dimension(), itemId).stream().mapToInt(record -> record.items().getOrDefault(itemId, 0)).sum();
    }

    public Map<String, Integer> knownItemCounts() {
        if (worldKey == null || Minecraft.getInstance().level == null) return Map.of();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ChestRecord record : memory.records(dimension())) {
            record.items().forEach((id, count) -> counts.merge(id, count, Integer::sum));
        }
        return Map.copyOf(counts);
    }

    private String dimension() { return Minecraft.getInstance().level.dimension().identifier().toString(); }
    private static Position positionOf(BlockPos position) { return new Position(position.getX(), position.getY(), position.getZ()); }

    private void flush() {
        if (memory == null || worldKey == null || !memory.isDirty()) return;
        try {
            memory.save();
            storageError = false;
        } catch (IOException exception) {
            if (!storageError) {
                LOGGER.error("Could not save chest memory", exception);
                notifyPlayer("itemfinder.error.save");
            }
            storageError = true;
        }
    }

    private void notifyPlayer(String key) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.player.sendOverlayMessage(Component.translatable(key));
    }
}
