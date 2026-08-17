package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

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

public class BukkitSmashEffect extends Effect {

    // ---- Cone geometry --------------------------------------------------------
    public static final double CONE_FULL_ANGLE_DEG = 60.0;
    public static final double CONE_HALF_ANGLE_DEG = CONE_FULL_ANGLE_DEG / 2.0;
    public static final double CONE_RANGE = 8.0;
    /**
     * Vertical band above the ground a target must be standing in to be hit.
     * The band is measured against the ground under the TARGET, not the impact
     * point, so enemies up or down a staircase stay hittable as long as they
     * are standing on a surface.
     */
    private static final double CONE_HEIGHT = 3.0;

    // ---- Terrain sampling (stairs/hills) --------------------------------------
    /**
     * Half-height of the nearby-entity search box around the impact. Terrain
     * can deviate far above or below the impact on staircases and hills; the
     * per-target ground check narrows the candidates down.
     */
    private static final double SEARCH_Y_HALF_EXTENT = CONE_HEIGHT + CONE_RANGE;
    /**
     * A column surface this far above the caster's feet is treated as a
     * roof/overhang instead of a climbable slope: stairs in play gain roughly
     * one block per two blocks of run, so the cone's far edge normally stays
     * within this band. Surfaces within it (ascending stairs) are ridden,
     * surfaces above it fall back to the nearest floor.
     */
    private static final int MAX_SLOPE_RISE = (int) CONE_HEIGHT + 1;
    /** When a column's only surface is a roof, scan down for the nearest floor. */
    private static final int MAX_FLOOR_SCAN_DOWN = 4;

    // ---- Eruption grid --------------------------------------------------------
    private static final double MIN_DISTANCE = 2.0;
    private static final double MAX_DISTANCE = 8.0;
    private static final double DISTANCE_STEP = 1.5;
    private static final double ANGLE_STEP_DEGREES = 8.0;

    // ---- Animation & timing (ticks) -------------------------------------------
    private static final int ROW_DELAY_TICKS = 1;
    private static final int PILLAR_RISE_TICKS = 3;
    private static final int HOLD_TICKS = 15;
    private static final int DISSOLVE_TICKS = 15;

    // ---- Pillar properties -----------------------------------------------------
    private static final float MIN_PILLAR_HEIGHT = 0.5f;
    private static final float MAX_PILLAR_HEIGHT = 1.4f;

    // ---- Audio & particles -----------------------------------------------------
    private static final float SOUND_VOLUME = 1.5f;
    private static final float SOUND_PITCH = 0.6f;
    private static final int DEBRIS_PARTICLES = 20;

    // ---- Damage ---------------------------------------------------------------
    private static final double BASE_DAMAGE = 15.0;
    private static final double ARMOR_SCALE = 1.5;
    private static final double MAGIC_RESIST_SCALE = 1.5;

    // ---- Knockback ------------------------------------------------------------
    private static final double KNOCKBACK_BASE = 0.85;
    private static final double KNOCKBACK_FALLOFF = 0.45;
    private static final double KNOCKBACK_UP = 0.32;

    // ---- Derived timeline ------------------------------------------------------
    private static final int ROW_COUNT = (int) ((MAX_DISTANCE - MIN_DISTANCE) / DISTANCE_STEP) + 1;
    private static final int LAST_ROW_TICK = (ROW_COUNT - 1) * ROW_DELAY_TICKS;
    private static final int RISE_END_TICK = LAST_ROW_TICK + 1 + PILLAR_RISE_TICKS;
    private static final int DISSOLVE_START_TICK = RISE_END_TICK + HOLD_TICKS;
    private static final int LAST_REMOVE_TICK = DISSOLVE_START_TICK + DISSOLVE_TICKS + 1;
    private static final int MAX_PILLAR_DISPLAYS = 96;

    private static final class PillarSpec {
        final double x;
        final double z;
        final double groundY;
        final BlockData block;
        final int spawnTick;

        PillarSpec(double x, double z, double groundY, BlockData block, int spawnTick) {
            this.x = x;
            this.z = z;
            this.groundY = groundY;
            this.block = block;
            this.spawnTick = spawnTick;
        }
    }

    private static final class Pillar {
        final BlockDisplay display;
        final BlockData block;
        final double x;
        final double z;
        final double groundY;
        final float targetHeight;
        final int riseTick;
        final int dissolveTick;
        final int removeTick;
        boolean risen;
        boolean dissolving;

        Pillar(BlockDisplay display, BlockData block, double x, double z, double groundY, float targetHeight,
                int riseTick, int dissolveTick, int removeTick) {
            this.display = display;
            this.block = block;
            this.x = x;
            this.z = z;
            this.groundY = groundY;
            this.targetHeight = targetHeight;
            this.riseTick = riseTick;
            this.dissolveTick = dissolveTick;
            this.removeTick = removeTick;
        }
    }

    private final List<PillarSpec> pendingPillars = new ArrayList<>();
    private final List<Pillar> pillars = new ArrayList<>();
    private boolean cleanedUp;
    private World world;
    private Vector coneForward;
    private double impactX;
    private double impactZ;
    private int ticks;

    public BukkitSmashEffect(String cooldownKey) {
        super(null, (LAST_REMOVE_TICK + 10) * 50L + 100, true, cooldownKey);
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
        this.pendingPillars.clear();
        this.pillars.clear();
        this.world = casterEntity.getWorld();
        Location impact = casterEntity.getLocation();
        this.impactX = impact.getX();
        this.impactZ = impact.getZ();

        Vector forward = casterEntity.getEyeLocation().getDirection();
        forward.setY(0);
        if (forward.lengthSquared() == 0) {
            forward = new Vector(0, 0, -1);
        }
        this.coneForward = forward.normalize();
        Vector right = new Vector(-coneForward.getZ(), 0, coneForward.getX());

        smashEnemies(caster, casterEntity, coneForward, impact);

        impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, SOUND_VOLUME, SOUND_PITCH);
        impact.getWorld().playSound(impact, Sound.BLOCK_STONE_BREAK, 1.0f, 0.9f);
        impact.getWorld().spawnParticle(Particle.FALLING_DUST, impact.clone().add(0, 0.2, 0), 60, 1.5, 0.4, 1.5, 0.4,
                floorBlock(impact).getBlockData());
        spawnDirectionalShockwave(impact, coneForward, right);

        schedulePillars(impact, coneForward, right);

        cooldownSink.startCooldown();
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (cleanedUp || world == null) {
            return;
        }
        ticks++;
        spawnPendingPillars();
        animatePillars();
        spawnHoldSmoke();
        if (pendingPillars.isEmpty() && pillars.isEmpty() && ticks >= LAST_REMOVE_TICK) {
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
        for (Pillar pillar : pillars) {
            if (pillar.display.isValid()) {
                pillar.display.remove();
            }
        }
        pillars.clear();
        pendingPillars.clear();
    }

    public static boolean inConeWedge(Vector forward, Vector offset, double range, double cosHalfAngle) {
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
        return dot >= cosHalfAngle;
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

    private void smashEnemies(RPGEntity caster, LivingEntity casterEntity, Vector forward, Location impact) {
        boolean casterIsPlayer = caster instanceof BukkitPlayerEntity;
        double cosHalfAngle = Math.cos(Math.toRadians(CONE_HALF_ANGLE_DEG));
        long now = System.currentTimeMillis();
        double armor = caster.getStatEngineAdapter().getCurrentValue(StatType.ARMOR, now);
        double magicResist = caster.getStatEngineAdapter().getCurrentValue(StatType.MAGIC_RESIST, now);
        double damage = smashDamage(armor, magicResist, caster.getAbilityDamageMultiplier());

        for (Entity entity : impact.getWorld().getNearbyEntities(impact, CONE_RANGE, SEARCH_Y_HALF_EXTENT, CONE_RANGE)) {
            if (!(entity instanceof LivingEntity living) || living.getUniqueId().equals(caster.getUuid())) {
                continue;
            }
            if (living instanceof ArmorStand) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
                continue;
            }
            if (casterIsPlayer && entity.getType() == EntityType.PLAYER) {
                continue;
            }
            if (!casterIsPlayer && entity.getType() != EntityType.PLAYER) {
                continue;
            }
            // The shockwave rides the ground, not the caster's own altitude: a
            // target is hittable when it stands on a surface within the band,
            // no matter how much higher or lower that surface is (stairs up
            // and down stay in the cone's reach).
            double groundTop = groundTopBelow(living.getLocation());
            if (Double.isNaN(groundTop)) {
                continue; // flying over a void column
            }
            double standHeight = living.getLocation().getY() - groundTop;
            if (standHeight < -0.5 || standHeight > CONE_HEIGHT) {
                continue; // not standing on the ground (mid-air / deep below ground)
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
                DamageUtils.damageMob(living, damage, casterEntity);
                playHitSound(living, DamageResult.NORMAL);
            });
        }
    }

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

    private void schedulePillars(Location impact, Vector forward, Vector right) {
        double halfAngle = Math.toRadians(CONE_HALF_ANGLE_DEG);
        double angleStep = Math.toRadians(ANGLE_STEP_DEGREES);
        double impactY = impact.getY(); // Floor reference level
        int rowIndex = 0;

        for (double d = MIN_DISTANCE; d <= MAX_DISTANCE + 1e-9; d += DISTANCE_STEP) {
            int spawnTick = rowIndex * ROW_DELAY_TICKS;
            for (double theta = -halfAngle; theta <= halfAngle + 1e-9; theta += angleStep) {
                if (pendingPillars.size() >= MAX_PILLAR_DISPLAYS) {
                    return;
                }

                double dirX = forward.getX() * Math.cos(theta) + right.getX() * Math.sin(theta);
                double dirZ = forward.getZ() * Math.cos(theta) + right.getZ() * Math.sin(theta);
                double x = impactX + dirX * d;
                double z = impactZ + dirZ * d;

                Block ground = pillarColumnGround(world, (int) Math.floor(x), (int) Math.floor(z), impactY);
                if (ground == null) {
                    continue;
                }

                BlockData block = ground.getType().isAir() ? Material.STONE.createBlockData() : ground.getBlockData();
                pendingPillars.add(new PillarSpec(x, z, ground.getY(), block, spawnTick));
            }
            rowIndex++;
        }
    }

    private void spawnPendingPillars() {
        pendingPillars.removeIf(spec -> {
            if (spec.spawnTick > ticks) {
                return false;
            }
            spawnPillar(spec);
            return true;
        });
    }

    private void spawnPillar(PillarSpec spec) {
        // Center alignment offset (-0.5 on X/Z) keeps block display centered on column
        Location loc = new Location(world, spec.x - 0.5, spec.groundY + 1.0, spec.z - 0.5);
        BlockDisplay display = world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(spec.block);
        });

        Transformation initial = display.getTransformation();
        Transformation flatTransform = new Transformation(initial.getTranslation(), initial.getLeftRotation(),
                new Vector3f(1.0f, 0.01f, 1.0f), initial.getRightRotation());
        display.setTransformation(flatTransform);
        display.setInterpolationDuration(PILLAR_RISE_TICKS);
        display.setInterpolationDelay(0);

        float targetHeight = MIN_PILLAR_HEIGHT + (float) (Math.random() * (MAX_PILLAR_HEIGHT - MIN_PILLAR_HEIGHT));

        pillars.add(new Pillar(display, spec.block, spec.x, spec.z, spec.groundY, targetHeight, ticks + 1,
                ticks + HOLD_TICKS + PILLAR_RISE_TICKS, ticks + HOLD_TICKS + PILLAR_RISE_TICKS + DISSOLVE_TICKS + 1));

        world.spawnParticle(Particle.BLOCK_CRUMBLE, new Location(world, spec.x, spec.groundY + 1.0, spec.z),
                DEBRIS_PARTICLES, 0.3, 0.2, 0.3, spec.block);
    }

    private void animatePillars() {
        pillars.removeIf(pillar -> {
            if (ticks >= pillar.removeTick) {
                if (pillar.display.isValid()) {
                    pillar.display.remove();
                }
                return true;
            }
            if (!pillar.risen && ticks >= pillar.riseTick) {
                Transformation current = pillar.display.getTransformation();
                Transformation riseTransform = new Transformation(current.getTranslation(), current.getLeftRotation(),
                        new Vector3f(1.0f, pillar.targetHeight, 1.0f), current.getRightRotation());
                pillar.display.setTransformation(riseTransform);
                pillar.risen = true;
            }
            if (pillar.dissolving) {
                Location loc = new Location(world, pillar.x, pillar.groundY + 1.3, pillar.z);
                world.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 8, 0.2, 0.3, 0.2, pillar.block);
                world.spawnParticle(Particle.SMOKE, new Location(world, pillar.x, pillar.groundY + 1.1, pillar.z), 2,
                        0.1, 0.1, 0.1, 0.02);
            } else if (ticks >= pillar.dissolveTick) {
                pillar.display.setInterpolationDuration(DISSOLVE_TICKS);
                pillar.display.setInterpolationDelay(0);

                Transformation current = pillar.display.getTransformation();
                Transformation shrinkTransform = new Transformation(new Vector3f(0.5f, 0.0f, 0.5f),
                        current.getLeftRotation(), new Vector3f(0.01f, 0.01f, 0.01f), current.getRightRotation());
                pillar.display.setTransformation(shrinkTransform);
                pillar.dissolving = true;
            }
            return false;
        });
    }

    private void spawnHoldSmoke() {
        if (ticks < RISE_END_TICK || ticks >= DISSOLVE_START_TICK || (ticks - RISE_END_TICK) % 5 != 0) {
            return;
        }
        for (Pillar pillar : pillars) {
            if (pillar.dissolving || !pillar.display.isValid()) {
                continue;
            }
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    new Location(world, pillar.x, pillar.groundY + 1.8, pillar.z), 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private void spawnDirectionalShockwave(Location center, Vector forward, Vector right) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double halfAngle = Math.toRadians(CONE_HALF_ANGLE_DEG);
        for (double theta = -halfAngle; theta <= halfAngle; theta += Math.toRadians(5.0)) {
            double dirX = forward.getX() * Math.cos(theta) + right.getX() * Math.sin(theta);
            double dirZ = forward.getZ() * Math.cos(theta) + right.getZ() * Math.sin(theta);
            Vector dir = new Vector(dirX, 0, dirZ).normalize().multiply(0.8);

            world.spawnParticle(Particle.EXPLOSION, center.clone().add(dir), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.CLOUD, center, 0, dir.getX(), 0.1, dir.getZ(), 0.3);
        }
    }

    private static Block floorBlock(Location loc) {
        Block below = loc.getBlock().getRelative(BlockFace.DOWN);
        return below.getType().isSolid() ? below : loc.getBlock();
    }

    /**
     * Picks the block a pillar rises out of for one ground column. The highest
     * motion-blocking surface in the column is followed whenever it is not
     * unreasonably high above the caster's feet (that only happens for stairs
     * climbing out of the slam, up to {@link #MAX_SLOPE_RISE}); a higher
     * surface is a roof/overhang and the search falls back to the nearest
     * floor under the caster. Returns null when the column has no usable
     * surface (e.g. over an open pit).
     */
    private static Block pillarColumnGround(World world, int bx, int bz, double impactY) {
        Block highest = world.getHighestBlockAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        while (highest.getY() > world.getMinHeight() && highest.isPassable()) {
            highest = highest.getRelative(BlockFace.DOWN);
        }
        if (highest.getY() + 1.0 <= impactY + MAX_SLOPE_RISE) {
            return highest; // flat ground, descending stairs, or climbing stairs
        }
        // Roof/overhang: fall back to the nearest floor at or below the caster.
        int from = (int) Math.floor(impactY + 1.0);
        int to = (int) Math.floor(impactY) - MAX_FLOOR_SCAN_DOWN;
        for (int y = from; y >= to && y > world.getMinHeight(); y--) {
            Block candidate = world.getBlockAt(bx, y, bz);
            if (candidate.getType().isSolid()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Top of the solid surface an entity is standing on: the highest solid
     * block at or below the entity's feet. NaN when the column has no solid
     * block (flying over a void).
     */
    private static double groundTopBelow(Location loc) {
        World world = loc.getWorld();
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        for (int y = loc.getBlockY(); y >= world.getMinHeight(); y--) {
            Block b = world.getBlockAt(bx, y, bz);
            if (b.getType().isSolid()) {
                return b.getY() + 1.0;
            }
        }
        return Double.NaN;
    }
}