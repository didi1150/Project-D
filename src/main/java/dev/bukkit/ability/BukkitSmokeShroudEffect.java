package dev.bukkit.ability;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.utils.StealthRegistry;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;

/**
 * Smoke Shroud active effect for Orb of Stealth.
 * Places a smoke cloud (r=7) around caster for 6s.
 * While inside and not recently revealed, player is invisible to mobs —
 * shroud blocks AGGRO only, never damage (hits that still connect land).
 * Re-entering clears aggro; attacking or leaving reveals briefly (1.5s).
 */
public class BukkitSmokeShroudEffect extends Effect {

    private static final long DURATION_MS = 6000L;
    private static final double RADIUS = 7.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;
    private static final long REVEAL_MS = 1500L;

    // smoke grid radius for visuals
    private static final double[] OFFSETS = {-3.5, -2.5, -1.5, -0.5, 0.5, 1.5, 2.5, 3.5};

    private RPGEntity caster;
    private Location center;
    private boolean cleanedUp = false;
    private boolean wasInside = false;
    private boolean wasShrouded = false;
    private long ticksAlive = 0;
    private EventAction<EntityDamageByEntityEvent> dmgAction;

    public BukkitSmokeShroudEffect(String cooldownKey) {
        super(null, DURATION_MS + 500L, true, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        LivingEntity ent = resolveEntity(caster);
        if (!(ent instanceof Player player)) return;
        this.caster = caster;
        this.center = player.getLocation().clone();
        // center at feet +1 as in inspiration: getHighestLocation logic approximated as player loc
        this.cleanedUp = false;
        this.wasInside = false;
        this.wasShrouded = false;
        this.ticksAlive = 0;
        StealthRegistry.placeShroud(player.getUniqueId(), center, RADIUS, DURATION_MS);
        cooldownSink.startCooldown();
        player.getWorld().playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 0.9f);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.4f);
        // immediately clear aggro for mobs already targeting player
        StealthRegistry.clearAggro(player);
        // subscribe to damage for reveal
        dmgAction = new EventAction<>(this::onDamage, EntityDamageByEntityEvent.class);
        BukkitEventBus.getInstance().subscribe(dmgAction);
    }

    private void onDamage(EntityDamageByEntityEvent e) {
        if (cleanedUp) return;
        if (caster == null) return;
        // The offensive hit must come from the caster: melee swings have the
        // player as damager; arrows/tridents carry them as the projectile's shooter.
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        if (!attacker.getUniqueId().equals(caster.getUuid())) return;
        if (e.isCancelled()) return;
        if (!isRevealingCause(e.getCause())) return;
        // reveal shrouded holder briefly when attacking
        if (StealthRegistry.hasShroud(attacker.getUniqueId())) {
            StealthRegistry.reveal(attacker.getUniqueId(), REVEAL_MS);
            attacker.getWorld().spawnParticle(Particle.SMOKE, attacker.getLocation().clone().add(0,1,0), 8, 0.3,0.3,0.3,0.02);
            attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().clone().add(0,1,0), 4, 0.2,0.2,0.2,0.02);
        }
    }

    /** The player behind a damage source: the entity itself for melee, the shooter for projectiles. */
    private static Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    /**
     * Landed offensive hits reveal the attacker: melee swings and sweeps, plus
     * the caster's own projectiles and magic hits so bow/mage play cannot stay
     * hidden while dealing damage. (Ability damage routed through the RPG
     * pipeline fires no Bukkit event at all and remains outside this hook.)
     */
    private static boolean isRevealingCause(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE
                || cause == EntityDamageEvent.DamageCause.MAGIC;
    }

    @Override
    public void tick(RPGEntity caster, long now) {
        if (cleanedUp) return;
        if (hasExpired(now)) {
            cleanup();
            return;
        }
        Player player = resolvePlayer(caster);
        if (player == null || !player.isOnline() || player.isDead()) {
            cleanup();
            return;
        }
        if (center == null || center.getWorld() == null) {
            cleanup();
            return;
        }
        // Cross-world travel would make distanceSquared below throw and kill
        // the whole game-tick loop; a cast shroud is location-bound, so end it.
        if (!player.getWorld().equals(center.getWorld())) {
            cleanup();
            return;
        }
        // render smoke
        renderSmoke(center);
        long remaining = StealthRegistry.getRemainingMs(player.getUniqueId());
        if (remaining <= 0) {
            cleanup();
            return;
        }
        // inside check
        boolean inside = player.getLocation().distanceSquared(center) <= RADIUS_SQ;
        boolean shrouded = StealthRegistry.isShrouded(player);
        ticksAlive++;
        boolean safetySlot = ticksAlive % ShroudTickDecision.SAFETY_CLEAR_INTERVAL_TICKS == 0;
        ShroudTickDecision decision = ShroudTickDecision.evaluate(inside, shrouded, wasInside, wasShrouded, safetySlot);

        if (decision.clearAggroNow()) {
            // Rising edge into SHROUDED (cast, re-entry, attack-reveal expiry)
            // drops held targets once; the throttled slot is a safety net for
            // anything that slipped past the EntityTargetLivingEntityEvent gate.
            StealthRegistry.clearAggro(player);
        }
        if (decision.enterCue()) {
            player.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0,0.5,0), 12, 1.0,0.5,1.0,0.02);
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.1f);
        }
        if (decision.exitReveal()) {
            // just left shroud -> brief reveal (documented penalty)
            StealthRegistry.reveal(player.getUniqueId(), REVEAL_MS);
        }
        wasInside = inside;
        wasShrouded = shrouded;
    }

    private void renderSmoke(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        // dense base cloud every tick — much thicker than before
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0,0.3,0), 8, 2.0,0.4,2.0,0.03);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0,1.0,0), 10, 2.2,0.5,2.2,0.03);
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0,0.6,0), 4, 1.7,0.3,1.7,0.02);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0,0.8,0), 6, 1.8,0.4,1.8,0.02);
        // dense grid of smoke at y steps like inspiration — increased density
        for (double y = 0; y <= 2.2; y += 0.5) {
            for (double x : OFFSETS) {
                for (double z : OFFSETS) {
                    double dist = x*x + z*z;
                    if (dist < 1.6 || dist > 18.6) continue;
                    Location spawn = loc.clone().add(x, y, z);
                    // much denser: 60% chance instead of 25%
                    if (Math.random() < 0.6) {
                        w.spawnParticle(Particle.SMOKE, spawn, 1, 0.08,0.08,0.08,0.015);
                        if (Math.random() < 0.35) w.spawnParticle(Particle.CLOUD, spawn, 1, 0.06,0.06,0.06,0.01);
                    }
                }
            }
            // ring of cosy smoke around perimeter at each layer
            for (int deg = 0; deg < 360; deg += 30) {
                double rad = Math.toRadians(deg);
                double rx = Math.cos(rad) * 3.4;
                double rz = Math.sin(rad) * 3.4;
                Location ring = loc.clone().add(rx, y, rz);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ring, 1, 0.1,0.05,0.1,0.01);
            }
        }
        // extra large puffs
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0,1.2,0), 3, 2.2,0.3,2.2,0.02);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0,0.2,0), 5, 2.1,0.2,2.1,0.02);
    }

    @Override
    public void cancel() {
        cleanup();
    }

    private void cleanup() {
        if (cleanedUp) return;
        cleanedUp = true;
        if (dmgAction != null) {
            try { BukkitEventBus.getInstance().unsubscribe(dmgAction); } catch (Exception ignored) {}
            dmgAction = null;
        }
        if (caster != null) {
            Player p = resolvePlayer(caster);
            if (p != null) {
                // ensure shroud removed after expiry window; keep reveal logic until natural expiry
                // but we remove shroud now to hide
                StealthRegistry.removeShroud(p.getUniqueId());
                if (p.getWorld() != null && center != null) {
                    p.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0,0.5,0), 6, 1.2,0.4,1.2,0.02);
                    p.getWorld().playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.3f);
                }
            } else {
                // fallback clear by uuid
                StealthRegistry.removeShroud(caster.getUuid());
            }
        }
    }

    private static LivingEntity resolveEntity(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity pe) return pe.getPlayer().orElse(null);
        if (Bukkit.getServer() == null) return null;
        var e = Bukkit.getEntity(caster.getUuid());
        return e instanceof LivingEntity le ? le : null;
    }

    private static Player resolvePlayer(RPGEntity caster) {
        LivingEntity le = resolveEntity(caster);
        return le instanceof Player p ? p : null;
    }
}
