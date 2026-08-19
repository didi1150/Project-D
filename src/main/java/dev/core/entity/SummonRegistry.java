package dev.core.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which entities are player-owned summons (summon UUID → owner UUID).
 * The source of truth for the allied-team damage/ targeting checks.
 *
 * <p>
 * Registration order is preserved (queue/FIFO) so owner lists come out in
 * spawn order — oldest first — which store-back and dismissal flows rely on.
 */
public class SummonRegistry {

    private static SummonRegistry instance;

    private final Map<UUID, UUID> summons = new ConcurrentHashMap<>();
    private final List<UUID> order = Collections.synchronizedList(new ArrayList<>());

    public static SummonRegistry getInstance() {
        if (instance == null) {
            instance = new SummonRegistry();
        }
        return instance;
    }

    public void register(UUID owner, UUID summonUuid) {
        order.add(summonUuid);
        summons.put(summonUuid, owner);
    }

    public boolean isSummon(UUID uuid) {
        return uuid != null && summons.containsKey(uuid);
    }

    /** The owning player's UUID, or null if not a summon. */
    public UUID getOwner(UUID summonUuid) {
        return summons.get(summonUuid);
    }

/** The owner's summons in spawn order (FIFO: oldest first). */
    public List<UUID> getSummons(UUID owner) {
        List<UUID> owned = new ArrayList<>();
        synchronized (order) {
            for (UUID summonId : order) {
                if (owner.equals(summons.get(summonId))) {
                    owned.add(summonId);
                }
            }
        }
        return owned;
    }

    /** Snapshot of every registered summon UUID, regardless of owner. */
    public List<UUID> allSummonIds() {
        synchronized (order) {
            return new ArrayList<>(order);
        }
    }

    public void unregister(UUID summonUuid) {
        order.remove(summonUuid);
        summons.remove(summonUuid);
    }

    public void clearAll() {
        summons.clear();
        order.clear();
    }
}