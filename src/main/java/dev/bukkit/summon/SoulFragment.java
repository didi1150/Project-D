package dev.bukkit.summon;

import org.bukkit.entity.EntityType;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;

/**
 * A captured soul: the kind of mob it came from, the tier it was captured at,
 * and the id of the {@link dev.core.entity.mob.MobDefinition} it was spawned
 * from ({@code null} for legacy souls captured before the definition id was
 * recorded). Dropped when a player (or their summon) kills a dungeon mob and
 * stored in the Soul Tome until the owner summons the mob. Immutable, so it is
 * safe to store in the tome's persistent data or serialize into a dropped
 * item's PDC.
 */
public record SoulFragment(EntityType mobType, SpawnTier tier, String definitionId) {
}