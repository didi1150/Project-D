package dev.bukkit.ability;

import java.util.Optional;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.bukkit.ability.behavior.SupportStaffBehavior;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.CombatRelation;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityHealEvent.HealReason;
import dev.core.stat.StatType;
import dev.core.status.StatusEffectType;

/**
 * RIGHT-click effect for the Utility Staff. The action depends on the staff's
 * current mode:
 * <ul>
 * <li><b>Mode 1 (Mending Touch)</b>: Heals a targeted ally (3s cooldown)</li>
 * <li><b>Mode 2 (Aegis Ward)</b>: Grants absorption to a targeted ally (2s
 * cooldown)</li>
 * <li><b>Mode 3 (Tempest Gust)</b>: Pushes all nearby enemies outward (10s
 * cooldown)</li>
 * </ul>
 * Cooldowns are managed per-mode via {@link CooldownSink#startCooldown(long)}.
 * Targeting indicators (glow outlines) are managed by
 * {@link SupportStaffBehavior}.
 */
public class BukkitSupportStaffUseEffect extends Effect {

    private static final double TARGETING_RANGE = 15.0;
    private static final double RAY_SIZE = 0.5;

    private static final double HEAL_BASE = 20.0;
    private static final long HEAL_COOLDOWN_MS = 3000;

    private static final double SHIELD_BASE_POTENCY = 20.0;
    private static final long SHIELD_DURATION_MS = 5000;
    private static final long SHIELD_COOLDOWN_MS = 2000;

    private static final double PUSH_RADIUS = 8.0;
    private static final double PUSH_STRENGTH = 1.2;
    private static final long PUSH_COOLDOWN_MS = 10000;

    public BukkitSupportStaffUseEffect(String cooldownKey) {
        super(null, -1, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        if (!(caster instanceof BukkitPlayerEntity playerEntity))
            return;
        Optional<Player> optPlayer = playerEntity.getPlayer();
        if (optPlayer.isEmpty())
            return;
        Player player = optPlayer.get();
        if (player.isDead() || !player.isOnline())
            return;

        SupportStaffBehavior state = SupportStaffBehavior.forHolder(caster.getUuid());
        if (state == null) {
            System.out.println("[StaffDebug] USE cast: state is null for " + caster.getUuid());
            return;
        }
        int mode = state.getCurrentMode();
        System.out.println("[StaffDebug] USE cast: mode=" + mode + " player=" + player.getName());

        switch (mode) {
        case 1 -> handleHeal(caster, player, cooldownSink);
        case 2 -> handleShield(caster, player, cooldownSink);
        case 3 -> handlePushback(caster, player, cooldownSink);
        }
    }

    @Override
    public void cancel() {
    }

    private void handleHeal(RPGEntity caster, Player player, CooldownSink cooldownSink) {
        Entity targetBukkit = raycastAlly(player);
        if (targetBukkit == null) {
//            System.out.println("[StaffDebug] HEAL: no target found by raycast");
            return;
        }
//        System.out.println("[StaffDebug] HEAL: raycast found " + targetBukkit.getName());

        Optional<RPGEntity> targetOpt = EntityManager.getInstance().getEntity(targetBukkit.getUniqueId());
        if (targetOpt.isEmpty()) {
//            System.out.println("[StaffDebug] HEAL: entity not in EntityManager for " + targetBukkit.getUniqueId());
            return;
        }
        RPGEntity target = targetOpt.get();
        if (!target.isAlive()) {
//            System.out.println("[StaffDebug] HEAL: target is dead");
            return;
        }
        if (target.getHealth() >= target.getMaxHealth()) {
//            System.out.println(
//                    "[StaffDebug] HEAL: target at max health=" + target.getHealth() + "/" + target.getMaxHealth());
            return;
        }

//        System.out.println("[StaffDebug] HEAL: healing " + targetBukkit.getName() + " for " + HEAL_BASE + " health="
//                + target.getHealth() + "/" + target.getMaxHealth());
        caster.healRPGEntity(caster, target, HEAL_BASE, HealReason.SPELL);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                targetBukkit.getLocation().add(0, targetBukkit.getHeight() + 0.3, 0), 5, 0.3, 0.3, 0.3, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.8f);
        cooldownSink.startCooldown(HEAL_COOLDOWN_MS);
    }

    private void handleShield(RPGEntity caster, Player player, CooldownSink cooldownSink) {
        Entity targetBukkit = raycastAlly(player);
        if (targetBukkit == null) {
//            System.out.println("[StaffDebug] SHIELD: no target found by raycast");
            return;
        }
//        System.out.println("[StaffDebug] SHIELD: raycast found " + targetBukkit.getName());

        Optional<RPGEntity> targetOpt = EntityManager.getInstance().getEntity(targetBukkit.getUniqueId());
        if (targetOpt.isEmpty()) {
//            System.out.println("[StaffDebug] SHIELD: entity not in EntityManager for " + targetBukkit.getUniqueId());
            return;
        }
        RPGEntity target = targetOpt.get();
        if (!target.isAlive()) {
//            System.out.println("[StaffDebug] SHIELD: target is dead");
            return;
        }

        double healPower = caster.getStatEngineAdapter().getCurrentValue(StatType.HEAL_AND_SHIELD_POWER,
                System.currentTimeMillis());
        double potency = SHIELD_BASE_POTENCY * (1.0 + healPower / 100.0);
        System.out.println("[StaffDebug] SHIELD: applying to " + targetBukkit.getName() + " potency="
                + String.format("%.1f", potency) + " healPower=" + String.format("%.1f", healPower));
        target.getStatusEffectManager().apply(target, StatusEffectType.ABSORPTION, SHIELD_DURATION_MS, true, potency);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                targetBukkit.getLocation().add(0, targetBukkit.getHeight() + 0.3, 0), 8, 0.3, 0.3, 0.3, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.5f, 1.2f);
        cooldownSink.startCooldown(SHIELD_COOLDOWN_MS);
    }

    private void handlePushback(RPGEntity caster, Player player, CooldownSink cooldownSink) {
        World world = player.getWorld();
        Location center = player.getLocation();
        for (Entity entity : world.getNearbyEntities(center, PUSH_RADIUS, PUSH_RADIUS, PUSH_RADIUS)) {
            if (!(entity instanceof LivingEntity living))
                continue;
            if (living.getUniqueId().equals(caster.getUuid()))
                continue;
            if (living.isDead() || !living.isValid())
                continue;
            if (EntityManager.getInstance().isGhost(living.getUniqueId()))
                continue;
            if (!CombatRelation.isEnemy(caster, entity))
                continue;

            Vector toEntity = living.getLocation().toVector().subtract(center.toVector());
            toEntity.setY(0);
            double dist = toEntity.length();
            if (dist < 0.1 || dist > PUSH_RADIUS)
                continue;

            double falloff = 1.0 - (dist / PUSH_RADIUS);
            Vector push = toEntity.normalize().multiply(PUSH_STRENGTH * falloff);
            push.setY(0.35);
            living.setVelocity(push);
            world.spawnParticle(Particle.CLOUD, living.getLocation().add(0, living.getHeight() * 0.5, 0), 3, 0.2, 0.2,
                    0.2, 0.01);
        }
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
        cooldownSink.startCooldown(PUSH_COOLDOWN_MS);
    }

    /**
     * Raycasts from the player's eye in the look direction to find the nearest
     * allied living entity (other players and player-owned summons). Returns the
     * Bukkit Entity or null if nothing valid is in sight.
     */
    private Entity raycastAlly(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        RayTraceResult result = world.rayTrace(eye.clone().add(dir), dir, TARGETING_RANGE, FluidCollisionMode.NEVER,
                true, RAY_SIZE, this::isAlliedTarget);
        if (result == null || result.getHitEntity() == null) {
//            if (System.currentTimeMillis() % 2000 < 50) {
//                int nearbyCount = 0;
//                for (Entity e : world.getNearbyEntities(eye, TARGETING_RANGE, TARGETING_RANGE, TARGETING_RANGE)) {
//                    if (e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())) {
//                        nearbyCount++;
//                    }
//                }
//                System.out.println("[StaffDebug] USE raycast miss: nearbyLiving=" + nearbyCount + " eye="
//                        + String.format("%.1f,%.1f,%.1f", eye.getX(), eye.getY(), eye.getZ()));
//            }
            return null;
        }

        Entity hit = result.getHitEntity();
        if (!(hit instanceof LivingEntity living))
            return null;
        if (living.isDead() || !living.isValid())
            return null;
        if (EntityManager.getInstance().isGhost(living.getUniqueId()))
            return null;

//        System.out.println("[StaffDebug] USE raycast HIT: " + hit.getName() + " dist="
//                + String.format("%.1f", eye.distance(hit.getLocation())));
        return hit;
    }

    /**
     * Entity filter for the ray trace: accepts living entities on the player team
     * that are not the caster.
     */
    private boolean isAlliedTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living))
            return false;
        if (living.isDead() || !living.isValid())
            return false;
        if (EntityManager.getInstance().isGhost(living.getUniqueId()))
            return false;
        boolean allied = CombatRelation.isPlayerTeam(entity);
//        if (System.currentTimeMillis() % 5000 < 50) {
//            System.out.println("[StaffDebug] USE isAlliedTarget: " + entity.getName() + " type=" + entity.getType()
//                    + " alive=" + living.isValid() + " playerTeam=" + allied);
//        }
        return allied;
    }
}
