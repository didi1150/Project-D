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
import org.bukkit.entity.Player;
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

/**
 * Spinjitzu: a cyclone of spinning block rings wraps the caster for
 * {@value #MAX_SPIN_TICKS} ticks (~4 seconds), visually following them and
 * pulling nearby enemies into the caster on every tick. Five rings of flat
 * glass bars rotate around the caster; the rings widen from the caster's feet
 * up to their head, so the cone grows outward as it rises.
 * <p>
 * Performance: the rings are the costliest part (each bar is a BlockDisplay
 * that must be moved), so they are re-positioned only every
 * {@link #RING_UPDATE_INTERVAL} ticks with a matching
 * {@link #TELEPORT_DURATION_TICKS}-tick glide - the client interpolates them at
 * render frame rate, so halving the updates is visually indistinguishable from
 * per-tick updates. The spin phase advances by a fixed increment per update
 * instead of being derived from the wall clock: on a late tick the rings simply
 * pause and resume, instead of jumping ahead all at once (the visible "bursts"
 * that read as lag). On normal movement the ring center is led ahead by
 * {@link #MOVEMENT_LEAD_TICKS} ticks of the caster's velocity, so the 2-tick
 * glide lands exactly on the caster's track: at constant velocity the vortex
 * rides on the player in real time instead of trailing a fixed two ticks of
 * motion behind. If the caster moves more than {@link #SNAP_DISTANCE} blocks in
 * one tick (sprint-through, dash, teleport), the bars are teleported WITHOUT
 * glide, so the vortex never trails behind the player.
 * <p>
 * The suction is not just physics: a few small glass dust motes are spawned in
 * the band just outside the cone and fly into the cone's center, visualizing
 * the pull. Their count is capped low, since each is also a BlockDisplay.
 * <p>
 * The effect is single-instance per (caster, item instance) via the effect
 * manager: re-casting while a cyclone is already up is refused until the
 * current one collapses.
 */
public class BukkitSpinjitzuEffect extends Effect {

    /** Rough visual + suction duration in ticks (10 seconds). */
    private static final int MAX_SPIN_TICKS = 80;

    /**
     * Ring radii, from the bottom (feet) ring to the top (head) ring. The cone
     * grows outward: narrow at the feet, wide at the head.
     */
    private static final double[] RING_RADII = { 0.2, 0.5, 0.9, 1.4, 1.8 };
    /** Ring heights relative to the caster's feet. */
    private static final double[] RING_Y = { -0.1, 0.35, 0.8, 1.25, 1.7 };
    /** Tangent bar segments per ring (more for the wider rings). */
    private static final int[] RING_SEGMENTS = { 6, 8, 12, 16, 20 };

    /** Spin rate in degrees per tick (per ring update this is doubled). */
    private static final float SPIN_PER_TICK = 18.0f;
    /** Per-ring phase offset in degrees, staggering the rings like a spiral. */
    private static final float RING_YAW_OFFSET = 9.0f;
    /**
     * Only re-position the rings every N ticks; each teleport glides over
     * {@link #TELEPORT_DURATION_TICKS} ticks, so the renderer still shows
     * continuous motion while the server sends half the updates.
     */
    private static final int RING_UPDATE_INTERVAL = 2;
    /** Each teleport glides over this many ticks (client-side smoothing). */
    private static final int TELEPORT_DURATION_TICKS = 2;
    /**
     * The ring center is led ahead of the caster by this many ticks of their
     * velocity. A glide of {@link #TELEPORT_DURATION_TICKS} ticks targets the LED
     * position, so under constant velocity the rings ride exactly on the caster
     * instead of trailing a fixed two ticks of motion behind.
     */
    private static final double MOVEMENT_LEAD_TICKS = TELEPORT_DURATION_TICKS;
    /** Upper bound for the velocity lead, in blocks (dash/knockback guard). */
    private static final double MAX_LEAD_BLOCKS = 1.5;

    /** Length and cross-section of one flat glass bar, in blocks. */
    private static final float SEGMENT_LENGTH = 0.50f;
    private static final float SEGMENT_THICKNESS = 0.35f;

    /** Enemies within this radius are pulled into the caster. */
    private static final double PULL_RADIUS = 4.5;

    /**
     * Caster movement above this many blocks in a single tick teleports the bars
     * with no glide, so the vortex keeps up instantly instead of trailing.
     */
    private static final double SNAP_DISTANCE = 1.2;

    /** Dust motes flying to the cone center: size and speed per tick. */
    private static final float DUST_SCALE = 0.09f;
    private static final double DUST_SPEED = 0.45;
    /** Max concurrent dust motes (kept low: each is a BlockDisplay entity). */
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
            // Caster despawned, logged out or died: drop the vortex.
            cleanup();
            return;
        }
        if (++ticks > MAX_SPIN_TICKS) {
            cleanup();
            return;
        }
        animate(casterEntity, ticks % RING_UPDATE_INTERVAL == 0);
        spawnDustMotes(casterEntity);
        moveDustMotes(casterEntity);
        pullEnemies(casterEntity);
    }

    /**
     * Ends the vortex. Cancelling an already-cleaned-up effect (e.g. the manager's
     * expiry pass after a tick-initiated cleanup) is a no-op.
     */
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

    /**
     * Places one ring of flat glass bars around the caster. Each bar is scaled so
     * its long axis is tangent to the ring at its position; the ring itself is
     * rotated by teleporting the bars (see {@link #animate}).
     */
    private void spawnRing(World world, Location center, double radius, double y, int segmentCount, float yawOffset) {
        for (int j = 0; j < segmentCount; j++) {
            double angle = Math.toRadians(yawOffset + j * (360.0 / segmentCount));
            Location loc = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            segments.add(world.spawn(loc, BlockDisplay.class, display -> {
                display.setBlock(Material.WHITE_STAINED_GLASS.createBlockData());
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTeleportDuration(TELEPORT_DURATION_TICKS);
                display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(1f, 0f, 0f, 0f),
                        new Vector3f(SEGMENT_LENGTH, SEGMENT_THICKNESS, SEGMENT_THICKNESS),
                        new AxisAngle4f(1f, 0f, 0f, 0f)));
            }));
        }
    }

    /**
     * Re-positions every bar of every ring to its next phase around the caster's
     * CURRENT location, so the vortex follows the caster and keeps spinning. The
     * phase advances by a fixed increment each ring update, so on a late tick the
     * rings pause and resume rather than jumping ahead.
     * <p>
     * Movement following: the ring center is led ahead by the caster's velocity
     * times {@link #MOVEMENT_LEAD_TICKS}. Because every teleport glides over
     * {@link #TELEPORT_DURATION_TICKS} ticks, a target at the caster's current
     * position would leave the rings trailing a constant two ticks of motion
     * behind; leading the target by exactly the glide length makes the linear glide
     * coincide with the caster's own track, so at constant velocity the rings ride
     * exactly on the caster at all times (at zero extra per-tick cost; direction
     * changes only cost one update). Bars are teleported without glide (a one-time
     * snap) when the caster covers more than {@link #SNAP_DISTANCE} blocks since
     * the last update, so even extreme moves (teleports) never leave the vortex
     * behind.
     */
    private void animate(LivingEntity casterEntity, boolean updateRings) {
        if (!updateRings) {
            return;
        }
        spinYaw += SPIN_PER_TICK * RING_UPDATE_INTERVAL;
        Location center = casterEntity.getLocation();
        boolean snap = false;
        if (lastCenter == null) {
            lastCenter = center.clone();
        } else {
            snap = lastCenter.distanceSquared(center) > SNAP_DISTANCE * SNAP_DISTANCE;
            lastCenter = center.clone();
        }
        Vector lead = casterEntity.getVelocity().clone().multiply(MOVEMENT_LEAD_TICKS);
        lead.setY(0);
        double leadLength = lead.length();
        if (leadLength > MAX_LEAD_BLOCKS) {
            lead.multiply(MAX_LEAD_BLOCKS / leadLength);
        }
        center.add(lead);
        int index = 0;
        for (int i = 0; i < RING_RADII.length; i++) {
            float ringYaw = spinYaw + i * RING_YAW_OFFSET;
            double radius = RING_RADII[i];
            double y = RING_Y[i];
            int count = RING_SEGMENTS[i];
            for (int j = 0; j < count; j++) {
                double angle = Math.toRadians(ringYaw + j * (360.0 / count));
                Location loc = new Location(center.getWorld(), center.getX() + Math.cos(angle) * radius,
                        center.getY() + y, center.getZ() + Math.sin(angle) * radius);
                // Yaw keeps the bar's long axis tangent to the ring.
                loc.setYaw((float) Math.toDegrees(angle) + 90.0f);
                BlockDisplay display = segments.get(index++);
                if (snap) {
                    display.setTeleportDuration(-1);
                }
                display.teleport(loc);
                if (snap) {
                    display.setTeleportDuration(TELEPORT_DURATION_TICKS);
                }
            }
        }
    }

    /**
     * Spawns one dust mote per tick in the band just outside the cone, so the
     * inflow of the suction is visible without spawning many displays.
     */
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

    /**
     * Advances every dust mote towards the center of the cone; motes that reach it
     * vanish (the pull sinks into the vortex).
     */
    private void moveDustMotes(LivingEntity casterEntity) {
        Vector target = casterEntity.getLocation().add(0, 1.1, 0).toVector();
        dustMotes.removeIf(mote -> {
            if (!mote.isValid()) {
                return true;
            }
            Location loc = mote.getLocation();
            Vector toCenter = target.clone().subtract(loc.toVector());
            double distance = toCenter.length();
            if (distance <= DUST_SPEED) {
                mote.remove();
                return true;
            }
            loc.add(toCenter.normalize().multiply(DUST_SPEED));
            mote.teleport(loc);
            return false;
        });
    }

    /**
     * Drags adversaries into the caster: players' cyclones pull mobs, a mob's
     * cyclone pulls players (mirroring the Bonemerang's symmetric targeting).
     * Ghosts, armor stands, items and the caster itself are never pulled.
     */
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
            if (distance == 0) {
                continue;
            }
            // Stronger pull the further away the enemy is, capped so they are
            // drawn in rather than launched at the caster.
            toCenter.normalize().multiply(Math.min(0.55, 0.22 + distance * 0.08));
            living.setVelocity(toCenter);
        }
    }

    /**
     * Resolves the Bukkit living entity backing a caster, so the cyclone works for
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
}