package dev.bukkit.summon;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import dev.core.game.dungeon.proceduralDungeon.util.SpawnTier;
import dev.core.stat.Stat;
import dev.core.stat.StatManager;
import dev.core.stat.StatType;
import dev.core.stat.impl.CombatStat;
import dev.core.stat.loader.StatLoader;

/**
 * Pure scaling/gating rules for the soul-summoning mechanic. Deliberately free
 * of Bukkit runtime dependencies so the rules are unit-testable in the core
 * test suite.
 *
 * <p>
 * Tier gating: a soul tier may only be captured when the Support player's level
 * is at least {@link SpawnTier#getMinLevel()}. Summon stats scale with the
 * soul's tier and the Support level (a level 10 Support can capture and field
 * stronger summons than a level 1 Support).
 */
public final class SummonStats {

    /** Souls the tome can hold at Support level 1. */
    private static final int BASE_CAPACITY = 2;
    /** Extra soul slot gained every this many Support levels beyond 1. */
    private static final int CAPACITY_STEP_LEVELS = 3;
    /** Maximum soul slots a tome can hold. */
    private static final int MAX_CAPACITY = 5;

    private SummonStats() {
    }

    /**
     * Party exploitation guard: Support levels 0/1 are treated as 1 so a brand
     * new player can always capture basic souls.
     */
    public static int effectiveLevel(int level) {
        return Math.max(1, level);
    }

    /** {@code true} when the Support player may capture a soul of the given tier. */
    public static boolean canCapture(int level, SpawnTier tier) {
        return tier == null || effectiveLevel(level) >= tier.getMinLevel();
    }

    /** Soul slots available at the given Support level. */
    public static int capacityForLevel(int level) {
        int effective = effectiveLevel(level) - 1;
        return Math.min(MAX_CAPACITY, BASE_CAPACITY + effective / CAPACITY_STEP_LEVELS);
    }

    /** The most permissive tier in a mob definition's spawn set (BASIC if none). */
    public static SpawnTier lowestTier(Set<SpawnTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return SpawnTier.BASIC;
        }
        SpawnTier lowest = null;
        for (SpawnTier tier : tiers) {
            if (lowest == null || tier.getMinLevel() < lowest.getMinLevel()) {
                lowest = tier;
            }
        }
        return lowest == null ? SpawnTier.BASIC : lowest;
    }

    /** 1 for BASIC, 2 for ADVANCED, 3 for ELITE. */
    public static int tierMultiplier(SpawnTier tier) {
        return tier == null ? 1 : tier.ordinal() + 1;
    }

    /**
     * Fallback stat block used only for legacy souls whose captured mob
     * definition is no longer known (removed from dungeon-mobs.yml). Modern
     * souls are rebuilt from the original {@code MobDefinition} with its real
     * stats. Health, damage and defenses scale with the tier; every stat also
     * grows with the Support level. Includes the full combat stat set the
     * damage pipeline reads (ATTACK_SPEED / CRIT_CHANCE / ...) so the fallback
     * summon can still fight.
     */
    public static StatManager buildStats(SpawnTier tier, int level) {
        int mult = tierMultiplier(tier);
        int effLevel = effectiveLevel(level);
        long now = System.currentTimeMillis();

        Map<StatType, Stat> stats = new HashMap<>();
        stats.put(StatType.HEALTH_MAX, new CombatStat("HEALTH_MAX", 100.0 * mult + 15.0 * (effLevel - 1)));
        stats.put(StatType.ATTACK_DAMAGE, new CombatStat("ATTACK_DAMAGE", 6.0 * mult + effLevel));
        stats.put(StatType.ATTACK_SPEED, new CombatStat("ATTACK_SPEED", 1.0 + (effLevel - 1) * 0.1));
        stats.put(StatType.CRIT_CHANCE, new CombatStat("CRIT_CHANCE", 5.0));
        stats.put(StatType.ARMOR, new CombatStat("ARMOR", 5.0 * mult + effLevel));
        stats.put(StatType.MAGIC_RESIST, new CombatStat("MAGIC_RESIST", 5.0 * mult + effLevel));
        stats.put(StatType.MOVE_SPEED, new CombatStat("MOVE_SPEED", 100));

        Map<StatType, Stat> synthesized = StatLoader.copyStats(stats);
        Stat resource = synthesized.get(StatType.HEALTH_RESOURCE);
        Stat max = synthesized.get(StatType.HEALTH_MAX);
        if (resource != null && max != null) {
            resource.modify(max.getCurrent(now) - resource.getCurrent(now));
        }
        return new StatManager(synthesized);
    }
}