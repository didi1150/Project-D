package dev.bukkit.ability.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.bukkit.DMain;
import dev.bukkit.ability.BukkitTriVolleyEffect;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.TriHomingHudFormatter;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.item.BowArrowManager;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Per-holder Trinity Bow behavior. Passive: every bow shot cancels the vanilla
 * arrow and fans {@link #HOMING_ARROWS} homing arrows that steer toward the
 * nearest enemy within {@link #HOMING_RADIUS} blocks (same mob allowed, no
 * falloff, consumed on first hit — damage flows through the vanilla
 * EntityDamageByEntityEvent so CombatListener's impact sound plays). Also owns
 * the TRI_VOLLEY arrows' pierce resolution and the "tri:ready" HUD fragment.
 */
public class TriVolleyBehavior implements AbilityBehavior {

    public static final String ITEM_ID = "TRI_HOMING_BOW";

    private static final int HOMING_ARROWS = 3;
    private static final double HOMING_RADIUS = 10.0;
    private static final int HOMING_TICKS = 60;
    private static final float HOMING_SPEED = 2.3f; // fallback if vanilla velocity unavailable
    private static final float HOMING_SPREAD = 4.0f;
    private static final double FAN_STEP_DEG = 4.0;
    // Steering: vanilla arrows lose ~0.05 velocity-Y per tick to gravity; each
    // tick we may correct at most a fraction of the arrow's speed toward the
    // pursuit direction, relaxed near the target so terminal guidance connects
    // instead of orbiting.
    private static final double ARROW_GRAVITY = 0.05;
    private static final double TURN_RATE_FAR = 0.2;
    private static final double TURN_RATE_CLOSE = 0.45;
    private static final double TERMINAL_RANGE_SQ = 9.0; // (3 blocks)^2

    private static final Color TRI_COLOR = Color.fromRGB(0xD946FF);

    // Arrow UUID -> steering task; self-cancels when the arrow dies/lands/expires
    private static final Map<UUID, BukkitTask> HOMING_TASKS = new ConcurrentHashMap<>();

    private ActiveAbility ctx;

    public TriVolleyBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions()
                .subscribe(new EventAction<>(this::onShoot, EntityShootBowEvent.class, EventAction.HIGHEST_PRIORITY));
        ctx.getSubscriptions().subscribe(new EventAction<>(this::onQuit, PlayerQuitEvent.class));
        showHudForHolder();
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        hideHudForHolder();
    }

    // ---- Shoot: replace vanilla arrow with a fan of homing arrows ----

    private void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (!player.getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        if (!(event.getProjectile() instanceof Arrow vanilla))
            return;
        ItemStack bow = event.getBow();
        if (bow == null)
            bow = player.getInventory().getItemInMainHand();
        if (!isTriBow(bow))
            return;

        // cancel vanilla single arrow
        event.setCancelled(true);
        vanilla.remove();

        Location eye = player.getEyeLocation();
        Vector baseDir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null)
            return;

        RPGEntity shooterRpg = EntityManager.getInstance().getEntity(player.getUniqueId()).orElse(null);
        double baseDamage = 0;
        if (shooterRpg != null) {
            baseDamage = shooterRpg.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                    System.currentTimeMillis()) * shooterRpg.getProjectileDamageMultiplier();
        }

        // Use vanilla arrow velocity directly — matches bow draw (max at full draw, min
        // at tap)
        float drawSpeed = (float) vanilla.getVelocity().length();
        if (drawSpeed < 0.1f) {
            try {
                drawSpeed = event.getForce() * 3.0f;
            } catch (Exception ignored) {
            }
        }
        float forcePitch = Math.max(0f, Math.min(1f, drawSpeed / 3.0f));

        world.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.95f, 0.9f + forcePitch * 0.6f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, eye, 8, 0.2, 0.2, 0.2, 0.05);
        world.spawnParticle(Particle.WITCH, eye.clone().add(0, -0.2, 0), 10, 0.3, 0.3, 0.3, 0.02);

        for (int i = 0; i < HOMING_ARROWS; i++) {
            int offIdx = i - HOMING_ARROWS / 2; // -1,0,1
            double yawDeg = offIdx * FAN_STEP_DEG + (i == 1 ? 0 : (Math.random() - 0.5) * 2.0);
            Vector dir = rotateYaw(baseDir.clone(), yawDeg).normalize();
            Location spawn = eye.clone().add(dir.clone().multiply(0.4));
            Arrow arrow = world.spawnArrow(spawn, dir, drawSpeed, HOMING_SPREAD);
            arrow.setShooter(player);
            arrow.setCritical(false);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setGlowing(false);
            var pdc = arrow.getPersistentDataContainer();
            pdc.set(BukkitTriVolleyEffect.HOMING_KEY, PersistentDataType.BOOLEAN, true);
            pdc.set(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, baseDamage);
            pdc.set(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN, false);
            startTrail(arrow);
            startHoming(arrow, player, shooterRpg);
        }
    }

    private void startTrail(Arrow arrow) {
        Plugin plugin = DMain.getInstance();
        if (plugin == null || !plugin.isEnabled())
            return;
        Particle.DustOptions dust = new Particle.DustOptions(TRI_COLOR, 1.45f);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                    cancel();
                    return;
                }
                Location l = arrow.getLocation();
                World w = l.getWorld();
                if (w == null) {
                    cancel();
                    return;
                }
                w.spawnParticle(Particle.DUST, l, 2, 0.02, 0.02, 0.02, 0, dust);
                if (arrow.getTicksLived() % 3 == 0)
                    w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.05, 0.05, 0.05, 0.02);
                if (arrow.getTicksLived() % 4 == 0)
                    w.spawnParticle(Particle.WITCH, l, 1, 0.06, 0.06, 0.06, 0.01);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void startHoming(Arrow arrow, LivingEntity shooter, RPGEntity shooterRpg) {
        Plugin plugin = DMain.getInstance();
        if (plugin == null || !plugin.isEnabled())
            return;
        UUID arrowId = arrow.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    HOMING_TASKS.remove(arrowId);
                    cancel();
                    return;
                }
                ticks++;
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround() || ticks > HOMING_TICKS) {
                    HOMING_TASKS.remove(arrowId);
                    cancel();
                    return;
                }
                if (ticks > 2) { // slight delay so initial trajectory is readable
                    LivingEntity target = nearestEnemy(arrow.getLocation(), shooter, shooterRpg);
                    if (target != null) {
                        Vector cur = arrow.getVelocity();
                        double speed = cur.length();
                        if (speed < 0.3)
                            speed = HOMING_SPEED;
                        // Aim for the hitbox's centre of mass, not the eyes:
                        // chasing the eye position makes close passes orbit the
                        // target's head and fly loops instead of connecting.
                        Vector to = target.getLocation().add(0, target.getHeight() * 0.5, 0).toVector()
                                .subtract(arrow.getLocation().toVector());
                        double distSq = to.lengthSquared();
                        Vector desired = to.normalize().multiply(speed);
                        // Compensate vanilla gravity so the pursuit curve stays
                        // flat instead of sagging low then snapping upward.
                        desired.setY(desired.getY() + ARROW_GRAVITY);
                        desired.normalize().multiply(speed);
                        // Clamp the per-tick correction to a fraction of speed:
                        // smooth pursuit arcs that converge, rather than a full
                        // re-aim every tick which overcorrects and circles.
                        Vector steer = desired.subtract(cur);
                        double turnRate = distSq <= TERMINAL_RANGE_SQ ? TURN_RATE_CLOSE : TURN_RATE_FAR;
                        double maxTurn = speed * turnRate;
                        if (steer.length() > maxTurn)
                            steer.normalize().multiply(maxTurn);
                        arrow.setVelocity(cur.add(steer));
                        arrow.setTicksLived(1); // prevent vanilla gravity from stacking
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        HOMING_TASKS.put(arrowId, task);
    }

    private static void cancelHoming(UUID arrowId) {
        BukkitTask t = HOMING_TASKS.remove(arrowId);
        if (t != null) {
            try {
                t.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private static LivingEntity nearestEnemy(Location loc, LivingEntity shooter, RPGEntity shooterRpg) {
        World w = loc.getWorld();
        if (w == null)
            return null;
        LivingEntity best = null;
        double bestDistSq = HOMING_RADIUS * HOMING_RADIUS;
        for (org.bukkit.entity.Entity e : w.getNearbyEntities(loc, HOMING_RADIUS, HOMING_RADIUS, HOMING_RADIUS)) {
            if (!(e instanceof LivingEntity le))
                continue;
            if (le.getUniqueId().equals(shooter.getUniqueId()))
                continue;
            if (EntityManager.getInstance().isGhost(le.getUniqueId()))
                continue;
            if (le.hasMetadata("BONEMERANG") || le.hasMetadata("SPIRIT_BAT"))
                continue;
            if (shooterRpg != null && !CombatRelation.isEnemy(shooterRpg, e))
                continue;
            // nearest within radius from arrow, not shooter
            double d = le.getLocation().distanceSquared(loc);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = le;
            }
        }
        return best;
    }

    private static Vector rotateYaw(Vector dir, double yawDeg) {
        double rad = Math.toRadians(yawDeg);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double x = dir.getX() * cos - dir.getZ() * sin;
        double z = dir.getX() * sin + dir.getZ() * cos;
        return new Vector(x, dir.getY(), z);
    }

    // ---- Projectile hit: consume homing arrows, resolve volley pierce ----

    /**
     * Global handler (registered once in DMain, independent of any holder's
     * ability bindings): players routinely swap away from the bow while arrows
     * are still in flight, which unbinds per-holder subscriptions — a
     * bind-scoped hit handler would then never run, leaving homing arrows to
     * steer (and visibly circle) until their expiry. Shooter context is
     * resolved from the arrow itself instead.
     */
    public static void onGlobalProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow))
            return;
        if (!(arrow.getShooter() instanceof Player p))
            return;
        var pdc = arrow.getPersistentDataContainer();
        boolean isHoming = Boolean.TRUE.equals(pdc.get(BukkitTriVolleyEffect.HOMING_KEY, PersistentDataType.BOOLEAN));
        boolean isVolley = Boolean.TRUE.equals(pdc.get(BukkitTriVolleyEffect.VOLLEY_KEY, PersistentDataType.BOOLEAN));
        if (!isHoming && !isVolley)
            return;

        // homing arrows are consumed on any hit (block or entity); vanilla
        // damage still processes so the standard impact sound plays
        if (isHoming) {
            cancelHoming(arrow.getUniqueId());
            BukkitTriVolleyEffect.cleanupPierce(arrow);
            Plugin plugin = DMain.getInstance();
            if (event.getHitEntity() != null) {
                // keep arrow for one tick so damage processes, then remove;
                // vanilla consumes it itself when the damage lands normally
                if (plugin != null && plugin.isEnabled()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (arrow.isValid())
                                arrow.remove();
                        }
                    }.runTaskLater(plugin, 1L);
                } else if (arrow.isValid()) {
                    arrow.remove();
                }
            } else {
                // block hit: remove immediately
                arrow.remove();
            }
            return;
        }

        // volley pierce: allow piercing up to VOLLEY_PIERCE entities
        if (event.getHitEntity() instanceof LivingEntity hit) {
            boolean shouldContinue = BukkitTriVolleyEffect.handlePierceHit(arrow, hit);
            if (shouldContinue) {
                event.setCancelled(true);
                // push slightly forward to avoid immediate re-hit
                RPGEntity rpg = EntityManager.getInstance().getEntity(p.getUniqueId()).orElse(null);
                if (rpg != null) {
                    double baseDamage = rpg.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                            System.currentTimeMillis()) * rpg.getProjectileDamageMultiplier();
                    DamageUtils.damageEntity(hit, baseDamage, rpg, DamageType.PHYSICAL);
                }
                arrow.setVelocity(arrow.getVelocity().clone().normalize().multiply(0.3));
                // keep arrow alive
            } else {
                // pierce cap reached -> let it die
                BukkitTriVolleyEffect.cleanupPierce(arrow);
            }
        } else if (event.getHitBlock() != null) {
            // volley hits block -> remove
            BukkitTriVolleyEffect.cleanupPierce(arrow);
            arrow.remove();
        }
    }

    // ---- HUD & lifecycle ----

    /**
     * Deferred by one tick via {@link BehaviorScheduler}: see HunterBowBehavior for
     * the swap-timing rationale.
     */
    private void showHudForHolder() {
        UUID uuid = ctx.getHolder().getUuid();
        BehaviorScheduler.runNextTick(() -> {
            try {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !isTriBow(p.getInventory().getItemInMainHand()))
                    return;
                HudOverlayService.getInstance().show(p, "tri:ready", TriHomingHudFormatter.formatReady(), 0, 10);
            } catch (Exception ignored) {
            }
        });
    }

    private void hideHudForHolder() {
        try {
            Player p = Bukkit.getPlayer(ctx.getHolder().getUuid());
            if (p == null)
                return;
            HudOverlayService hud = HudOverlayService.getInstance();
            hud.hide(p, "tri:ready");
            hud.hide(p, "tri:volley");
        } catch (Exception ignored) {
        }
    }

    private void onQuit(PlayerQuitEvent e) {
        if (!e.getPlayer().getUniqueId().equals(ctx.getHolder().getUuid()))
            return;
        hideHudForHolder();
    }

    public static boolean isTriBow(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        return ITEM_ID.equals(id);
    }
}
