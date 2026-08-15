package dev.bukkit.utils;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
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

    /** Ring points drawn per render; keeps the circle smooth without particle spam. */
    private static final int RING_POINTS = 32;

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

    /**
     * Renders the aura radius as a gently rotating circle on the ground around
     * the wearer: happy-villager particles along the ring with a heart accent
     * every few points. Runs every tick (cheap: ~40 particles) so the radius
     * is continuously visible while the set passive is on. {@link #tick} keeps
     * its own 1-second interval for the actual healing.
     */
    public static void renderRing(RPGEntity holder) {
        if (!(holder instanceof BukkitPlayerEntity auraHolder)) {
            return;
        }
        Optional<Player> holderPlayer = auraHolder.getPlayer();
        if (holderPlayer.isEmpty() || !holder.isAlive()) {
            return;
        }
        Player player = holderPlayer.get();
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 0.15, 0);

        // Slow rotation so the ring visibly spins instead of flickering in place.
        double angleOffset = Math.toRadians((System.currentTimeMillis() / 50) % 360);
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = angleOffset + 2 * Math.PI * i / RING_POINTS;
            double x = center.getX() + HEAL_RADIUS * Math.cos(angle);
            double z = center.getZ() + HEAL_RADIUS * Math.sin(angle);
            Location ringPoint = center.clone();
            ringPoint.setX(x);
            ringPoint.setZ(z);
            world.spawnParticle(Particle.HAPPY_VILLAGER, ringPoint, 1, 0, 0, 0, 0);
            if (i % 8 == 0) {
                world.spawnParticle(Particle.HEART, ringPoint.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
            }
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
