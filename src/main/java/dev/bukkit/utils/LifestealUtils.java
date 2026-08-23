package dev.bukkit.utils;

import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityHealEvent.HealReason;
import dev.core.stat.StatType;

/**
 * Lifesteal application for melee hits. The {@link StatType#LIFESTEAL} stat is
 * fraction-based (0.05 = 5%) like CRIT_CHANCE; a landed melee hit heals the
 * attacker for that fraction of the damage actually dealt (post-mitigation,
 * as returned by {@code RPGDamageResult#getDamage()}).
 *
 * <p>Deliberately scoped to the melee auto-attack sites in
 * {@code CombatListener}: ability, projectile and DoT damage never lifesteal.
 * The heal routes through {@code RPGEntity#healRPGEntity} with
 * {@link HealReason#LIFESTEAL} so it fires {@code RPGEntityHealEvent}, is
 * amplified by HEAL_AND_SHIELD_POWER and capped at max health like every
 * other heal in the system.</p>
 */
public final class LifestealUtils {

    private LifestealUtils() {
    }

    /**
     * Heal the attacker for their lifesteal fraction of {@code damageDealt}.
     * No-op when the attacker is null/dead, no damage landed or the stat is 0.
     *
     * @param attacker    the entity whose lifesteal stat applies (the healer)
     * @param damageDealt damage after mitigation that actually landed
     */
    public static void applyLifesteal(RPGEntity attacker, double damageDealt) {
        if (attacker == null || !attacker.isAlive() || damageDealt <= 0) {
            return;
        }
        double lifesteal = attacker.getStatEngineAdapter().getCurrentValue(StatType.LIFESTEAL,
                System.currentTimeMillis());
        if (lifesteal <= 0) {
            return;
        }
        double heal = damageDealt * lifesteal;
        if (heal <= 0) {
            return;
        }
        attacker.healRPGEntity(attacker, attacker, heal, HealReason.LIFESTEAL);
    }
}
