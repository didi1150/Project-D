package dev.core.event.impl;

import java.util.UUID;

import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.Event;

/**
 * Fired whenever a player's class XP is changed through the progression
 * service. Covers reward XP grants, command-driven XP changes and any future
 * XP source.
 */
public class PlayerClassXpChangedEvent extends Event {

    private final UUID playerId;
    private final RPGClassType classType;
    private final int oldXp;
    private final int newXp;
    private final int oldLevel;
    private final int newLevel;

    public PlayerClassXpChangedEvent(UUID playerId, RPGClassType classType, int oldXp, int newXp, int oldLevel,
            int newLevel) {
        this.playerId = playerId;
        this.classType = classType;
        this.oldXp = oldXp;
        this.newXp = newXp;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public RPGClassType getClassType() {
        return classType;
    }

    public int getOldXp() {
        return oldXp;
    }

    public int getNewXp() {
        return newXp;
    }

    public int getOldLevel() {
        return oldLevel;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public int getXpDelta() {
        return newXp - oldXp;
    }

    public boolean isLevelUp() {
        return newLevel > oldLevel;
    }
}
