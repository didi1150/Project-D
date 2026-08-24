package dev.bukkit.ability;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.utils.StealthRegistry;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;

/**
 * Smoke Shroud active effect for Orb of Stealth.
 * Places a smoke cloud (r=5) around caster for 6s.
 * While inside and not recently revealed, player is invisible to mobs.
 * Re-entering clears aggro; attacking or leaving reveals briefly (1.5s).
 */
public class BukkitSmokeShroudEffect extends Effect {

    private static final long DURATION_MS = 6000L;
    private static final double RADIUS = 5.0;
    private static final double RADIUS_SQ = RADIUS * RADIUS;
    private static final long REVEAL_MS = 1500L;

    // smoke grid radius for visuals
    private static final double[] OFFSETS = {-2.5, -1.5, -0.5, 0.5, 1.5, 2.5};

    private RPGEntity caster;
    private Location center;
    private boolean cleanedUp = false;
    private boolean wasInside = false;
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
        if (!(e.getDamager() instanceof Player p)) return;
        if (!p.getUniqueId().equals(caster.getUuid())) return;
        if (e.isCancelled()) return;
        if (e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        // reveal shrouded holder briefly when attacking
        if (StealthRegistry.hasShroud(p.getUniqueId())) {
            StealthRegistry.reveal(p.getUniqueId(), REVEAL_MS);
            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().clone().add(0,1,0), 8, 0.3,0.3,0.3,0.02);
            p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().clone().add(0,1,0), 4, 0.2,0.2,0.2,0.02);
        }
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
        // render smoke
        renderSmoke(center);
        // inside check
        boolean inside = player.getLocation().distanceSquared(center) <= RADIUS_SQ;
        long remaining = StealthRegistry.getRemainingMs(player.getUniqueId());
        if (remaining <= 0) {
            cleanup();
            return;
        }
        boolean isRevealed = false;
        // check reveal window by querying shroud's revealUntil via isShrouded false when revealed
        // isShrouded returns false when revealed, so we can infer revealed = hasShroud && !isShrouded && inside
        boolean shrouded = StealthRegistry.isShrouded(player);
        if (inside && !shrouded && StealthRegistry.hasShroud(player.getUniqueId())) {
            isRevealed = true;
        }
        if (inside && shrouded) {
            // while inside, continuously clear any mob that (re)targets you
            StealthRegistry.clearAggro(player);
            if (!wasInside) {
                player.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0,0.5,0), 12, 1.0,0.5,1.0,0.02);
                player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.1f);
            }
        } else if (!inside && wasInside) {
            // just left shroud -> brief reveal (visible even if re-enter quickly)
            StealthRegistry.reveal(player.getUniqueId(), REVEAL_MS);
        }
        wasInside = inside;
    }

    private void renderSmoke(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        // dense base cloud every tick — much thicker than before
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0,0.3,0), 8, 1.4,0.4,1.4,0.03);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0,1.0,0), 10, 1.6,0.5,1.6,0.03);
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0,0.6,0), 4, 1.2,0.3,1.2,0.02);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0,0.8,0), 6, 1.3,0.4,1.3,0.02);
        // dense grid of smoke at y steps like inspiration — increased density
        for (double y = 0; y <= 2.2; y += 0.5) {
            for (double x : OFFSETS) {
                for (double z : OFFSETS) {
                    double dist = x*x + z*z;
                    if (dist < 0.8 || dist > 9.5) continue;
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
                double rx = Math.cos(rad) * 2.4;
                double rz = Math.sin(rad) * 2.4;
                Location ring = loc.clone().add(rx, y, rz);
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ring, 1, 0.1,0.05,0.1,0.01);
            }
        }
        // extra large puffs
        w.spawnParticle(Particle.CLOUD, loc.clone().add(0,1.2,0), 3, 1.6,0.3,1.6,0.02);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0,0.2,0), 5, 1.5,0.2,1.5,0.02);
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
