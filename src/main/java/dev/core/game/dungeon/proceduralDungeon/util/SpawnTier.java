package dev.core.game.dungeon.proceduralDungeon.util;

public enum SpawnTier {
    BASIC(1),
    ADVANCED(5),
    ELITE(10);

    private final int minLevel;

    SpawnTier(int minLevel) {
        this.minLevel = minLevel;
    }

    public int getMinLevel() {
        return minLevel;
    }
}
