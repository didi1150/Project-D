package dev.bukkit.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.ability.behavior.SniperPassiveBehavior;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.StealthRegistry;
import dev.core.entity.EntityManager;
import dev.core.entity.mob.MobDefinition;
import dev.core.stat.StatType;
import dev.core.storage.config.ConfigSection;

public class SniperSkeletonBehavior implements MobBehavior {

    private static final Map<UUID, Settings> SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> TARGETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_SHOT_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_REPATH_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_TARGET_REFRESH_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> STRAFE_SIDE = new ConcurrentHashMap<>();

    @Override
    public void onSpawn(MobRPGEntity mob, LivingEntity vanilla) {
        UUID id = vanilla.getUniqueId();
        Settings settings = Settings.from(mob.getDefinition());
        SETTINGS.put(id, settings);
        NEXT_SHOT_AT.put(id, System.currentTimeMillis() + settings.shootIntervalTicks() * 50L);
        NEXT_REPATH_AT.put(id, 0L);
        NEXT_TARGET_REFRESH_AT.put(id, 0L);
        STRAFE_SIDE.put(id, Boolean.TRUE);
        if (vanilla instanceof Mob vanillaMob) {
            vanillaMob.setAware(true);
            vanillaMob.setTarget(null);
        }
    }

    @Override
    public void onTick(MobRPGEntity mob, LivingEntity vanilla, long now) {
        if (!(vanilla instanceof Mob vanillaMob) || !vanilla.isValid() || vanilla.isDead()) {
            return;
        }
        UUID id = vanilla.getUniqueId();
        Settings settings = SETTINGS.computeIfAbsent(id, ignored -> Settings.from(mob.getDefinition()));

        vanillaMob.setTarget(null);

        if (now >= NEXT_TARGET_REFRESH_AT.getOrDefault(id, 0L)) {
            NEXT_TARGET_REFRESH_AT.put(id, now + settings.repathTicks() * 50L);
            LivingEntity target = findTarget(vanilla, settings);
            if (target == null) {
                TARGETS.remove(id);
            } else {
                TARGETS.put(id, target.getUniqueId());
            }
        }

        LivingEntity target = resolveTarget(vanilla, id, settings);
        if (target == null) {
            vanillaMob.getPathfinder().stopPathfinding();
            return;
        }

        vanillaMob.lookAt(target);
        double distance = vanilla.getLocation().distance(target.getLocation());

        if (now >= NEXT_REPATH_AT.getOrDefault(id, 0L)) {
            NEXT_REPATH_AT.put(id, now + settings.repathTicks() * 50L);
            updateMovement(vanillaMob, target, distance, settings, id);
        }

        if (distance <= settings.shootRange()
                && vanilla.hasLineOfSight(target)
                && now >= NEXT_SHOT_AT.getOrDefault(id, 0L)) {
            NEXT_SHOT_AT.put(id, now + settings.shootIntervalTicks() * 50L);
            fire(mob, vanilla, target, settings, now);
        }
    }

    @Override
    public void onDeath(MobRPGEntity mob, LivingEntity vanilla) {
        UUID id = vanilla.getUniqueId();
        SETTINGS.remove(id);
        TARGETS.remove(id);
        NEXT_SHOT_AT.remove(id);
        NEXT_REPATH_AT.remove(id);
        NEXT_TARGET_REFRESH_AT.remove(id);
        STRAFE_SIDE.remove(id);
    }

    private static LivingEntity resolveTarget(LivingEntity vanilla, UUID id, Settings settings) {
        UUID targetId = TARGETS.get(id);
        if (targetId == null) {
            return null;
        }
        Entity entity = vanilla.getWorld().getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || target.isDead() || !target.isValid()) {
            TARGETS.remove(id);
            return null;
        }
        if (EntityManager.getInstance().isGhost(target.getUniqueId())) {
            TARGETS.remove(id);
            return null;
        }
        if (target.getLocation().distanceSquared(vanilla.getLocation()) > settings.aggroRange()
                * settings.aggroRange()) {
            TARGETS.remove(id);
            return null;
        }
        return target;
    }

    private static LivingEntity findTarget(LivingEntity vanilla, Settings settings) {
        LivingEntity best = null;
        double bestDistanceSq = settings.aggroRange() * settings.aggroRange();
        for (Entity entity : vanilla.getNearbyEntities(settings.aggroRange(), settings.aggroRange(),
                settings.aggroRange())) {
            if (!(entity instanceof LivingEntity target) || target.isDead() || !target.isValid()) {
                continue;
            }
            if (EntityManager.getInstance().isGhost(target.getUniqueId())) {
                continue;
            }
            if (!CombatRelation.isPlayerTeam(target)) {
                continue;
            }
            if (target instanceof Player player && StealthRegistry.shouldHideFromMob(player)) {
                continue;
            }
            double distanceSq = target.getLocation().distanceSquared(vanilla.getLocation());
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = target;
            }
        }
        return best;
    }

    private static void updateMovement(Mob vanilla, LivingEntity target, double distance, Settings settings,
            UUID id) {
        Location mobLocation = vanilla.getLocation();
        Location targetLocation = target.getLocation();
        Vector towardTarget = targetLocation.toVector().subtract(mobLocation.toVector()).setY(0);
        if (towardTarget.lengthSquared() < 1e-6) {
            return;
        }
        towardTarget.normalize();

        Location destination = null;
        if (distance < settings.preferredMinDistance() || distance > settings.preferredMaxDistance()) {
            destination = targetLocation.clone().subtract(towardTarget.clone().multiply(settings.preferredMaxDistance()));
        } else if (settings.strafeInRange() && settings.strafeDistance() > 0) {
            boolean right = STRAFE_SIDE.getOrDefault(id, Boolean.TRUE);
            STRAFE_SIDE.put(id, !right);
            Vector side = new Vector(-towardTarget.getZ(), 0, towardTarget.getX());
            if (!right) {
                side.multiply(-1);
            }
            destination = mobLocation.clone().add(side.multiply(settings.strafeDistance()));
        }

        if (destination == null) {
            vanilla.getPathfinder().stopPathfinding();
            return;
        }

        boolean moving = vanilla.getPathfinder().moveTo(destination, settings.moveSpeed());
        if (!moving) {
            Vector fallback = destination.toVector().subtract(mobLocation.toVector()).setY(0);
            if (fallback.lengthSquared() > 1e-6) {
                vanilla.setVelocity(fallback.normalize().multiply(0.25));
            }
        }
    }

    private static void fire(MobRPGEntity mob, LivingEntity vanilla, LivingEntity target, Settings settings,
            long now) {
        Location eye = vanilla.getEyeLocation();
        Location aim = target.getLocation().add(0, target.getHeight() * 0.55, 0);
        Vector direction = aim.toVector().subtract(eye.toVector());
        if (direction.lengthSquared() < 1e-6) {
            return;
        }
        direction.normalize();

        World world = vanilla.getWorld();
        Location spawn = eye.clone().add(direction.clone().multiply(0.6));
        Arrow arrow = world.spawnArrow(spawn, direction, settings.arrowSpeed(), 0.0f);
        arrow.setShooter(vanilla);
        arrow.setCritical(false);
        arrow.setGravity(false);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setDamage(0.0);
        arrow.setPierceLevel(127);

        double baseDamage = mob.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, now)
                * mob.getAbilityDamageMultiplier()
                * mob.getProjectileDamageMultiplier()
                * settings.baseDamageMultiplier();
        var pdc = arrow.getPersistentDataContainer();
        pdc.set(SniperPassiveBehavior.ARROW_KEY, PersistentDataType.BOOLEAN, true);
        pdc.set(SniperPassiveBehavior.BASE_DAMAGE_KEY, PersistentDataType.DOUBLE, baseDamage);
        pdc.set(SniperPassiveBehavior.ORIGIN_X_KEY, PersistentDataType.DOUBLE, eye.getX());
        pdc.set(SniperPassiveBehavior.ORIGIN_Y_KEY, PersistentDataType.DOUBLE, eye.getY());
        pdc.set(SniperPassiveBehavior.ORIGIN_Z_KEY, PersistentDataType.DOUBLE, eye.getZ());
        pdc.set(SniperPassiveBehavior.GROWTH_KEY, PersistentDataType.DOUBLE, settings.distanceDamageGrowth());
        pdc.set(SniperPassiveBehavior.MAX_MULTIPLIER_KEY, PersistentDataType.DOUBLE,
                settings.maxDistanceDamageMultiplier());
        pdc.set(SniperPassiveBehavior.PIERCE_KEY, PersistentDataType.INTEGER, settings.pierceCount());

        world.playSound(vanilla.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.75f);
        scheduleLifetimeCleanup(arrow, settings.arrowLifetimeTicks());
    }

    private static void scheduleLifetimeCleanup(Arrow arrow, int lifetimeTicks) {
        DMain plugin = DMain.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isValid()) {
                    arrow.remove();
                }
                SniperPassiveBehavior.clearArrowState(arrow.getUniqueId());
            }
        }.runTaskLater(plugin, Math.max(1, lifetimeTicks));
    }

    private record Settings(int shootIntervalTicks, double aggroRange, double shootRange,
            double preferredMinDistance, double preferredMaxDistance, int repathTicks, double moveSpeed,
            boolean strafeInRange, double strafeDistance, float arrowSpeed, int arrowLifetimeTicks,
            double baseDamageMultiplier, double distanceDamageGrowth, double maxDistanceDamageMultiplier,
            int pierceCount) {

        private static Settings from(MobDefinition definition) {
            ConfigSection params = definition.getBehaviorParams();
            double minDistance = Math.max(1.0, getDouble(params, "preferred-min-distance", 8.0));
            double maxDistance = Math.max(minDistance,
                    getDouble(params, "preferred-max-distance", 18.0));
            return new Settings(
                    Math.max(1, getInt(params, "shoot-interval-ticks", 120)),
                    Math.max(1.0, getDouble(params, "aggro-range", 32.0)),
                    Math.max(1.0, getDouble(params, "shoot-range", 24.0)),
                    minDistance,
                    maxDistance,
                    Math.max(1, getInt(params, "repath-ticks", 10)),
                    Math.max(0.1, getDouble(params, "move-speed", 1.0)),
                    getBoolean(params, "strafe-in-range", true),
                    Math.max(0.0, getDouble(params, "strafe-distance", 5.0)),
                    (float) Math.max(0.1, getDouble(params, "arrow-speed", 3.5)),
                    Math.max(1, getInt(params, "arrow-lifetime-ticks", 100)),
                    Math.max(0.0, getDouble(params, "base-damage-multiplier", 1.0)),
                    Math.max(0.0, getDouble(params, "distance-damage-growth", 0.05)),
                    Math.max(1.0, getDouble(params, "max-distance-damage-multiplier", 5.0)),
                    Math.max(1, getInt(params, "pierce-count", 8)));
        }

        private static int getInt(ConfigSection section, String key, int fallback) {
            return section == null ? fallback : section.getInt(key, fallback);
        }

        private static double getDouble(ConfigSection section, String key, double fallback) {
            return section == null ? fallback : section.getDouble(key, fallback);
        }

        private static boolean getBoolean(ConfigSection section, String key, boolean fallback) {
            return section == null ? fallback : section.getBoolean(key, fallback);
        }
    }
}
