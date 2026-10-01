package de.vali.itemfinder.client;

import de.vali.itemfinder.storage.ChestMemory.Position;
import java.util.List;

/** Associates a real block interaction with its subsequent server menu. */
public final class CaptureGate {
    public record Target(String world, String dimension, List<Position> positions, long expiresAt) {
        public Target { positions = List.copyOf(positions); }
        public int slotCount() { return positions.size() * 27; }
    }

    private Target pending;
    private Target active;
    private int menuId = -1;
    private boolean initialized;

    public void offer(Target target) {
        pending = target;
    }

    public boolean open(String world, String dimension, int id, int slots, long now) {
        Target offered = pending;
        clear();
        if (offered == null || now > offered.expiresAt()
                || !offered.world().equals(world) || !offered.dimension().equals(dimension)
                || offered.slotCount() != slots || id < 1) {
            return false;
        }
        active = offered;
        menuId = id;
        return true;
    }

    public boolean initialize(int id) {
        if (active == null || id != menuId) return false;
        initialized = true;
        return true;
    }

    public Target ready(String world, String dimension, int id) {
        return initialized && active != null && menuId == id
                && active.world().equals(world) && active.dimension().equals(dimension)
                ? active : null;
    }

    public boolean hasActive() { return active != null; }
    public int menuId() { return menuId; }

    public void clear() {
        pending = null;
        active = null;
        menuId = -1;
        initialized = false;
    }
}
