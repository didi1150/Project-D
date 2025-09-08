package dev.core.storage.database;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;

public class PlayerProgression {

    private final UUID playerId;
    private RPGClassType activeClass;
    private final Map<RPGClassType, PlayerClassProgression> progressions;

    public PlayerProgression(UUID playerId) {
        this.playerId = playerId;
        this.progressions = new HashMap<>();

        for (RPGClassType rpgClassType : RPGClassType.values()) {
            unlockClass(rpgClassType);
        }
    }

    public void unlockClass(RPGClassType type) {
        progressions.putIfAbsent(type, new PlayerClassProgression(type));
    }

    public PlayerClassProgression getProgression(RPGClassType type) {
        return progressions.get(type);
    }

    public void setActiveClass(RPGClassType type, StatManager statManager) {
        unlockClass(type);
        this.activeClass = type;

        statManager.clearAll();
        Map<StatType, Stat> baseStats = getProgression(type).getBaseStats();
        statManager.addAll(baseStats);
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
