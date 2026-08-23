package dev.bukkit.ability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import org.bukkit.NamespacedKey;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BowArrowManager;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;

/**
 * Scatter Volley — 5 arrows fanned ±28° in front of the caster. No homing,
 * each arrow pierces up to 5 distinct enemies then expires. Close-range design,
 * arrows despawn after ~30 ticks if no hit.
 */
public class BukkitTriVolleyEffect extends Effect {

    private static final int VOLLEY_COUNT = 5;
    private static final int VOLLEY_PIERCE = 5;
    private static final double VOLLEY_SPEED = 1.9;
    private static final double VOLLEY_SPREAD_DEG = 14.0; // step between arrows => ±28°
    private static final double VOLLEY_DAMAGE_SCALE = 0.85;
    private static final int VOLLEY_LIFETIME_TICKS = 30;

    public static final NamespacedKey VOLLEY_KEY = new NamespacedKey("project_d", "tri_volley");
    public static final NamespacedKey HOMING_KEY = new NamespacedKey("project_d", "tri_homing");

    // Per-arrow pierce tracking: arrow UUID -> set of hit entity UUIDs
    private static final Map<UUID, Set<UUID>> PIERCE_HITS = new HashMap<>();
    private static final Random RAND = new Random();

    public BukkitTriVolleyEffect(String cooldownKey) {
        super(null, 0L, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) return;
        if (!(casterEntity instanceof Player)) {
            // mobs: fallback single forward
            spawnVolley(caster, casterEntity, cooldownSink);
            return;
        }
        spawnVolley(caster, casterEntity, cooldownSink);
        if (cooldownSink != null) cooldownSink.startCooldown();
    }

    private void spawnVolley(RPGEntity caster, LivingEntity casterEntity, CooldownSink sink) {
        Location eye = casterEntity.getEyeLocation();
        Vector baseDir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null) return;

        // Capture damage at cast time like BowArrowManager
        double baseDamage = 0;
        if (caster != null) {
            baseDamage = caster.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, System.currentTimeMillis())
                    * caster.getProjectileDamageMultiplier() * VOLLEY_DAMAGE_SCALE;
        }

        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 0.9f, 1.25f);
        world.playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 0.6f, 1.4f);
        world.spawnParticle(Particle.CRIT, eye.clone().add(baseDir.clone().multiply(0.6)), 12, 0.15, 0.15, 0.15, 0.2);

        for (int i = 0; i < VOLLEY_COUNT; i++) {
            int offsetIndex = i - VOLLEY_COUNT / 2; // -2..2
            double yawDeg = offsetIndex * VOLLEY_SPREAD_DEG + RAND.nextDouble(-3.0, 3.0);
            double pitchJitter = RAND.nextDouble(-2.0, 2.0);
            Vector dir = rotateYawPitch(baseDir.clone(), yawDeg, pitchJitter).normalize();
            double speed = VOLLEY_SPEED + RAND.nextDouble(-0.15, 0.25);
            Location spawn = eye.clone().add(dir.clone().multiply(0.3));
            Arrow arrow = world.spawnArrow(spawn, dir, (float) speed, 6.0f);
            arrow.setShooter(casterEntity);
            arrow.setCritical(false);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setGlowing(false);
            // Mark as volley for pierce handling and to exempt from BowArrowManager bounce consume check
            arrow.getPersistentDataContainer().set(VOLLEY_KEY, PersistentDataType.BOOLEAN, true);
            arrow.getPersistentDataContainer().set(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, baseDamage);
            arrow.getPersistentDataContainer().set(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN, false);
            // trail distinct from hunter (violet-pink)
            startVolleyTrail(arrow);
            // lifetime despawn
            scheduleDespawn(arrow, VOLLEY_LIFETIME_TICKS);
            // piercing handled via follow-up ProjectileHitEvent in TriVolleyBehavior
        }
    }

    private void startVolleyTrail(Arrow arrow) {
        if (arrow == null || DMain.getInstance() == null) return;
        Color col = Color.fromRGB(0xD946FF);
        Particle.DustOptions dust = new Particle.DustOptions(col, 1.4f);
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) { cancel(); return; }
                Location l = arrow.getLocation(); World w = l.getWorld(); if (w==null){cancel();return;}
                w.spawnParticle(Particle.DUST, l, 2, 0.02,0.02,0.02,0, dust);
                if (arrow.getTicksLived() % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.05,0.05,0.05,0.02);
            }
        }.runTaskTimer(DMain.getInstance(), 1L, 1L);
    }

    private void scheduleDespawn(Arrow arrow, int ticks) {
        if (DMain.getInstance()==null) return;
        new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ if(arrow.isValid() && !arrow.isDead()) arrow.remove(); } }.runTaskLater(DMain.getInstance(), ticks);
    }

    private static Vector rotateYawPitch(Vector dir, double yawDeg, double pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        // yaw around Y
        double cosYaw = Math.cos(yawRad), sinYaw = Math.sin(yawRad);
        double x = dir.getX() * cosYaw - dir.getZ() * sinYaw;
        double z = dir.getX() * sinYaw + dir.getZ() * cosYaw;
        double y = dir.getY();
        Vector yawed = new Vector(x, y, z);
        // pitch around local X (approx): rotate around right vector
        // simplified: just add small vertical offset
        yawed.setY(yawed.getY() + Math.tan(pitchRad) * 0.1);
        return yawed.normalize();
    }

    private static LivingEntity resolveEntity(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity p) return p.getPlayer().orElse(null);
        if (org.bukkit.Bukkit.getServer()==null) return null;
        Entity e = org.bukkit.Bukkit.getEntity(caster.getUuid());
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public void cancel() {}

    // ---- Pierce bookkeeping for Tri volley/homing ----

    public static boolean isVolleyArrow(Entity e) {
        if (!(e instanceof Arrow a)) return false;
        Boolean v = a.getPersistentDataContainer().get(VOLLEY_KEY, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(v);
    }

    public static boolean isTriArrow(Entity e) {
        if (!(e instanceof Arrow a)) return false;
        Boolean h = a.getPersistentDataContainer().get(HOMING_KEY, PersistentDataType.BOOLEAN);
        Boolean v = a.getPersistentDataContainer().get(VOLLEY_KEY, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(h) || Boolean.TRUE.equals(v);
    }

    /** Called from TriVolleyBehavior's ProjectileHitEvent to implement pierce. Returns true if arrow should continue. */
    public static boolean handlePierceHit(Arrow arrow, LivingEntity hit) {
        if (arrow == null || hit == null) return false;
        UUID aid = arrow.getUniqueId();
        Set<UUID> hits = PIERCE_HITS.computeIfAbsent(aid, k -> new HashSet<>());
        if (hits.contains(hit.getUniqueId())) {
            return true; // already hit this entity before, allow continue
        }
        if (hits.size() >= VOLLEY_PIERCE) {
            PIERCE_HITS.remove(aid);
            arrow.remove();
            return false;
        }
        hits.add(hit.getUniqueId());
        if (hits.size() >= VOLLEY_PIERCE) {
            // reached pierce cap -> next hit will despawn, but keep for this hit
            // schedule removal after this tick so damage processes
            if (DMain.getInstance()!=null) new org.bukkit.scheduler.BukkitRunnable(){ @Override public void run(){ if(arrow.isValid()) arrow.remove(); PIERCE_HITS.remove(aid);} }.runTaskLater(DMain.getInstance(), 2L);
        }
        return true; // continue
    }

    public static void cleanupPierce(Arrow arrow) {
        if (arrow!=null) PIERCE_HITS.remove(arrow.getUniqueId());
    }
}
