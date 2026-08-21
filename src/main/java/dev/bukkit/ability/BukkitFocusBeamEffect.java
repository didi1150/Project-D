package dev.bukkit.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.bukkit.utils.ManaDiscountUtils;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.CooldownSink;
import dev.core.ability.CostEntry;
import dev.core.ability.Effect;
import dev.core.ability.impl.FocusBeamAbility;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Focus Beam — the Arcane Focus staff's channeled laser. A short inward-spiral
 * charge at the staff tip (rising warden-charge pitch) is followed by a beam
 * that follows the caster's view every tick, damaging the first enemy in line
 * with magic damage scaled by the caster's ABILITY_POWER
 * ({@code BASE_DAMAGE_PER_TICK * (1 + Ability Power)}, mirroring the Spirit
 * Sceptre formula).
 *
 * The beam is cancellable: a second right-click while it is up tears it down
 * instead of casting a new one. The cancelling click still travels through the
 * normal cast gate (so it needs to afford the configured cost), but its
 * deduction is refunded immediately — stopping a beam never costs anything on
 * top of the original cast.
 */
public class BukkitFocusBeamEffect extends Effect {

    private static final double MAX_RANGE = 30.0;
    private static final int CHARGE_TICKS = 20; // 1s inward spiral charge
    private static final int BEAM_TICKS = 40; // 2s discharge
    private static final double BASE_DAMAGE_PER_TICK = 3.0;
    private static final Particle HELIX_PARTICLE = Particle.END_ROD;
    /** Ticks between magic damage indicators, so the channel doesn't spam text displays. */
    private static final int INDICATOR_INTERVAL_TICKS = 5;

    /**
     * One live beam per caster, keyed by uuid. A cast while an entry exists
     * cancels that beam instead of starting a new one (the toggle-off click).
     */
    private static final Map<UUID, BukkitFocusBeamEffect> ACTIVE_BEAMS = new HashMap<>();

    private RPGEntity caster;
    private int currentTick;
    private boolean finished;

    public BukkitFocusBeamEffect(String cooldownKey) {
        super(null, (CHARGE_TICKS + BEAM_TICKS) * 50L, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        BukkitFocusBeamEffect active = ACTIVE_BEAMS.get(caster.getUuid());
        if (active != null && !active.finished) {
            // Second right-click while charging/firing: tear the beam down,
            // refund this click's cost and leave no new beam behind.
            active.finish();
            refundCastCost(caster);
            return;
        }
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null) {
            return;
        }
        this.caster = caster;
        ACTIVE_BEAMS.put(caster.getUuid(), this);
        casterEntity.getWorld().playSound(casterEntity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (finished || this.caster == null) {
            return;
        }
        LivingEntity casterEntity = resolveEntity(caster);
        if (casterEntity == null || !casterEntity.isValid() || casterEntity.isDead()) {
            finish();
            return;
        }

        World world = casterEntity.getWorld();
        Location eye = casterEntity.getEyeLocation();
        Vector eyeDir = eye.getDirection().normalize();

        // Staff tip origin calculation (offset to right hand)
        Vector staffOrigin = staffOrigin(eye, eyeDir);

        // --- PHASE 1: INWARD SPIRAL CHARGE ANIMATION ---
        if (currentTick < CHARGE_TICKS) {
            renderInwardSpiralCharge(world, staffOrigin, eyeDir, currentTick);

            // Pitch increases as the charge gets closer to firing
            float pitch = 0.8f + (1.2f * ((float) currentTick / CHARGE_TICKS));
            world.playSound(staffOrigin.toLocation(world), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.3f, pitch);

            currentTick++;
            return;
        }

        // Play blast sound at the moment of discharge
        if (currentTick == CHARGE_TICKS) {
            world.playSound(staffOrigin.toLocation(world), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.8f);
            world.playSound(staffOrigin.toLocation(world), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 2.0f);
            world.playSound(casterEntity, Sound.ENTITY_BEE_LOOP, 1.0f, 0.1f);
            world.playSound(casterEntity, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 0.1f);
        }

        // --- PHASE 2: BEAM DISCHARGE ---
        RayTraceResult rayTrace = world.rayTrace(eye, eyeDir, MAX_RANGE, FluidCollisionMode.NEVER, true, 0.4,
                this::isBeamTarget);

        Vector targetPoint = rayTrace != null && rayTrace.getHitPosition() != null ? rayTrace.getHitPosition()
                : eye.toVector().add(eyeDir.clone().multiply(MAX_RANGE));

        if (rayTrace != null && rayTrace.getHitEntity() instanceof LivingEntity target) {
            damageTarget(casterEntity, target);
        }

        Vector beamVector = targetPoint.clone().subtract(staffOrigin);
        double beamLength = beamVector.length();
        Vector beamDir = beamVector.normalize();

        // Render white core & custom helix
        renderFastCore(world, staffOrigin, beamDir, beamLength);
        renderFastHelix(world, staffOrigin, beamDir, beamLength, currentTick);

        // Impact spark effect
        if (rayTrace != null && rayTrace.getHitPosition() != null) {
            Location hitLoc = rayTrace.getHitPosition().toLocation(world);
            world.spawnParticle(Particle.LAVA, hitLoc, 2, 0.05, 0.05, 0.05, 0.05);
        }

        currentTick++;

        if (currentTick >= CHARGE_TICKS + BEAM_TICKS) {
            finish();
        }
    }

    @Override
    public void cancel() {
        finish();
    }

    /**
     * Idempotent teardown: deregisters the beam, stops the looping discharge
     * ambience for everyone who could hear it. Runs on natural expiry, on the
     * cancelling right-click, on caster death/logout and via the manager's
     * cancelAll on shutdown.
     */
    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (caster != null) {
            ACTIVE_BEAMS.remove(caster.getUuid(), this);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.stopSound(Sound.ENTITY_BEE_LOOP);
            player.stopSound(Sound.ENTITY_ENDER_DRAGON_GROWL);
        }
    }

    /**
     * Returns the cost the effect manager deducted for THIS (cancelling) click,
     * re-resolving it exactly like {@code BukkitEffectManager.resolveCharges}
     * (formula against the caster, mana discount applied). The original cast's
     * cost stays spent.
     */
    private void refundCastCost(RPGEntity caster) {
        AbilityRegistry.get(FocusBeamAbility.ID).ifPresent(ability -> {
                    for (CostEntry cost : ability.getCost().getCosts()) {
                        double base = cost.resolve(caster);
                        double paid = ManaDiscountUtils.discountedCost(caster, cost.mode().getResourceType(), base);
                        caster.getStatManager().modifyStat(StatType.valueOf(cost.mode().getResourceType()), paid);
                    }
                });
    }

    /**
     * Magic damage for one beam tick, following the standard ability formula
     * Damage = Base Damage * (1 + Ability Power), further scaled by the
     * caster's ability damage multiplier. Registered RPG targets go through
     * the central pipeline (defenses, crits, events); unregistered vanilla mobs
     * fall back to direct damage so CombatListener still renders the hit.
     */
    private void damageTarget(LivingEntity casterEntity, LivingEntity target) {
        long now = System.currentTimeMillis();
        double abilityPower = caster.getStatEngineAdapter().getCurrentValue(StatType.ABILITY_POWER, now);
        double damage = BASE_DAMAGE_PER_TICK * (1.0 + abilityPower) * caster.getAbilityDamageMultiplier();

        RPGDamageResult result = EntityManager.getInstance().getEntity(target.getUniqueId())
                .map(rpgTarget -> rpgTarget.dealRPGDamage(caster, rpgTarget, damage, DamageType.MAGIC))
                .orElse(null);
        if (result != null) {
            if (result.getResult() != DamageResult.DENY && currentTick % INDICATOR_INTERVAL_TICKS == 0) {
                showMagicIndicator(target, result.getDamage(), result.getResult());
                playHitSound(target, result.getResult());
            }
            return;
        }
        // Vanilla mob: damageMob fires an EntityDamageByEntityEvent that
        // CombatListener already renders an indicator for, so only the hit
        // sound is added here.
        DamageUtils.damageMob(target, damage, casterEntity);
        if (currentTick % INDICATOR_INTERVAL_TICKS == 0) {
            playHitSound(target, DamageResult.NORMAL);
        }
    }

    /**
     * Ray trace filter: the beam stops at the first valid enemy and passes
     * through everything else (allies, ghosts, armor stands, projectiles).
     */
    private boolean isBeamTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living instanceof ArmorStand) {
            return false;
        }
        if (EntityManager.getInstance().isGhost(living.getUniqueId())) {
            return false;
        }
        return CombatRelation.isEnemy(caster, living);
    }

    /**
     * Resolves the Bukkit living entity backing a caster, so the beam can be
     * channelled by players AND mobs (mobs share the vanilla entity's uuid).
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

    /**
     * Staff tip origin: eye position offset to the right hand and pushed
     * forward along the look direction.
     */
    private static Vector staffOrigin(Location eye, Vector eyeDir) {
        Vector right = eyeDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        if (Double.isNaN(right.getX()) || right.lengthSquared() == 0) {
            right = new Vector(1, 0, 0);
        }
        return eye.toVector().add(right.clone().multiply(0.4)).add(new Vector(0, -0.2, 0))
                .add(eyeDir.clone().multiply(0.5));
    }

    /**
     * Renders a ring in front of the staff tip that spirals inward toward the
     * focal point.
     */
    private static void renderInwardSpiralCharge(World world, Vector staffOrigin, Vector eyeDir, int tick) {
        // Coordinate space perpendicular to the eye direction
        Vector u = Math.abs(eyeDir.getY()) > 0.9 ? eyeDir.clone().crossProduct(new Vector(1, 0, 0)).normalize()
                : eyeDir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector w = eyeDir.clone().crossProduct(u).normalize();

        // Fraction going from 1.0 (start of charge) down to 0.0 (fully charged)
        double progress = 1.0 - ((double) tick / CHARGE_TICKS);

        double maxRadius = 1.2; // Starting ring width
        double currentRadius = maxRadius * progress; // Shrinks down to 0.0

        // Plane is offset slightly forward along line of sight
        Vector planeCenter = staffOrigin.clone().add(eyeDir.clone().multiply(0.3 * progress));

        // Spawn multiple spiraling strands around the shrinking circumference
        int strands = 4;
        double rotationOffset = tick * 0.5; // Spins around the center as it shrinks

        for (int i = 0; i < strands; i++) {
            double angle = ((2 * Math.PI / strands) * i) + rotationOffset;

            double x = Math.cos(angle) * currentRadius;
            double y = Math.sin(angle) * currentRadius;

            Vector particlePos = planeCenter.clone().add(u.clone().multiply(x)).add(w.clone().multiply(y));

            Location loc = particlePos.toLocation(world);

            // Flame particles draw in toward center
            world.spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0);

            // Add a dense inner glowing focal spot at the staff tip
            world.spawnParticle(Particle.INSTANT_EFFECT, staffOrigin.toLocation(world), 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private static void renderFastCore(World world, Vector origin, Vector dir, double length) {
        for (double d = 0; d < length; d += 0.25) {
            Location pLoc = origin.clone().add(dir.clone().multiply(d)).toLocation(world);
            world.spawnParticle(Particle.INSTANT_EFFECT, pLoc, 1, 0, 0, 0, 0);
            if (d % 0.5 == 0) {
                world.spawnParticle(Particle.DUST_PLUME, pLoc, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void renderFastHelix(World world, Vector origin, Vector dir, double length, int tick) {
        Vector u = Math.abs(dir.getY()) > 0.9 ? dir.clone().crossProduct(new Vector(1, 0, 0)).normalize()
                : dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector w = dir.clone().crossProduct(u).normalize();

        double radius = 0.25;
        double frequency = 1.2;
        double rotationSpeed = tick * 0.6;

        for (double d = 0; d < length; d += 0.4) {
            double angle = (d * frequency) + rotationSpeed;

            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;
            Vector offset1 = u.clone().multiply(cos).add(w.clone().multiply(sin));
            Location point1 = origin.clone().add(dir.clone().multiply(d)).add(offset1).toLocation(world);
            world.spawnParticle(HELIX_PARTICLE, point1, 1, 0, 0, 0, 0);

            Vector offset2 = u.clone().multiply(-cos).add(w.clone().multiply(-sin));
            Location point2 = origin.clone().add(dir.clone().multiply(d)).add(offset2).toLocation(world);
            world.spawnParticle(HELIX_PARTICLE, point2, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Renders the magical ✦ damage indicator, mirroring the Spirit Sceptre's
     * reuse of the central damage-number machinery.
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
