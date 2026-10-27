package dev.core.progression;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.DefaultStats;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.loader.StatLoader;

public class PlayerProgression {

    private final UUID playerId;
    private RPGClassType activeClass;
    private final Map<RPGClassType, PlayerClassProgression> progressions;
    private final Set<String> unlockedItems;

    public PlayerProgression(UUID playerId) {
        this.playerId = playerId;
        this.progressions = new HashMap<>();
        this.unlockedItems = new HashSet<>();

        for (RPGClassType rpgClassType : RPGClassType.values()) {
            unlockClass(rpgClassType);
        }
    }

    public void unlockClass(RPGClassType type) {
        progressions.putIfAbsent(type, new PlayerClassProgression(type));
    }

    /** Adds a reward-only RPG item to this player's permanent unlock set. */
    public boolean unlockItem(String itemId) {
        return itemId != null && !itemId.isBlank() && unlockedItems.add(itemId);
    }

    public boolean isItemUnlocked(String itemId) {
        return itemId != null && unlockedItems.contains(itemId);
    }

    public Set<String> getUnlockedItems() {
        return Set.copyOf(unlockedItems);
    }

    public void replaceUnlockedItems(Collection<String> itemIds) {
        unlockedItems.clear();
        if (itemIds == null) {
            return;
        }
        for (String itemId : itemIds) {
            if (itemId != null && !itemId.isBlank()) {
                unlockedItems.add(itemId);
            }
        }
    }

    public PlayerClassProgression getProgression(RPGClassType type) {
        return progressions.get(type);
    }

    public void setActiveClass(RPGClassType type, StatManager statManager) {
        unlockClass(type);
        this.activeClass = type;

        Map<StatType, Stat> stats;
        if (type == RPGClassType.NONE) {
            // No class: keep only the universal base stat set.
            stats = DefaultStats.getDefaultStats();
        } else {
            // Default (NONE) base + class bonus scaled by level.
            stats = StatLoader.mergeStats(DefaultStats.getDefaultStats(), getProgression(type).getBonusStats());
        }

        statManager.clearAll();
        statManager.addAll(stats);
    }

    public RPGClassType getActiveClass() {
        return activeClass;
    }

    public Map<RPGClassType, PlayerClassProgression> getAllProgressions() {
        return new HashMap<>(progressions);
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
