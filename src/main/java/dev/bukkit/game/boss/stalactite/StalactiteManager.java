package dev.bukkit.game.boss.stalactite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.event.BukkitEventBus;
import dev.core.event.EventAction;

/**
 * Slim manager — owns spawn locations and dispatches ticks to
 * {@link BukkitStalactiteEntity}. Each entity owns its own lifecycle (GROWING,
 * ACTIVE, FALLING), hitbox, collider, display and damage handling.
 * <p>
 * Manager responsibilities: create another {@link BukkitStalactiteEntity} at
 * the set spawn location on impact (instantly as GROWING; entity handles
 * REGROW_TICKS visually), global projectile sweep, and cleanup.
 */
public class StalactiteManager {

    private final World world;
    private final List<Location> ceilings = new ArrayList<>();
    private final List<BukkitStalactiteEntity> entities = new ArrayList<>();
    private final Map<UUID, BukkitStalactiteEntity> hitboxMap = new HashMap<>();
    private final Map<UUID, BukkitStalactiteEntity> colliderMap = new HashMap<>();
    private final Consumer<BukkitStalactiteEntity> impactHandler;
    private final boolean respawnEnabled;
    private final boolean debugHitbox;

    private BukkitTask tickTask;
    private long tickCounter = 0;
    private EventAction<ProjectileHitEvent> projectileBlockHitAction;

    public StalactiteManager(World world, Location singleCeiling, Consumer<BukkitStalactiteEntity> impactHandler,
            boolean respawnEnabled, boolean debugHitbox) {
        this.world = world;
        this.impactHandler = impactHandler != null ? impactHandler : (e -> {
        });
        this.respawnEnabled = respawnEnabled;
        this.debugHitbox = debugHitbox;
        Location ceiling = singleCeiling.clone();
        ceiling.setWorld(world);
        this.ceilings.add(ceiling);
    }

    public List<BukkitStalactiteEntity> getEntities() {
        return entities;
    }

    public void start() {
        for (int i = 0; i < ceilings.size(); i++) {
            spawnEntityAt(ceilings.get(i), i);
        }
        projectileBlockHitAction = new EventAction<>(this::onProjectileHitBlockProximity, ProjectileHitEvent.class,
                EventAction.HIGH_PRIORITY);
        BukkitEventBus.getInstance().subscribe(projectileBlockHitAction);
        tickTask = Bukkit.getScheduler().runTaskTimer(DMain.getInstance(), this::tick, 0L, 1L);
    }

    private BukkitStalactiteEntity spawnEntityAt(Location ceiling, int idx) {
        return spawnEntityAt(ceiling, ceiling, idx);
    }

    private BukkitStalactiteEntity spawnEntityAt(Location ceiling, Location pivot, int idx) {
        // ensure world
        ceiling.setWorld(world);
        if (pivot != null)
            pivot.setWorld(world);
        Location usePivot = pivot != null ? pivot : ceiling;
        BukkitStalactiteEntity entity = BukkitStalactiteEntity.spawnNew(world, ceiling, usePivot, idx, tickCounter,
                debugHitbox);
        entities.add(entity);
        if (entity.hitbox != null)
            hitboxMap.put(entity.hitbox.getUniqueId(), entity);
        if (entity.collider != null)
            colliderMap.put(entity.collider.getUniqueId(), entity);
        return entity;
    }

    private void handleImpactAndScheduleRespawn(BukkitStalactiteEntity entity) {
        // external impact (boss push / debug msg)
        try {
            impactHandler.accept(entity);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Stalactite] impact handler failed: " + e.getMessage());
        }

        Location ceiling = entity.getCeiling();
        Location pivot = entity.getPivot();
        int respawnIdx = entity.spawnIndex;
        if (respawnIdx < 0 || respawnIdx >= ceilings.size()) {
            respawnIdx = 0;
            for (int i = 0; i < ceilings.size(); i++) {
                if (ceilings.get(i).distanceSquared(ceiling) < 0.01) {
                    respawnIdx = i;
                    break;
                }
            }
        }
        final Location respawnCeiling = ceiling.clone();
        final Location respawnPivot = pivot.clone();

        // despawn current entity and maps
        if (entity.hitbox != null)
            hitboxMap.remove(entity.hitbox.getUniqueId());
        // hitboxMap removal also try uuid
        hitboxMap.remove(entity.getUuid());
        if (entity.collider != null)
            colliderMap.remove(entity.collider.getUniqueId());
        entities.remove(entity);
        // fully despawn bukkit entities + unregister
        entity.despawn();

        if (tickTask == null || world == null)
            return; // manager destroyed externally
        if (!respawnEnabled) {
            // Single stalactite that doesn't regrow — stay despawned after fall
            return;
        }
        // Instant respawn as GROWING — entity handles 20s visual regrow (REGROW_TICKS)
        // itself.
        // Previously this was delayed by REGROW_TICKS, causing 20s void + 20s grow =
        // 40s downtime.
        spawnEntityAt(respawnCeiling, respawnPivot, respawnIdx);
    }

    private void tick() {
        tickCounter++;
        // tick each entity (snapshot to allow despawn during loop)
        for (BukkitStalactiteEntity e : new ArrayList<>(entities)) {
            // keep hitboxMap in sync if hitbox recreated? not needed after spawnNew
            e.tickStalactite(tickCounter);
            // maps keep valid; hitbox/collider teleport handled inside entity
        }
        // sync maps after tick (hitbox may have been teleported but uuid same)
        // also cleanup any entities that marked pendingDespawn but not yet handled via
        // direct callback?
        // In our flow onImpact already handled via handleImpactAndScheduleRespawn
        // synchronously,
        // but double-check polling fallback for any missed:
        for (BukkitStalactiteEntity e : new ArrayList<>(entities)) {
            if (e.isPendingDespawn() && entities.contains(e)) {
                handleImpactAndScheduleRespawn(e);
            }
        }
        checkProjectileCollisions();
    }

    private void checkProjectileCollisions() {
        if (entities.isEmpty())
            return;
        double radius = StalactiteConfig.PROJECTILE_TRACK_RADIUS;
        double margin = StalactiteConfig.PROJECTILE_TRACK_MARGIN;
        Set<UUID> handled = new HashSet<>();
        for (BukkitStalactiteEntity entity : new ArrayList<>(entities)) {
            if (!entity.isDamageable())
                continue;
            if (entity.hitbox == null || !entity.hitbox.isValid())
                continue;
            Location ceiling = entity.getCeiling();
            Location scanCenter = StalactiteHitbox.computeColliderLocation(ceiling);
            Collection<Entity> nearby;
            try {
                nearby = world.getNearbyEntities(scanCenter, radius + StalactiteConfig.MAX_DISPLAY_SCALE_X,
                        radius + StalactiteConfig.MAX_DISPLAY_SCALE_Y, radius + StalactiteConfig.MAX_DISPLAY_SCALE_Z);
            } catch (Exception ex) {
                continue;
            }
            for (Entity en : nearby) {
                if (!(en instanceof Projectile proj))
                    continue;
                if (!proj.isValid() || proj.isDead())
                    continue;
                UUID pid = proj.getUniqueId();
                if (handled.contains(pid))
                    continue;
                Location projLoc = proj.getLocation();
                Vector projVec = new Vector(projLoc.getX(), projLoc.getY(), projLoc.getZ());
                boolean hit = StalactiteHitbox.isInsideTaperedStalactite(projVec, ceiling, margin);
                if (!hit) {
                    try {
                        Vector vel = proj.getVelocity();
                        if (vel.lengthSquared() > 1e-8) {
                            Vector start = projVec.clone().subtract(vel);
                            Vector dir = vel.clone().normalize();
                            double dist = vel.length() + 0.6;
                            if (StalactiteHitbox.rayIntersectsTapered(start, dir, dist, ceiling, margin))
                                hit = true;
                            else {
                                try {
                                    BoundingBox box = proj.getBoundingBox();
                                    if (box != null) {
                                        Vector min = new Vector(box.getMinX(), box.getMinY(), box.getMinZ());
                                        Vector max = new Vector(box.getMaxX(), box.getMaxY(), box.getMaxZ());
                                        Vector center = min.clone().add(max).multiply(0.5);
                                        if (StalactiteHitbox.isInsideTaperedStalactite(center, ceiling, margin))
                                            hit = true;
                                        else if (StalactiteHitbox.isInsideTaperedStalactite(min, ceiling, margin))
                                            hit = true;
                                        else if (StalactiteHitbox.isInsideTaperedStalactite(max, ceiling, margin))
                                            hit = true;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (!hit && entity.collider != null && entity.collider.isValid()) {
                    try {
                        if (StalactiteHitbox.isInsideTaperedStalactite(projVec, ceiling, margin + 0.25))
                            hit = true;
                    } catch (Exception ignored) {
                    }
                }
                if (hit) {
                    handled.add(pid);
                    LivingEntity shooter = null;
                    if (proj.getShooter() instanceof LivingEntity le)
                        shooter = le;
                    entity.dealRPGDamageFromProjectile(shooter, proj);
                    try {
                        proj.remove();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public void onProjectileHitBlockProximity(ProjectileHitEvent event) {
        if (event.isCancelled())
            return;
        Entity hit = event.getHitEntity();
        if (hit != null) {
            BukkitStalactiteEntity entity = hitboxMap.get(hit.getUniqueId());
            boolean isHitbox = entity != null;
            if (entity == null)
                entity = colliderMap.get(hit.getUniqueId());
            if (entity != null && entity.isDamageable()) {
                Projectile proj = event.getEntity();
                Location projLoc = proj.getLocation();
                Vector projVec = new Vector(projLoc.getX(), projLoc.getY(), projLoc.getZ());
                double margin = StalactiteConfig.PROJECTILE_TRACK_MARGIN;
                if (!StalactiteHitbox.isInsideTaperedStalactite(projVec, entity.getCeiling(), margin)) {
                    try {
                        Vector vel = proj.getVelocity();
                        if (vel.lengthSquared() > 1e-8) {
                            Vector start = projVec.clone().subtract(vel);
                            if (!StalactiteHitbox.rayIntersectsTapered(start, vel.clone().normalize(),
                                    vel.length() + 0.6, entity.getCeiling(), margin))
                                return;
                        } else
                            return;
                    } catch (Exception ignored) {
                        return;
                    }
                }
                if (isHitbox) {
                    try {
                        proj.remove();
                    } catch (Exception ignored) {
                    }
                } else {
                    LivingEntity shooter = proj.getShooter() instanceof LivingEntity le ? le : null;
                    entity.dealRPGDamageFromProjectile(shooter, proj);
                    try {
                        proj.remove();
                    } catch (Exception ignored) {
                    }
                }
            }
            return;
        }
        if (event.getHitBlock() == null)
            return;
        Location blockLoc = event.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        BukkitStalactiteEntity near = findByProximity(blockLoc, 1.5);
        if (near == null || !near.isDamageable())
            return;
        Projectile proj = event.getEntity();
        Vector projVec = new Vector(proj.getLocation().getX(), proj.getLocation().getY(), proj.getLocation().getZ());
        if (!StalactiteHitbox.isInsideTaperedStalactite(projVec, near.getCeiling(),
                StalactiteConfig.PROJECTILE_TRACK_MARGIN)) {
            Vector vel = proj.getVelocity();
            if (vel.lengthSquared() > 1e-8) {
                Vector start = projVec.clone().subtract(vel);
                if (!StalactiteHitbox.rayIntersectsTapered(start, vel.clone().normalize(), vel.length() + 0.6,
                        near.getCeiling(), StalactiteConfig.PROJECTILE_TRACK_MARGIN))
                    return;
            } else
                return;
        }
        LivingEntity shooter = null;
        if (proj.getShooter() instanceof LivingEntity le)
            shooter = le;
        near.dealRPGDamageFromProjectile(shooter, proj);
        proj.remove();
    }

    private BukkitStalactiteEntity findByProximity(Location loc, double radius) {
        double best = radius * radius;
        BukkitStalactiteEntity bestEnt = null;
        for (BukkitStalactiteEntity e : entities) {
            double d;
            if (e.hitbox != null && e.hitbox.isValid())
                d = e.hitbox.getLocation().distanceSquared(loc);
            else if (e.collider != null && e.collider.isValid())
                d = e.collider.getLocation().distanceSquared(loc);
            else
                d = e.getCeiling().distanceSquared(loc);
            if (d < best) {
                best = d;
                bestEnt = e;
            }
        }
        return bestEnt;
    }

    public void destroy() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        try {
            if (projectileBlockHitAction != null) {
                BukkitEventBus.getInstance().unsubscribe(projectileBlockHitAction);
                projectileBlockHitAction = null;
            }
        } catch (Exception ignored) {
        }
        for (BukkitStalactiteEntity e : new ArrayList<>(entities)) {
            try {
                hitboxMap.remove(e.hitbox != null ? e.hitbox.getUniqueId() : e.getUuid());
            } catch (Exception ignored) {
            }
            try {
                if (e.collider != null)
                    colliderMap.remove(e.collider.getUniqueId());
            } catch (Exception ignored) {
            }
            try {
                e.despawn();
            } catch (Exception ignored) {
            }
        }
        entities.clear();
        hitboxMap.clear();
        colliderMap.clear();
    }
}
