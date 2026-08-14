package dev.bukkit.utils;

import java.util.Optional;

import org.bukkit.Particle;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.passive.SetPassive;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityHealEvent.HealReason;

/**
 * Support set passive ("HEAL_AURA"): every second the wearer heals nearby
 * non-ghost teammates. Invoked from {@code BukkitPlayerEntity.tick} so the aura
 * only ticks while the wearer is alive and stops as soon as the set is broken.
 */
public class HealAuraUtils {

    public static final String PASSIVE_ID = "HEAL_AURA";
    public static final double HEAL_RADIUS = 6.0;
    public static final double HEAL_AMOUNT = 4.0;
    public static final long HEAL_INTERVAL_MS = 1000;

    private HealAuraUtils() {
    }

    /**
     * Heals alive, non-ghost players within range of the aura holder.
     */
    public static void tick(RPGEntity holder) {
        if (!(holder instanceof BukkitPlayerEntity auraHolder)) {
            return;
        }
        Optional<Player> holderPlayer = auraHolder.getPlayer();
        if (holderPlayer.isEmpty() || !holder.isAlive()) {
            return;
        }

        double radiusSq = HEAL_RADIUS * HEAL_RADIUS;
        for (Player player : holderPlayer.get().getWorld().getPlayers()) {
            if (player.getUniqueId().equals(holder.getUuid())) {
                continue; // aura heals teammates, not the wearer
            }
            if (player.isDead() || !player.isOnline()
                    || EntityManager.getInstance().isGhost(player.getUniqueId())) {
                continue;
            }
            if (player.getLocation().distanceSquared(holderPlayer.get().getLocation()) > radiusSq) {
                continue;
            }

            EntityManager.getInstance().getEntity(player.getUniqueId())
                    .filter(RPGEntity::isAlive)
                    .filter(target -> target.getHealth() < target.getMaxHealth())
                    .ifPresent(target -> {
                        holder.healRPGEntity(holder, target, HEAL_AMOUNT, HealReason.SPELL);
                        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.2, 0), 1);
                    });
        }
    }

    /** Marker so the registry can resolve the passive id from config. */
    public static final SetPassive MARKER = new SetPassive() {
        @Override
        public String getId() {
            return PASSIVE_ID;
        }
    };
}
