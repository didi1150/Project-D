package dev.bukkit.game.boss;

import java.time.Instant;

/**
 * Describes a transient boss arena instance created from a template world.
 *
 * This record represents the single active arena instance on the server
 * (global scope). It no longer stores a `partyId`.
 */
public record BossArena(String bossId, String worldName, Instant createdAt, State state) {

    /**
     * Returns a copy of this arena with an updated state.
     */
    public BossArena withState(State state) {
        return new BossArena(bossId, worldName, createdAt, state);
    }

    public enum State {
        COPYING,
        LOADING,
        ACTIVE,
        DESTROYING
    }
}
