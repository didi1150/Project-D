package dev.bukkit.ability;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitEntityMetadata;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.game.boss.stalactite.BukkitStalactiteEntity;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.game.boss.stalactite.StalactiteHitbox;
import dev.bukkit.utils.CombatRelation;
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
 * Spirit Sceptre bat. Spawns a red bat that flies in a straight line toward the
 * block the caster was aiming at when casting (locked at cast time, NOT
 * re-aimed while in flight); on contact with a mob or a solid block it
 * explodes, dealing magic damage to every enemy within
 * {@link #EXPLOSION_RADIUS} blocks. No cooldown: each successful cast is its
 * own effect instance, so bats can be chained as long as the caster's mana (250
 * per cast, see {@link dev.core.ability.impl.SpiritSceptreAbility}) holds out.
 */
public class BukkitSpiritSceptreBatEffect extends Effect {

    private static final double BAT_BASE_DAMAGE = 10.0;
    private static final double BAT_SPEED = 1.2; // blocks per tick
    private static final int MAX_TICKS = 40; // ~48 block range cap
    private static final double HIT_RADIUS = 1.0; // contact radius that triggers the explosion
    private static final double EXPLOSION_RADIUS = 6.0;
    public static final String METADATA = "SPIRIT_BAT";

    private Bat bat;
    private int ticks;
    private boolean exploded;
    private CooldownSink cooldownSink;
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
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        this.cooldownSink = cooldownSink;
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
            BukkitEntityMetadata.set(b, METADATA, true);
        });

        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_BAT_TAKEOFF, new Random().nextFloat(0.3f, 0.6f),
                new Random().nextFloat(1.5f, 1.8f));

        // Lock the flight path at cast time: raycast from the eye along the look
        // direction (up to the max flight range) and fly straight toward the
        // block the crosshair was on. The bat is NOT re-aimed while in flight.
        double maxRange = BAT_SPEED * MAX_TICKS;
        RayTraceResult trace = spawnLoc.getWorld().rayTraceBlocks(spawnLoc, spawnLoc.getDirection(), maxRange);
        Vector dest = trace != null && trace.getHitPosition() != null ? trace.getHitPosition()
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

        if (hitsStalactite(caster, loc)) {
            explode(caster, casterEntity);
            return;
        }

        ticks++;
        bat.teleport(loc);

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

    private boolean hitsStalactite(RPGEntity caster, Location loc) {
        // Stalactite Interaction is not LivingEntity, so isEnemy never matches — check
        // explicitly
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, HIT_RADIUS + 2, HIT_RADIUS + 2, HIT_RADIUS + 2)) {
            if (!(entity instanceof Interaction inter))
                continue;
            UUID id = inter.getUniqueId();
            Optional<RPGEntity> opt = EntityManager.getInstance().getEntity(id);
            if (opt.isEmpty() || !(opt.get() instanceof BukkitStalactiteEntity st))
                continue;
            Vector vec = new Vector(loc.getX(), loc.getY(), loc.getZ());
            if (StalactiteHitbox.isInsideTaperedStalactite(vec, st.getCeiling(), HIT_RADIUS))
                return true;
            if (inter.getLocation().distanceSquared(loc) <= (HIT_RADIUS + 1) * (HIT_RADIUS + 1))
                return true;
            // also check pivot distance for centered stalactite
            if (st.getPivot().distanceSquared(loc) <= (HIT_RADIUS + 2) * (HIT_RADIUS + 2))
                return true;
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

        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0.0, 0.0, 0.0, // Offsets (X, Y, Z)
                0.0);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

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

        // Stalactite handling: Interaction hitbox is not LivingEntity, so isEnemy
        // filtered it out.
        // Damage nearby stalactites via hitbox geometry with same MAGIC scaling, show
        // indicator at hitbox.
        Set<UUID> handledStalactites = new HashSet<>();
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, EXPLOSION_RADIUS + 3, EXPLOSION_RADIUS + 5,
                EXPLOSION_RADIUS + 3)) {
            if (!(entity instanceof Interaction inter))
                continue;
            UUID id = inter.getUniqueId();
            if (!handledStalactites.add(id))
                continue;
            Optional<RPGEntity> opt = EntityManager.getInstance().getEntity(id);
            if (opt.isEmpty() || !(opt.get() instanceof BukkitStalactiteEntity st))
                continue;
            if (!st.isDamageable())
                continue;
            // Check explosion within stalactite tapered hitbox (with explosion radius
            // margin) or close to hitbox
            Vector locVec = new Vector(loc.getX(), loc.getY(), loc.getZ());
            boolean inTaper = StalactiteHitbox.isInsideTaperedStalactite(locVec, st.getCeiling(), EXPLOSION_RADIUS);
            boolean nearHitbox = inter.getLocation().distanceSquared(loc) <= (EXPLOSION_RADIUS * EXPLOSION_RADIUS) + 4;
            if (!inTaper && !nearHitbox)
                continue;
            RPGDamageResult r = st.dealRPGDamage(caster, st, damage, DamageType.MAGIC);
            if (r.getResult() != DamageResult.DENY && r.getDamage() > 0) {
                // Show indicator at hitbox location per clarification (not pivot)
                Location hitboxLoc = inter.getLocation().clone().add(0, 1, 0);
                showMagicIndicator(hitboxLoc, r.getDamage(), r.getResult());
                playHitSound(inter, r.getResult());
            }
        }

        cancel();
        if (cooldownSink != null) {
            cooldownSink.startCooldown();
        }
    }

    /**
     * Enemy filter shared by the contact check and the AoE: living entities,
     * excluding the caster, other bat/bonemerang projectiles, ghosts, and same-team
     * entities (players and player-owned summons are allies; only cross-team
     * targets are valid).
     */
    private boolean isEnemy(RPGEntity caster, Entity entity) {
        if (!(entity instanceof LivingEntity le)) {
            return false;
        }
        if (BukkitEntityMetadata.has(le, METADATA) || BukkitEntityMetadata.has(le, "BONEMERANG")
                || BukkitEntityMetadata.has(le, BukkitStalactiteEntity.COLLIDER_METADATA)) {
            return false;
        }
        if (EntityManager.getInstance().isGhost(le.getUniqueId())) {
            return false;
        }
        return CombatRelation.isEnemy(caster, entity);
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

    private void showMagicIndicator(Location loc, double damage, DamageResult result) {
        if (damage <= 0 || loc == null) {
            return;
        }
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.showMagicDamage(loc, damage, result);
    }

    private void playHitSound(LivingEntity le, DamageResult result) {
        DMain plugin = DMain.getInstance();
        CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
        if (combatListener == null) {
            return;
        }
        combatListener.playProjectileHitSound(le, result);
    }

    private void playHitSound(Location loc, DamageResult result) {
        if (loc == null || loc.getWorld() == null)
            return;
        try {
            DMain plugin = DMain.getInstance();
            CombatListener combatListener = plugin == null ? null : plugin.getCombatListener();
            if (combatListener != null) {
                // CombatListener has no Location overload, fallback to generic projectile hit
                // sound via location
                // Use direct world sound as stalactite hitbox is Interaction, not LivingEntity
                loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            loc.getWorld().playSound(loc, Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
        } catch (Exception ignored) {
        }
    }

    private void playHitSound(Entity entity, DamageResult result) {
        if (entity instanceof LivingEntity le)
            playHitSound(le, result);
        else
            playHitSound(entity.getLocation(), result);
    }

}