package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.status.StatusEffectType;

/**
 * Shield Bash - the Bulwark's shift-right-click ability. Swings the shield
 * through the half-circle in front of the caster, stunning every enemy inside
 * for {@value #STUN_DURATION_MS} ms. Pure crowd control: no damage, no
 * knockback. The effect lives exactly as long as the stun window so it can
 * sparkle above the struck victims, then cancels itself.
 */
public class BukkitShieldBashEffect extends Effect {

    // ---- Geometry ------------------------------------------------------------
    public static final double BASH_RANGE = 3.0;
    /**
     * A half-circle is a 180-degree wedge: cos(90 deg) = 0, so any target not
     * strictly behind the caster is inside.
     */
    public static final double COS_HALF_ANGLE = 0.0;
    private static final int PARTICLE_PULSE_TICKS = 5;

    // ---- Crowd control -------------------------------------------------------
    public static final long STUN_DURATION_MS = 1500;

    // ---- Audio & particles ----------------------------------------------------
    private static final float BASH_VOLUME = 1.0f;
    private static final float BASH_PITCH = 0.8f;
    private static final double SHOCKWAVE_ANGLE_STEP_DEG = 15.0;

    private final List<LivingEntity> victims = new ArrayList<>();
    private LivingEntity casterEntity;
    private long lastPulse;

    public BukkitShieldBashEffect(String cooldownKey) {
        super(null, STUN_DURATION_MS + PARTICLE_PULSE_TICKS * 50L + 50, true, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            return;
        }
        victims.clear();
        lastPulse = 0;

        Location origin = casterEntity.getLocation();
        Vector forward = casterEntity.getEyeLocation().getDirection();
        forward.setY(0);
        if (forward.lengthSquared() == 0) {
            forward = new Vector(0, 0, -1);
        }
        forward.normalize();

        bashEnemies(caster, origin, forward);
        playBashVisuals(origin, forward);
        origin.getWorld().playSound(casterEntity, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.6f);
        cooldownSink.startCooldown();
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (lastPulse == 0) {
            lastPulse = now;
            return;
        }
        if (now - lastPulse < PARTICLE_PULSE_TICKS * 50L) {
            return;
        }
        lastPulse = now;
        victims.removeIf(v -> v == null || !v.isValid() || v.isDead());
        for (LivingEntity victim : victims) {
            World world = victim.getWorld();
            world.spawnParticle(Particle.ELECTRIC_SPARK, victim.getEyeLocation().add(0, 0.4, 0), 2, 0.15, 0.2, 0.15,
                    0.02);
        }
    }

    @Override
    public void cancel() {
        victims.clear();
        casterEntity = null;
    }

    /**
     * Whether a horizontal offset from the caster lies in the half-circle in front
     * of it. Pure math so it can be tested headless.
     */
    public static boolean inFrontHalfCircle(Vector forward, Vector offset, double range) {
        double dx = offset.getX();
        double dz = offset.getZ();
        double distSq = dx * dx + dz * dz;
        if (distSq > range * range) {
            return false;
        }
        if (distSq == 0) {
            return true;
        }
        double invDist = 1.0 / Math.sqrt(distSq);
        double dot = (dx * forward.getX() + dz * forward.getZ()) * invDist;
        return dot >= COS_HALF_ANGLE;
    }

    private static LivingEntity resolveEntity(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayer().orElse(null);
        }
        if (Bukkit.getServer() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(caster.getUuid());
        return entity instanceof LivingEntity living ? living : null;
    }

    private void bashEnemies(RPGEntity caster, Location origin, Vector forward) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(origin, BASH_RANGE, BASH_RANGE, BASH_RANGE)) {
            if (!(entity instanceof LivingEntity living) || living.getUniqueId().equals(caster.getUuid())) {
                continue;
            }
            if (living instanceof ArmorStand) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
                continue;
            }
            // PvE: the dungeon's monsters are the enemies, never other players.
            if (entity.getType() == EntityType.PLAYER) {
                continue;
            }
            Vector offset = living.getLocation().toVector().subtract(origin.toVector());
            offset.setY(0);
            if (!inFrontHalfCircle(forward, offset, BASH_RANGE)) {
                continue;
            }
            EntityManager.getInstance().getEntity(living.getUniqueId()).ifPresent(target -> {
                if (target.applyStatusEffect(StatusEffectType.STUNNED, STUN_DURATION_MS)) {
                    victims.add(living);
                    world.spawnParticle(Particle.CRIT, living.getEyeLocation().add(0, 0.3, 0), 12, 0.3, 0.3, 0.3, 0.05);
                }
            });
        }
    }

    private void playBashVisuals(Location origin, Vector forward) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(origin, Sound.ITEM_SHIELD_BLOCK, BASH_VOLUME, BASH_PITCH);
        world.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_CRIT, BASH_VOLUME, 1.2f);

        // The arc swipe: Sweep Attack renders its built-in arc facing the player.
        Location eye = origin.clone().add(0, 1.0, 0).add(forward.clone().multiply(0.4));
        world.spawnParticle(Particle.SWEEP_ATTACK, eye, 1, 0, 0, 0, 0);

        // Semicircular cloud shockwave, mirroring Smash's particle language.
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        for (double theta = -90.0; theta <= 90.0; theta += SHOCKWAVE_ANGLE_STEP_DEG) {
            double rad = Math.toRadians(theta);
            double dirX = forward.getX() * Math.cos(rad) + right.getX() * Math.sin(rad);
            double dirZ = forward.getZ() * Math.cos(rad) + right.getZ() * Math.sin(rad);
            Vector dir = new Vector(dirX, 0, dirZ).normalize().multiply(0.7);
            world.spawnParticle(Particle.CLOUD, origin.clone().add(0, 0.4, 0).add(dir), 0, dir.getX(), 0.05, dir.getZ(),
                    0.25);
        }
    }
}