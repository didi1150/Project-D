package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Smash: the caster slams the ground, sending a shockwave out in a
 * {@value #CONE_FULL_ANGLE_DEG}-degree cone aligned with the direction they
 * are looking. Every enemy standing inside the cone — including exactly on the
 * edge — is knocked back away from the impact and takes damage that scales
 * with the caster's ARMOR and MAGIC_RESIST stats.
 * <p>
 * Geometry: the cone is a section of a circle (a wedge) on the ground plane in
 * front of the caster: a horizontal angular sector of
 * {@value #CONE_FULL_ANGLE_DEG} degrees, {@value #CONE_RANGE} blocks deep. A
 * target is inside the wedge when its horizontal distance to the impact is at
 * most {@value #CONE_RANGE} and its horizontal offset is within the half-angle
 * of the look direction (the >= edge comparison keeps exactly-on-the-boundary
 * targets hittable).
 * <p>
 * Damage formula: {@code (BASE_DAMAGE + ARMOR * ARMOR_SCALE + MAGIC_RESIST *
 * MAGIC_RESIST_SCALE) * abilityDamageMultiplier}, dealt as TRUE damage so the
 * caster's defensive stats translate into raw damage instead of being filtered
 * through the target's defenses.
 * <p>
 * The shockwave is visualized two ways: dust particles burst at the impact and
 * billow at the expanding wavefront while it travels, and the ground itself
 * ripples — each ground block inside the wedge gets exactly one BlockDisplay
 * (identity transformation, so it is indistinguishable from the block it
 * mirrors). The wave travels through the ground only ONCE: a block rests until
 * the wavefront reaches it, climbs to its sine peak (a chirp wave whose short
 * period near the impact widens with distance) over a few ticks, then falls
 * back with gravity and is removed once it has landed. The ripple is anchored
 * to the impact spot, so it does not follow the caster.
 * <p>
 * The effect is single-instance per (caster, item instance) via the effect
 * manager and starts the ability's configured cooldown at cast.
 */
public class BukkitSmashEffect extends Effect {

    // ---- Cone geometry ------------------------------------------------------
    /** Full width of the shockwave cone, in degrees. */
    public static final double CONE_FULL_ANGLE_DEG = 45.0;
    /** Half-angle of the cone, in degrees (the wedge spans +/- this around the look). */
    public static final double CONE_HALF_ANGLE_DEG = CONE_FULL_ANGLE_DEG / 2.0;
    /** How far out the cone reaches, in blocks. */
    public static final double CONE_RANGE = 8.0;
    /** Vertical band above the impact that a target must stand in to be hit. */
    private static final double CONE_HEIGHT = 3.0;

    // ---- Damage --------------------------------------------------------------
    private static final double BASE_DAMAGE = 15.0;
    /** Damage per point of the caster's ARMOR stat. */
    private static final double ARMOR_SCALE = 1.5;
    /** Damage per point of the caster's MAGIC_RESIST stat. */
    private static final double MAGIC_RESIST_SCALE = 1.5;

    // ---- Knockback ------------------------------------------------------------
    /** Knockback strength at the impact (0 blocks away), blocks/tick. */
    private static final double KNOCKBACK_BASE = 0.85;
    /** Knockback falls off to this fraction at the far edge of the cone. */
    private static final double KNOCKBACK_FALLOFF = 0.45;
    /** Vertical hop applied with the knockback. */
    private static final double KNOCKBACK_UP = 0.32;

    // ---- Ripple visuals -------------------------------------------------------
    /** How long the wavefront takes to reach the far edge of the cone, in ticks. */
    private static final int RIPPLE_TICKS = 24;
    /** Ticks a lifted block takes to climb to its sine peak. */
    private static final int RISE_TICKS = 4;
    /** Downward acceleration of a falling block, in blocks/tick^2. */
    private static final double FALL_GRAVITY = 0.12;
    /** Peak height of the ground wave, in blocks. */
    private static final double RIPPLE_AMPLITUDE = 3.0;
    /** Wavelength of the ripple right at the impact, in blocks (short period). */
    private static final double RIPPLE_WAVELENGTH_MIN = 1.4;
    /**
     * How much the wavelength grows per block of distance from the impact, in
     * blocks: the ripple's period starts short next to the player and widens
     * toward the far edge of the cone.
     */
    private static final double RIPPLE_WAVELENGTH_GROWTH = 0.55;
    /**
     * Safety cap on concurrent ripple displays. The wedge is sampled per
     * ground block (at most one display per block), so the real count is the
     * number of floor blocks inside the cone (~25 at the configured range);
     * the cap only guards against pathological geometry.
     */
    private static final int MAX_RIPPLE_DISPLAYS = 80;

    /**
     * Resting center height above the floor, in blocks: just under the surface
     * (top at 0.95), so a resting display never coplanar-fights the ground
     * block it mirrors and stays hidden until the wave lifts it.
     */
    private static final double REST_OFFSET = 0.45;

    /**
     * One ground block in the wedge: the display riding on it plus its own
     * one-pass timeline. The wavefront reaches the block at
     * {@code arrivalTick}, the block then climbs to its sine peak
     * ({@code peak}) over {@link #RISE_TICKS} ticks and falls back with
     * {@link #FALL_GRAVITY} gravity, landing at {@code endTick}.
     */
    private static final class Ripple {
        final double x;
        final double z;
        final BlockDisplay display;
        final double arrivalTick;
        final double peak;
        final double endTick;

        Ripple(double x, double z, BlockDisplay display, double arrivalTick, double peak, double endTick) {
            this.x = x;
            this.z = z;
            this.display = display;
            this.arrivalTick = arrivalTick;
            this.peak = peak;
            this.endTick = endTick;
        }
    }

    private final List<Ripple> ripples = new ArrayList<>();
    private boolean cleanedUp;
    private World world;
    private Vector coneForward;
    private double impactX;
    private double impactZ;
    private double groundY;
    private int ticks;

    public BukkitSmashEffect(String cooldownKey) {
        // The configured duration only caps a wave that never settles (the
        // manager's emergency despawn); normally the effect ends on its own as
        // soon as the last block has landed and been removed.
        super(null, (RIPPLE_TICKS + RISE_TICKS + 10) * 50L + 100, true, cooldownKey);
    }

    @Override
    public boolean hasExpired(long now) {
        return cleanedUp || super.hasExpired(now);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            return;
        }
        this.cleanedUp = false;
        this.ticks = 0;
        this.world = casterEntity.getWorld();
        Location impact = casterEntity.getLocation();
        this.impactX = impact.getX();
        this.impactZ = impact.getZ();

        // The cone is aligned with the direction the caster is looking, but
        // flattened to the ground plane: looking down still slams in front.
        Vector forward = casterEntity.getEyeLocation().getDirection();
        forward.setY(0);
        if (forward.lengthSquared() == 0) {
            forward = new Vector(0, 0, -1);
        }
        this.coneForward = forward.normalize();

        // Damage + knockback resolve at the moment of impact.
        smashEnemies(caster, casterEntity, coneForward, impact);

        // The ground ripple rides on the floor under the impact point. A slam
        // with no ground beneath it (mid-air) still shows the dust shockwave.
        this.groundY = groundYAt(impact);
        if (!Double.isNaN(this.groundY)) {
            spawnRipple(impact, coneForward);
        }

        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
        impact.getWorld().playSound(impact, Sound.BLOCK_STONE_BREAK, 1.0f, 0.9f);
        impact.getWorld().spawnParticle(Particle.FALLING_DUST, impact.clone().add(0, 0.2, 0), 60, 1.5, 0.4, 1.5, 0.4,
                floorBlock(impact).getBlockData());

        cooldownSink.startCooldown();
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (cleanedUp || world == null) {
            return;
        }
        ticks++;
        animateRipple();
        spawnWavefrontDust();
        if (ripples.isEmpty()) {
            // Every block has risen, fallen back and been removed: the wave
            // has travelled through the ground exactly once.
            cleanup();
        }
    }

    @Override
    public void cancel() {
        cleanup();
    }

    private void cleanup() {
        if (cleanedUp) {
            return;
        }
        cleanedUp = true;
        for (Ripple ripple : ripples) {
            if (ripple.display.isValid()) {
                ripple.display.remove();
            }
        }
        ripples.clear();
    }

    // ---- Cone geometry --------------------------------------------------------

    /**
     * True when the horizontal offset (impact point -> target) lies inside or
     * exactly on the edge of the wedge: no farther than {@code range} blocks
     * and within {@code cosHalfAngle} of the (horizontal) forward direction.
     * The >= comparison keeps targets standing exactly on the boundary hittable.
     * Both vectors are treated as horizontal (their Y components are ignored).
     */
    public static boolean inConeWedge(Vector forward, Vector offset, double range, double cosHalfAngle) {
        double dx = offset.getX();
        double dz = offset.getZ();
        double distSq = dx * dx + dz * dz;
        if (distSq > range * range) {
            return false;
        }
        if (distSq == 0) {
            return true; // standing on the impact point itself
        }
        double invDist = 1.0 / Math.sqrt(distSq);
        double dot = (dx * forward.getX() + dz * forward.getZ()) * invDist;
        return dot >= cosHalfAngle;
    }

    /**
     * Resolves the Bukkit living entity backing a caster, so the slam works for
     * players AND mobs. Mobs keep their vanilla AI but cast abilities through an
     * {@code RPGMobEntity} wrapper that shares the vanilla entity's uuid, so
     * {@link Bukkit#getEntity(uuid)} resolves the living mob.
     */
    private static LivingEntity resolveEntity(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayer().orElse(null);
        }
        // Off-server (tests/headless): there is no entity registry, so no-op.
        if (Bukkit.getServer() == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(caster.getUuid());
        return entity instanceof LivingEntity living ? living : null;
    }

    // ---- Damage & knockback ---------------------------------------------------

    private void smashEnemies(RPGEntity caster, LivingEntity casterEntity, Vector forward, Location impact) {
        boolean casterIsPlayer = caster instanceof BukkitPlayerEntity;
        double cosHalfAngle = Math.cos(Math.toRadians(CONE_HALF_ANGLE_DEG));
        long now = System.currentTimeMillis();
        double armor = caster.getStatEngineAdapter().getCurrentValue(StatType.ARMOR, now);
        double magicResist = caster.getStatEngineAdapter().getCurrentValue(StatType.MAGIC_RESIST, now);
        double damage = smashDamage(armor, magicResist, caster.getAbilityDamageMultiplier());

        for (Entity entity : impact.getWorld().getNearbyEntities(impact, CONE_RANGE, CONE_HEIGHT, CONE_RANGE)) {
            if (!(entity instanceof LivingEntity living) || living.getUniqueId().equals(caster.getUuid())) {
                continue;
            }
            if (living instanceof ArmorStand) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
                continue;
            }
            // Players slam mobs; mobs slam players (mirrors Spinjitzu/Bonemerang).
            if (casterIsPlayer && entity.getType() == EntityType.PLAYER) {
                continue;
            }
            if (!casterIsPlayer && entity.getType() != EntityType.PLAYER) {
                continue;
            }
            double dy = living.getLocation().getY() - impact.getY();
            if (dy < -0.5 || dy > CONE_HEIGHT) {
                continue; // standing target: within a band above the impact
            }
            Vector offset = living.getLocation().toVector().subtract(impact.toVector());
            offset.setY(0);
            if (!inConeWedge(forward, offset, CONE_RANGE, cosHalfAngle)) {
                continue;
            }

            double distance = offset.length();
            double falloff = KNOCKBACK_FALLOFF + (1 - KNOCKBACK_FALLOFF) * (1 - distance / CONE_RANGE);
            Vector knock = distance == 0 ? forward.clone() : offset.clone().normalize();
            knock.multiply(KNOCKBACK_BASE * falloff);
            knock.setY(KNOCKBACK_UP);
            living.setVelocity(knock);

            EntityManager.getInstance().getEntity(entity.getUniqueId()).ifPresentOrElse(target -> {
                RPGDamageResult rpgDamage = target.dealRPGDamage(caster, target, damage, DamageType.TRUE);
                if (rpgDamage.getResult() != DamageResult.DENY) {
                    showDamageIndicator(living, rpgDamage.getDamage());
                    playHitSound(living, rpgDamage.getResult());
                }
            }, () -> {
                // Vanilla mob: damageMob fires an EntityDamageByEntityEvent that
                // CombatListener already renders an indicator for, so we must NOT
                // render again here or the indicator doubles.
                DamageUtils.damageMob(living, damage, casterEntity);
                playHitSound(living, DamageResult.NORMAL);
            });
        }
    }

    /**
     * The shockwave's raw damage: a flat base plus the caster's defensive
     * stats (ARMOR and MAGIC_RESIST) fed through their per-point scales, then
     * multiplied by the caster's ability-damage multiplier.
     */
    static double smashDamage(double armor, double magicResist, double abilityMultiplier) {
        return (BASE_DAMAGE + armor * ARMOR_SCALE + magicResist * MAGIC_RESIST_SCALE) * abilityMultiplier;
    }

    private void showDamageIndicator(LivingEntity le, double damage) {
        if (damage <= 0) {
            return;
        }
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.showTrueDamage(le.getLocation(), damage);
    }

    private void playHitSound(LivingEntity le, DamageResult result) {
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.playProjectileHitSound(le, result);
    }

    // ---- Ground ripple visuals -------------------------------------------------

    /**
     * Samples the floor block-by-block: every ground block whose center lies
     * inside the wedge gets exactly ONE display, cloned from that block's own
     * blockdata. The display uses the identity transformation (no scale, no
     * rotation) and rests buried just under the surface, so before the wave
     * reaches it — and after it lands — it is visually indistinguishable from
     * the ground block it mirrors. The number of displays therefore never
     * exceeds the number of ground blocks in the cone.
     */
    private void spawnRipple(Location impact, Vector forward) {
        double cosHalfAngle = Math.cos(Math.toRadians(CONE_HALF_ANGLE_DEG));
        double kFactor = 2 * Math.PI / RIPPLE_WAVELENGTH_GROWTH;
        int minX = (int) Math.floor(impactX - CONE_RANGE);
        int maxX = (int) Math.floor(impactX + CONE_RANGE);
        int minZ = (int) Math.floor(impactZ - CONE_RANGE);
        int maxZ = (int) Math.floor(impactZ + CONE_RANGE);
        for (int bx = minX; bx <= maxX && ripples.size() < MAX_RIPPLE_DISPLAYS; bx++) {
            for (int bz = minZ; bz <= maxZ && ripples.size() < MAX_RIPPLE_DISPLAYS; bz++) {
                double x = bx + 0.5;
                double z = bz + 0.5;
                Vector offset = new Vector(x - impactX, 0, z - impactZ);
                if (!inConeWedge(forward, offset, CONE_RANGE, cosHalfAngle)) {
                    continue;
                }
                double distance = Math.sqrt(offset.getX() * offset.getX() + offset.getZ() * offset.getZ());
                // The block's sine peak: crests of the chirp wave (short period
                // near the impact, widening with distance) decide how high it
                // jumps; blocks on a trough barely move.
                double phase = kFactor * Math.log(1 + distance * RIPPLE_WAVELENGTH_GROWTH / RIPPLE_WAVELENGTH_MIN);
                double peak = RIPPLE_AMPLITUDE * Math.abs(Math.sin(phase));
                double arrivalTick = distance * RIPPLE_TICKS / CONE_RANGE;
                double fallTicks = peak > 0 ? Math.sqrt(2 * peak / FALL_GRAVITY) : 0;
                double endTick = arrivalTick + (peak > 0 ? RISE_TICKS + fallTicks : 0);

                Location loc = new Location(world, x, groundY + REST_OFFSET, z);
                Block floor = world.getBlockAt(bx, (int) groundY, bz);
                BlockDisplay display = world.spawn(loc, BlockDisplay.class, d -> {
                    d.setBlock(floor.getBlockData());
                    d.setTeleportDuration(1);
                    // Identity transformation on purpose: the display IS the
                    // ground block, only its vertical motion is animated.
                });
                ripples.add(new Ripple(x, z, display, arrivalTick, peak, endTick));
            }
        }
    }

    /**
     * Advances the one-pass wave. Each block has its own timeline: it rests,
     * visually indistinguishable from the ground, until the wavefront reaches
     * it ({@code arrivalTick}); it then climbs to its sine peak over
     * {@link #RISE_TICKS} ticks (eased by a sine, so the pop is smooth) and
     * falls back with {@link #FALL_GRAVITY} gravity. Once it has fallen back
     * to the ground ({@code endTick}) the display is removed — the shockwave
     * has travelled through that block exactly once.
     */
    private void animateRipple() {
        ripples.removeIf(ripple -> {
            if (!ripple.display.isValid() || ticks >= ripple.endTick) {
                if (ripple.display.isValid()) {
                    ripple.display.remove();
                }
                return true;
            }
            double y = groundY + REST_OFFSET;
            if (ticks >= ripple.arrivalTick) {
                if (ticks < ripple.arrivalTick + RISE_TICKS) {
                    double u = (ticks - ripple.arrivalTick) / RISE_TICKS;
                    y += ripple.peak * Math.sin(Math.PI / 2 * u);
                } else {
                    double falling = ticks - ripple.arrivalTick - RISE_TICKS;
                    y += ripple.peak - 0.5 * FALL_GRAVITY * falling * falling;
                }
            }
            ripple.display.teleport(new Location(world, ripple.x, y, ripple.z));
            return false;
        });
    }

    /**
     * Billows dust at the leading edge of the wavefront while it is still
     * travelling: the arc radius equals how far the shockwave has travelled so
     * far, so the dust ring expands outward across the wedge alongside the
     * lifted ground blocks. Once the front has reached the far edge the dust
     * stops — the wave passes through the ground only once.
     */
    private void spawnWavefrontDust() {
        if (ticks > RIPPLE_TICKS) {
            return;
        }
        double radius = CONE_RANGE * (double) ticks / RIPPLE_TICKS;
        if (radius <= 0) {
            return;
        }
        double halfAngle = Math.toRadians(CONE_HALF_ANGLE_DEG);
        int count = 3 + (int) (radius * 2);
        DustOptions dust = new DustOptions(Color.fromRGB(128, 122, 112), 1.1f);
        for (int i = 0; i < count; i++) {
            double angle = -halfAngle + Math.random() * 2 * halfAngle;
            Vector offset = rotateHorizontal(coneForward, angle).multiply(radius);
            world.spawnParticle(Particle.DUST,
                    new Location(world, impactX + offset.getX() + 0.5, groundY + 1 + Math.random() * 0.5,
                            impactZ + offset.getZ() + 0.5),
                    1, 0.15, 0.15, 0.15, 0.01, dust);
        }
    }

    // ---- Helpers ---------------------------------------------------------------

    /**
     * Rotates a vector around the Y axis (horizontal rotation).
     */
    private static Vector rotateHorizontal(Vector v, double radians) {
        return v.clone().rotateAroundY(radians);
    }

    /**
     * Y of the floor under a location: the block below the given point when
     * solid, otherwise the first solid block underneath. NaN when the column
     * has no solid block (mid-air slam with no ground to ripple).
     */
    private static double groundYAt(Location loc) {
        Block below = loc.getBlock().getRelative(BlockFace.DOWN);
        if (below.getType().isSolid()) {
            return below.getY();
        }
        World w = loc.getWorld();
        for (int y = loc.getBlockY(); y > w.getMinHeight(); y--) {
            Block b = w.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());
            if (b.getType().isSolid()) {
                return b.getY();
            }
        }
        return Double.NaN;
    }

    private static Block floorBlock(Location loc) {
        Block below = loc.getBlock().getRelative(BlockFace.DOWN);
        return below.getType().isSolid() ? below : loc.getBlock();
    }
}
