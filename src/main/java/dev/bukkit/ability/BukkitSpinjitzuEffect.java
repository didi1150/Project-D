package dev.bukkit.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;
import dev.core.stat.modifier.StatModifier;
import dev.core.stat.modifier.StatModifierType;

public class BukkitSpinjitzuEffect extends Effect {

    private static final int MAX_SPIN_TICKS = 80;

    private static final double[] RING_RADII = { 0.2, 0.5, 0.9, 1.4, 1.8 };
    private static final double[] RING_Y = { -0.1, 0.35, 0.8, 1.25, 1.7 };
    private static final int[] RING_SEGMENTS = { 6, 8, 12, 16, 20 };

    private static final float SPIN_PER_TICK = 18.0f;
    private static final float RING_YAW_OFFSET = 9.0f;
    
    /** 
     * Teleport duration set to 1 tick ensures smooth 60 FPS client-side lerp 
     * between server updates without introducing matrix rotation warping.
     */
    private static final int TELEPORT_DURATION_TICKS = 1;
    private static final double MOVEMENT_LEAD_TICKS = 1.0;
    private static final double MAX_LEAD_BLOCKS = 1.5;

    private static final float SEGMENT_LENGTH = 0.50f;
    private static final float SEGMENT_THICKNESS = 0.35f;

    private static final double PULL_RADIUS = 4.5;
    private static final double SNAP_DISTANCE = 2.5;

    private static final float DUST_SCALE = 0.09f;
    private static final double DUST_SPEED = 0.45;
    private static final int MAX_DUST_MOTES = 18;

    private final List<BlockDisplay> segments = new ArrayList<>();
    private final List<BlockDisplay> dustMotes = new ArrayList<>();
    private final Random random = new Random();
    private RPGEntity caster;
    private Location lastCenter;
    private float spinYaw;
    private int ticks;
    private boolean cleanedUp;

    public BukkitSpinjitzuEffect(String cooldownKey) {
        super(null, MAX_SPIN_TICKS * 125L, true, cooldownKey);
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
        this.caster = caster;
        this.cleanedUp = false;
        this.spinYaw = 0;
        this.lastCenter = null;
        spawnCyclone(casterEntity);
        casterEntity.getWorld().playSound(casterEntity.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 0.8f);
        caster.getStatEngineAdapter().addStatModifier(new StatModifier(20, StatModifierType.MULTIPLY, StatType.MOVE_SPEED,
                getCooldownKey() + ":spinjitzu", System.currentTimeMillis()));
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (cleanedUp) {
            return;
        }
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null || casterEntity.isDead()) {
            cleanup();
            return;
        }
        if (++ticks > MAX_SPIN_TICKS) {
            cleanup();
            return;
        }
        animate(casterEntity);
        spawnDustMotes(casterEntity);
        moveDustMotes(casterEntity);
        pullEnemies(casterEntity);
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
        for (BlockDisplay segment : segments) {
            if (segment.isValid()) {
                segment.remove();
            }
        }
        segments.clear();
        for (BlockDisplay mote : dustMotes) {
            if (mote.isValid()) {
                mote.remove();
            }
        }
        dustMotes.clear();
        caster.getStatEngineAdapter().removeModifiersBySource(getCooldownKey() + ":spinjitzu");
    }

    private void spawnCyclone(LivingEntity casterEntity) {
        Location center = casterEntity.getLocation();
        World world = center.getWorld();
        for (int i = 0; i < RING_RADII.length; i++) {
            spawnRing(world, center, RING_RADII[i], RING_Y[i], RING_SEGMENTS[i], i * RING_YAW_OFFSET);
        }
    }

    private void spawnRing(World world, Location center, double radius, double y, int segmentCount, float yawOffset) {
        for (int j = 0; j < segmentCount; j++) {
            double angle = Math.toRadians(yawOffset + j * (360.0 / segmentCount));
            Location loc = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            
            segments.add(world.spawn(loc, BlockDisplay.class, display -> {
                display.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTeleportDuration(TELEPORT_DURATION_TICKS);
                // Transformation handles only scaling and static alignment; 
                // world positions handle exact circular movement.
                display.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f), 
                        new AxisAngle4f(0f, 0f, 0f, 0f),
                        new Vector3f(SEGMENT_LENGTH, SEGMENT_THICKNESS, SEGMENT_THICKNESS),
                        new AxisAngle4f(0f, 0f, 0f, 0f)
                ));
            }));
        }
    }

    private void animate(LivingEntity casterEntity) {
        spinYaw += SPIN_PER_TICK;
        Location center = casterEntity.getLocation();
        if (!isFinite(center)) {
            return;
        }

        boolean snap = false;
        Vector lead = new Vector();

        if (lastCenter == null) {
            lastCenter = center.clone();
        } else {
            // Calculated position delta tracks key movement correctly (unlike casterEntity.getVelocity())
            Vector delta = center.toVector().subtract(lastCenter.toVector());
            double distSq = delta.lengthSquared();

            if (Double.isFinite(distSq) && distSq > 0.0) {
                lead = delta.multiply(MOVEMENT_LEAD_TICKS);
                double leadLengthSq = lead.lengthSquared();
                if (leadLengthSq > MAX_LEAD_BLOCKS * MAX_LEAD_BLOCKS) {
                    lead.normalize().multiply(MAX_LEAD_BLOCKS);
                }
            }

            // Snap only on instant teleports or massive dashes
            snap = distSq > SNAP_DISTANCE * SNAP_DISTANCE;
        }
        
        lastCenter = center.clone();
        center.add(lead);

        int index = 0;
        for (int i = 0; i < RING_RADII.length; i++) {
            float ringYaw = spinYaw + i * RING_YAW_OFFSET;
            double radius = RING_RADII[i];
            double y = RING_Y[i];
            int count = RING_SEGMENTS[i];

            for (int j = 0; j < count; j++) {
                double angle = Math.toRadians(ringYaw + j * (360.0 / count));
                
                double xOffset = Math.cos(angle) * radius;
                double zOffset = Math.sin(angle) * radius;

                Location loc = new Location(
                        center.getWorld(),
                        center.getX() + xOffset,
                        center.getY() + y,
                        center.getZ() + zOffset
                );

                // Safe yaw calculation preventing NaN crashes
                float calculatedYaw = (float) Math.toDegrees(angle) + 90.0f;
                loc.setYaw(finiteOr(calculatedYaw, 0.0f));
                loc.setPitch(0.0f);

                BlockDisplay display = segments.get(index++);
                if (snap) {
                    display.setTeleportDuration(0);
                }
                
                display.teleport(loc);
                
                if (snap) {
                    display.setTeleportDuration(TELEPORT_DURATION_TICKS);
                }
            }
        }
    }

    private static boolean isFinite(Location loc) {
        return Double.isFinite(loc.getX()) && Double.isFinite(loc.getY()) && Double.isFinite(loc.getZ());
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isNaN(value) || Float.isInfinite(value) ? fallback : value;
    }

    private void spawnDustMotes(LivingEntity casterEntity) {
        if (dustMotes.size() >= MAX_DUST_MOTES) {
            return;
        }
        Location center = casterEntity.getLocation();
        double angle = Math.toRadians(random.nextDouble() * 360.0);
        double radius = PULL_RADIUS * (0.55 + 0.35 * random.nextDouble());
        double y = 0.2 + random.nextDouble() * 1.5;
        Location start = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
        dustMotes.add(center.getWorld().spawn(start, BlockDisplay.class, mote -> {
            mote.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
            mote.setBrightness(new Display.Brightness(15, 15));
            mote.setTeleportDuration(TELEPORT_DURATION_TICKS);
            mote.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(1f, 0f, 0f, 0f),
                    new Vector3f(DUST_SCALE, DUST_SCALE, DUST_SCALE), new AxisAngle4f(1f, 0f, 0f, 0f)));
        }));
    }

    private void moveDustMotes(LivingEntity casterEntity) {
        Vector target = casterEntity.getLocation().add(0, 1.1, 0).toVector();
        dustMotes.removeIf(mote -> {
            if (!mote.isValid()) {
                return true;
            }
            Location loc = mote.getLocation();
            Vector toCenter = target.clone().subtract(loc.toVector());
            double distance = toCenter.length();
            if (!Double.isFinite(distance) || distance <= DUST_SPEED) {
                mote.remove();
                return true;
            }
            loc.add(toCenter.normalize().multiply(DUST_SPEED));
            mote.teleport(loc);
            return false;
        });
    }

    private void pullEnemies(LivingEntity casterEntity) {
        boolean casterIsPlayer = caster instanceof BukkitPlayerEntity;
        Location center = casterEntity.getLocation().add(0, 0.75, 0);
        for (Entity entity : center.getWorld().getNearbyEntities(center, PULL_RADIUS, PULL_RADIUS, PULL_RADIUS)) {
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
            Vector toCenter = center.toVector().subtract(living.getLocation().toVector());
            double distance = toCenter.length();

            if (!Double.isFinite(distance) || distance <= 0.0) {
                continue;
            }

            toCenter.normalize().multiply(Math.min(0.55, 0.22 + distance * 0.08));
            living.setVelocity(toCenter);
        }
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
}