package dev.bukkit.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.core.ability.AbilityAction;

/**
 * Example miniboss behavior for the hybrid config+Java model: the "Lost Adventurer"
 * (inspired by Hypixel Skyblock dungeon minibosses) keeps its stats/equipment in
 * {@code dungeon-mobs.yml} but gains Java-driven mechanics here:
 *
 * <ul>
 *   <li>{@link #onSpawn} — a glowing marker so the adventurer reads as special.</li>
 *   <li>{@link #onTick} — an enrage phase below 50% health: stays fast and casts its
 *       weapon ability on a custom cadence, independent of the config's
 *       {@code ability-cast-interval}.</li>
 *   <li>{@link #onDeath} — logs the kill and clears per-mob state.</li>
 * </ul>
 *
 * <p>
 * Per-mob state (the last ability-cast time) is keyed by entity UUID, since this
 * behavior is a shared singleton across all Lost Adventurers.
 */
public class LostAdventurerBehavior implements MobBehavior {

    private static final long ENRAGE_ABILITY_INTERVAL_MS = 2000L;

    private final Map<UUID, Long> lastAbilityCast = new ConcurrentHashMap<>();

    @Override
    public void onSpawn(MobRPGEntity mob, LivingEntity vanilla) {
        // Unique visual marker so the Lost Adventurer reads as a special miniboss.
        vanilla.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
    }

    @Override
    public void onTick(MobRPGEntity mob, LivingEntity vanilla, long now) {
        double max = mob.getMaxHealth();
        if (max <= 0 || mob.getHealth() / max >= 0.5) {
            return; // not enraged yet
        }

        // Enrage phase: stay fast while below half health.
        vanilla.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, false, false));

        // Cast the equipped weapon's ability on a custom cadence (ignores config interval).
        long last = lastAbilityCast.getOrDefault(vanilla.getUniqueId(), 0L);
        if (now - last > ENRAGE_ABILITY_INTERVAL_MS) {
            lastAbilityCast.put(vanilla.getUniqueId(), now);
            mob.triggerAbility(AbilityAction.RIGHT_CLICK);
        }
    }

    @Override
    public void onDeath(MobRPGEntity mob, LivingEntity vanilla) {
        lastAbilityCast.remove(vanilla.getUniqueId());
        System.out.println("Lost Adventurer miniboss defeated: " + mob.getName());
    }
}
