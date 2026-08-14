package dev.bukkit.ability;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.Sound;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Spirit Sceptre bat. Spawns a red bat that flies in a straight line toward
 * the block the caster was aiming at when casting (locked at cast time, NOT
 * re-aimed while in flight); on contact with a mob or a solid block it
 * explodes, dealing magic damage to every enemy within {@link #EXPLOSION_RADIUS}
 * blocks. No cooldown: each successful cast is its own effect instance, so bats
 * can be chained as long as the caster's mana (250 per cast, see
 * {@link dev.core.ability.impl.SpiritSceptreAbility}) holds out.
 */
public class BukkitSpiritSceptreBatEffect extends Effect {

    private static final double BAT_BASE_DAMAGE = 10.0;
    private static final double BAT_SPEED = 1.2; // blocks per tick
    private static final int MAX_TICKS = 40; // ~48 block range cap
    private static final double HIT_RADIUS = 1.0; // contact radius that triggers the explosion
    private static final double EXPLOSION_RADIUS = 10.0;
    public static final String METADATA = "SPIRIT_BAT";

    private Bat bat;
    private int ticks;
    private boolean exploded;
    private Runnable startCooldown;
    // Flight path, locked at cast time: direction per tick plus the exact
    // destination the caster was aiming at, so the bat is unaffected by the
    // player turning while it is in flight.
    private Vector flightDirection;
    private Vector targetPosition;

    /**
     * Each cast produces an independent effect (not single-instance) so bats may be
     * fired back-to-back. Duration covers the full flight; an early explosion
     * simply leaves an inert effect that the manager purges at expiry.
     */
    public BukkitSpiritSceptreBatEffect(String cooldownKey) {
        super(null, MAX_TICKS * 50L + 100, false, cooldownKey);
    }

    /**
     * Resolves the Bukkit living entity backing a caster, so the bat can be
     * launched by players AND mobs (mobs share the vanilla entity's uuid).
     */
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

    @Override
    public void cast(RPGEntity caster, Runnable startCooldown, Runnable resetCooldown) {
        this.startCooldown = startCooldown;
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            return;
        }

        Location spawnLoc = casterEntity.getEyeLocation();
        bat = spawnLoc.getWorld().spawn(spawnLoc, Bat.class, b -> {
            b.setAI(false);
            b.setGravity(false);
            b.setInvulnerable(true);
            b.setSilent(true);
            b.setCollidable(false);
            b.setPersistent(true);
            // Awake = the flying pose with flapping wings; without this the bat
            // renders hanging upside down in its static "sleep" position.
            b.setAwake(true);
            // The red leather cap is what makes the bat read as a "red bat".
            ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
            LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
            meta.setColor(Color.RED);
            helmet.setItemMeta(meta);
            b.getEquipment().setHelmet(helmet);
            b.setMetadata(METADATA, new FixedMetadataValue(DMain.getInstance(), true));
        });

        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_BAT_TAKEOFF, new Random().nextFloat(0.3f, 0.6f),
                new Random().nextFloat(1.5f, 1.8f));

        // Lock the flight path at cast time: raycast from the eye along the look
        // direction (up to the max flight range) and fly straight toward the
        // block the crosshair was on. The bat is NOT re-aimed while in flight.
        double maxRange = BAT_SPEED * MAX_TICKS;
        RayTraceResult trace = spawnLoc.getWorld().rayTraceBlocks(spawnLoc, spawnLoc.getDirection(), maxRange);
        Vector dest = trace != null && trace.getHitPosition() != null
                ? trace.getHitPosition()
                : spawnLoc.toVector().add(spawnLoc.getDirection().multiply(maxRange));
        targetPosition = dest;
        Vector delta = dest.clone().subtract(spawnLoc.toVector());
        if (delta.lengthSquared() < 1.0) {
            delta = spawnLoc.getDirection().multiply(maxRange);
        }
        flightDirection = delta.normalize().multiply(BAT_SPEED);
    }

    @Override
    public void cancel() {
        exploded = true;
        if (bat != null && bat.isValid()) {
            bat.remove();
        }
        bat = null;
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (exploded || bat == null || !bat.isValid()) {
            return;
        }
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            cancel();
            return;
        }

        Location loc = bat.getLocation();

        // Keep the bat in its flying (flapping) pose: even with AI disabled the
        // bat can still flip to the hanging "sleep" pose when the server decides
        // it should rest, so re-assert the awake flag every tick.
        if (!bat.isAwake()) {
            bat.setAwake(true);
        }

        // The flight path was locked at cast time: the bat flies in a straight
        // line toward the targeted block, independent of the player's aim.
        // Sub-step the movement so a fast bat cannot tunnel through a wall.
        Vector step = flightDirection.clone().multiply(0.25);
        for (int i = 0; i < 4; i++) {
            loc.add(step);
            if (isBlocked(loc)) {
                explode(caster, casterEntity);
                return;
            }
        }

        // Destination reached (the block the player was aiming at): detonate.
        if (loc.toVector().distanceSquared(targetPosition) <= BAT_SPEED * BAT_SPEED) {
            explode(caster, casterEntity);
            return;
        }

        if (hitsEntity(caster, loc)) {
            explode(caster, casterEntity);
            return;
        }

        ticks++;
        bat.teleport(loc);
        loc.getWorld().spawnParticle(Particle.DUST, loc, 1, new DustOptions(Color.RED, 1.0f));

        if (ticks >= MAX_TICKS) {
            explode(caster, casterEntity);
        }
    }

    /**
     * The bat only explodes when its own position enters a solid block. It must NOT
     * react to the block above it: the bat spawns at eye height, so a ceiling one
     * block overhead (any 2-tall corridor) used to detonate it the instant it was
     * cast.
     */
    private boolean isBlocked(Location loc) {
        return loc.getBlock().getType().isSolid();
    }

    private boolean hitsEntity(RPGEntity caster, Location loc) {
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
            if (isEnemy(caster, entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Explodes the bat: AoE magic damage to every enemy within the blast radius,
     * then removes the bat. Magic damage follows the standard formula Damage = Base
     * Damage * (1 + (Mana / 100) * Ability Power), where Mana is the caster's max
     * mana (the intelligence-equivalent stat) and Ability Power the caster's ✦
     * stat.
     */
    private void explode(RPGEntity caster, LivingEntity casterEntity) {
        if (exploded) {
            return;
        }
        exploded = true;
        Location loc = bat != null && bat.isValid() ? bat.getLocation() : casterEntity.getLocation();

        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, new Random().nextFloat(0.7f, 0.9f));
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        loc.getWorld().spawnParticle(Particle.DUST, loc, 20, 1.5, 1.5, 1.5, new DustOptions(Color.RED, 2.0f));

        long now = System.currentTimeMillis();
//        double mana = caster.getStatEngineAdapter().getCurrentValue(StatType.MANA_MAX, now);
        double abilityPower = caster.getStatEngineAdapter().getCurrentValue(StatType.ABILITY_POWER, now);
//        double damage = BAT_BASE_DAMAGE * (1.0 + (mana / 100.0) * abilityPower)
//                * caster.getAbilityDamageMultiplier();
        double damage = BAT_BASE_DAMAGE * (1.0 + abilityPower) * caster.getAbilityDamageMultiplier();

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, EXPLOSION_RADIUS, EXPLOSION_RADIUS,
                EXPLOSION_RADIUS)) {
            if (!isEnemy(caster, entity)) {
                continue;
            }
            LivingEntity le = (LivingEntity) entity;

            EntityManager.getInstance().getEntity(entity.getUniqueId()).ifPresentOrElse(target -> {
                RPGDamageResult result = target.dealRPGDamage(caster, target, damage, DamageType.MAGIC);
                if (result.getResult() != DamageResult.DENY) {
                    knockback(le, loc);
                    showMagicIndicator(le, result.getDamage(), result.getResult());
                    playHitSound(le, result.getResult());
                }
            }, () -> {
                // Vanilla mob: damageMob fires an EntityDamageByEntityEvent that
                // CombatListener already renders an indicator for, so we must not
                // render again here or the indicator doubles.
                DamageUtils.damageMob(le, damage, casterEntity);
                knockback(le, loc);
                playHitSound(le, DamageResult.NORMAL);
            });
        }

        cancel();
        if (startCooldown != null) {
            startCooldown.run();
        }
    }

    /**
     * Enemy filter shared by the contact check and the AoE: living entities,
     * excluding the caster, other bats/bonemerangs, ghosts, and same-team entities
     * (players only blast mobs, mobs only blast players).
     */
    private boolean isEnemy(RPGEntity caster, Entity entity) {
        if (!(entity instanceof LivingEntity le) || le.getUniqueId().equals(caster.getUuid())) {
            return false;
        }
        if (le.hasMetadata(METADATA) || le.hasMetadata("BONEMERANG")) {
            return false;
        }
        if (EntityManager.getInstance().isGhost(le.getUniqueId())) {
            return false;
        }
        boolean casterIsPlayer = caster instanceof BukkitPlayerEntity;
        if (casterIsPlayer && entity.getType() == EntityType.PLAYER) {
            return false;
        }
        return casterIsPlayer || entity.getType() == EntityType.PLAYER;
    }

    private void knockback(LivingEntity le, Location blastLoc) {
        Vector knockbackDirection = le.getLocation().toVector().subtract(blastLoc.toVector()).normalize();
        le.setVelocity(knockbackDirection.multiply(0.4));
    }

    /**
     * Renders the magical ✦ damage indicator, mirroring the Bonemerang's reuse of
     * the central damage-number machinery.
     */
    private void showMagicIndicator(LivingEntity le, double damage, DamageResult result) {
        if (damage <= 0) {
            return;
        }
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.showMagicDamage(le.getLocation(), damage, result);
    }

    private void playHitSound(LivingEntity le, DamageResult result) {
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.playProjectileHitSound(le, result);
    }

}