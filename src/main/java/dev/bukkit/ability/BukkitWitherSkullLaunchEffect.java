package dev.bukkit.ability;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import dev.bukkit.DMain;
import dev.bukkit.ability.behavior.WitherSkullOrbitBehavior;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;
import dev.core.status.StatusEffectType;

/**
 * Active effect for the Wither Skull Launch ability. The player aims at an
 * orbiting skull and left-clicks to punch it, launching it as a raycast
 * projectile in the player's look direction. On impact, deals AoE magic damage
 * and applies a wither DoT that scales with HEAL_AND_SHIELD_POWER.
 */
public class BukkitWitherSkullLaunchEffect extends Effect {

    private static final double LAUNCH_SPEED = 1.5;
    private static final int MAX_TRAVEL_TICKS = 20;
    private static final double EXPLOSION_RADIUS = 3.0;
    private static final double EXPLOSION_BASE_DAMAGE = 15.0;
    private static final double EXPLOSION_AD_RATIO = 0.2;
    private static final double WITHER_BASE_DAMAGE = 5.0;
    private static final long WITHER_DURATION_MS = 4000L;
    private static final double MAX_REACH = 6.0;

    public BukkitWitherSkullLaunchEffect(String cooldownKey) {
        super(null, 0L, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        Player player = resolvePlayer(caster);
        if (player == null)
            return;
        UUID holderUUID = caster.getUuid();

        // Raycast: find the orbiting skull the player is aiming at
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().clone().normalize();
        ItemDisplay skull = findAimedSkull(holderUUID, eye, dir);
        if (skull == null || !skull.isValid()) {
//            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.7f);
            return;
        }

        // Consume the aimed skull
        WitherSkullOrbitBehavior.consumeSkull(holderUUID, skull);

        // Launch from the skull's current orbital position
        Location skullLoc = skull.getLocation().clone();

        // Point skull forward (along launch direction)
        skull.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(WitherSkullOrbitBehavior.SCALE), new AxisAngle4f(0, 0, 0, 1)));
        skull.setBillboard(Display.Billboard.FIXED);

        World world = player.getWorld();
        world.playSound(skullLoc, Sound.ENTITY_WITHER_SKELETON_HURT, 1.0f, 1.2f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, skullLoc, 8, 0.2, 0.2, 0.2, 0.05);
        world.spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().clone().add(0, 1.0, 0), 1, 0.0, 0.0, 0.0, 0.0);

        // Launch projectile from the skull's orbital position
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (skull == null || !skull.isValid()) {
                    cancel();
                    return;
                }
                if (ticks > MAX_TRAVEL_TICKS) {
                    onImpact(skull.getLocation(), caster);
                    skull.remove();
                    cancel();
                    return;
                }

                Location next = skull.getLocation().clone().add(dir.clone().multiply(LAUNCH_SPEED));

                // Block collision check
                if (world.getBlockAt(next).getType() != Material.AIR
                        && !world.getBlockAt(next).getType().isInteractable()) {
                    onImpact(next, caster);
                    skull.remove();
                    cancel();
                    return;
                }

                // Entity collision check
                for (Entity ent : skull.getNearbyEntities(1.0, 1.0, 1.0)) {
                    if (!(ent instanceof LivingEntity le))
                        continue;
                    if (le.getUniqueId().equals(caster.getUuid()))
                        continue;
                    if (EntityManager.getInstance().isGhost(le.getUniqueId()))
                        continue;
                    if (!CombatRelation.isEnemy(caster, ent))
                        continue;
                    onImpact(skull.getLocation(), caster);
                    skull.remove();
                    cancel();
                    return;
                }

                // Teleport and trail
                skull.teleport(next);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, next, 1, 0.05, 0.05, 0.05, 0.02);
                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.SMOKE, next, 1, 0.02, 0.02, 0.02, 0.01);
                }

                ticks++;
            }
        }.runTaskTimer(DMain.getInstance(), 0L, 1L);
    }

    private void onImpact(Location impactLoc, RPGEntity caster) {
        World world = impactLoc.getWorld();
        if (world == null)
            return;

        // Explosion visual
        world.spawnParticle(Particle.EXPLOSION_EMITTER, impactLoc, 1, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, impactLoc, 30, 1.0, 0.5, 1.0, 0.05);
        world.spawnParticle(Particle.SMOKE, impactLoc, 15, 0.5, 0.5, 0.5, 0.03);
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        world.playSound(impactLoc, Sound.ENTITY_WITHER_HURT, 0.6f, 1.5f);

        // Compute damage from caster stats
        double healAndShieldPower = caster.getStatEngineAdapter().getCurrentValue(StatType.HEAL_AND_SHIELD_POWER,
                System.currentTimeMillis());
        double explosionDamage = EXPLOSION_BASE_DAMAGE + healAndShieldPower * EXPLOSION_AD_RATIO;

        // AoE damage + wither application
        for (Entity ent : world.getNearbyEntities(impactLoc, EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS)) {
            if (!(ent instanceof LivingEntity le))
                continue;
            if (le.getUniqueId().equals(caster.getUuid()))
                continue;
            if (EntityManager.getInstance().isGhost(le.getUniqueId()))
                continue;
            if (!CombatRelation.isEnemy(caster, ent))
                continue;

            // Deal explosion damage
            DamageUtils.damageEntity(le, explosionDamage, caster, DamageType.MAGIC);

            // Apply wither DoT via status effect system
            RPGEntity targetRpg = EntityManager.getInstance().getEntity(le.getUniqueId()).orElse(null);
            if (targetRpg != null) {
                targetRpg.getStatusEffectManager().apply(targetRpg, StatusEffectType.WITHER, WITHER_DURATION_MS, true,
                        WITHER_BASE_DAMAGE, caster.getUuid());
            }
        }
    }

    /**
     * Finds the orbiting skull the player is aiming at via ray-sphere test. Returns
     * the closest skull within tolerance and reach, or null.
     */
    private ItemDisplay findAimedSkull(UUID holderUUID, Location eye, Vector dir) {
        List<ItemDisplay> skulls = WitherSkullOrbitBehavior.getOrbitingSkulls(holderUUID);
        if (skulls.isEmpty())
            return null;

        ItemDisplay best = null;
        double bestDist = Double.MAX_VALUE;

        for (ItemDisplay skull : skulls) {
            if (skull == null || !skull.isValid())
                continue;
            Vector toSkull = skull.getLocation().add(0, 0.5, 0).toVector().subtract(eye.toVector());
            double dot = toSkull.dot(dir);
            if (dot < 0 || dot > MAX_REACH)
                continue;
            double perpDistSq = toSkull.lengthSquared() - dot * dot;
            double hitRadius = 0.5;
            if (perpDistSq > hitRadius * hitRadius)
                continue;
            if (dot < bestDist) {
                bestDist = dot;
                best = skull;
            }
        }
        return best;
    }

    private Player resolvePlayer(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity pe)
            return pe.getPlayer().orElse(null);
        if (Bukkit.getServer() == null)
            return null;
        var e = Bukkit.getEntity(caster.getUuid());
        return e instanceof Player p ? p : null;
    }

    @Override
    public void cancel() {
    }
}
