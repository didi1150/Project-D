package dev.bukkit.game.boss.stalactite;

/**
 * Lifecycle of a single stalactite owned by
 * {@link dev.bukkit.entity.boss.BukkitStalactiteEntity}.
 * <p>
 * GROWING is invulnerable (scale 0 -> max), ACTIVE is damageable, FALLING is
 * invulnerable and animates to ground on death.
 */
public enum StalactiteLifecycle {
    GROWING, ACTIVE, FALLING
}
