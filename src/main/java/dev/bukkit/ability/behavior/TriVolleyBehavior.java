package dev.bukkit.ability.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
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
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.TriHomingHudFormatter;
import dev.bukkit.item.BowArrowManager;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.CombatRelation;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.ability.CooldownSink;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.stat.StatType;

/**
 * Per-holder Trinity Bow behavior. Passive: every bow shot cancels the vanilla
 * arrow and fans {@link #HOMING_ARROWS} homing arrows that steer toward the
 * nearest enemy within {@link #HOMING_RADIUS} blocks (same mob allowed, no
 * falloff, consumed on first hit — damage flows through the vanilla
 * EntityDamageByEntityEvent so CombatListener's impact sound plays). Left click
 * TOGGLES Scatter Volley: while armed, the next bow shot fans
 * {@link #VOLLEY_ARROWS} arrows with vanilla piercing ({@code setPierceLevel})
 * instead of the homing fan; clicking again cancels. The armed state survives
 * item swaps (cleared on quit) and the TRI_VOLLEY cooldown only starts once the
 * volley is actually fired — see {@link #toggleVolley}. Also owns the
 * "tri:ready"/"tri:volley" HUD fragments.
 */
public class TriVolleyBehavior implements AbilityBehavior {

    public static final String ITEM_ID = "TRI_HOMING_BOW";

    public static final NamespacedKey HOMING_KEY = new NamespacedKey("project_d", "tri_homing");
    public static final NamespacedKey VOLLEY_KEY = new NamespacedKey("project_d", "tri_volley");

    private static final int HOMING_ARROWS = 3;
    private static final double HOMING_RADIUS = 10.0;
    private static final int HOMING_TICKS = 60;
    private static final float HOMING_SPEED = 2.3f; // fallback if vanilla velocity unavailable
    private static final float HOMING_SPREAD = 4.0f;
    private static final double FAN_STEP_DEG = 4.0;

    // Scatter Volley: 5 arrows fanned ±28°, each pierces up to VOLLEY_PIERCE
    // distinct foes via vanilla piercing; close-range design, despawns after a
    // few seconds if nothing is hit.
    private static final int VOLLEY_ARROWS = 5;
    private static final int VOLLEY_PIERCE = 5;
    private static final double VOLLEY_SPREAD_DEG = 14.0; // step between arrows => ±28°
    private static final float VOLLEY_SPREAD = 6.0f;
    private static final double VOLLEY_DAMAGE_SCALE = 0.85;
    private static final int VOLLEY_LIFETIME_TICKS = 60;
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

    // Holder UUID -> TRUE while Scatter Volley is armed for the next bow shot.
    // Deliberately survives item swaps (feature: toggled states outlive
    // unequip); only quit clears it.
    private static final Map<UUID, Boolean> VOLLEY_ARMED = new ConcurrentHashMap<>();

    // Holder UUID -> cooldown sink captured at the toggle cast; its
    // startCooldown() fires only when the armed volley actually leaves the bow,
    // so toggling never consumes the TRI_VOLLEY cooldown window.
    private static final Map<UUID, CooldownSink> VOLLEY_SINKS = new ConcurrentHashMap<>();

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
        // Armed state intentionally kept: re-equipping the bow restores the
        // pending volley (see class javadoc).
        hideHudForHolder();
    }

    // ---- Ability entry points (called from effects) ----

    /**
     * Toggles Scatter Volley. Turning ON arms the holder's next tri-bow shot
     * (no cooldown yet — that starts in {@code onShoot} when the volley is
     * actually fired); turning OFF cancels a pending arm for free. Returns
     * {@code true} when the toggle resulted in an ARMED state so callers can
     * keep the cast cost, or refund it on disarm.
     */
    public boolean toggleVolley(Player player, CooldownSink cooldownSink) {
        if (player == null || !isTriBow(player.getInventory().getItemInMainHand()))
            return false;
        UUID uuid = ctx.getHolder().getUuid();
        boolean armed = !Boolean.TRUE.equals(VOLLEY_ARMED.get(uuid));
        if (armed) {
            VOLLEY_ARMED.put(uuid, Boolean.TRUE);
            if (cooldownSink != null)
                VOLLEY_SINKS.put(uuid, cooldownSink);
            playArmFeedback(player);
        } else {
            VOLLEY_ARMED.remove(uuid);
            VOLLEY_SINKS.remove(uuid);
            playDisarmFeedback(player);
        }
        refreshHud(player, armed);
        return armed;
    }

    private void playArmFeedback(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        world.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
        world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.65f);
        Location burstAt = player.getEyeLocation().add(0, -0.3, 0);
        world.spawnParticle(Particle.WITCH, burstAt, 12, 0.35, 0.3, 0.35, 0.02);
        world.spawnParticle(Particle.DUST, burstAt, 10, 0.3, 0.25, 0.3, 0, new Particle.DustOptions(TRI_COLOR, 1.4f));
    }

    private void playDisarmFeedback(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.55f, 0.9f);
        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.7f);
        world.spawnParticle(Particle.SMOKE, player.getEyeLocation().add(0, -0.3, 0), 12, 0.35, 0.3, 0.35, 0.02);
    }

    private void refreshHud(Player player, boolean armed) {
        HudOverlayService hud = HudOverlayService.getInstance();
        if (armed)
            hud.show(player, "tri:volley", TriHomingHudFormatter.formatVolleyReady(), 0, 10);
        else
            hud.hide(player, "tri:volley");
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

        // armed Scatter Volley: this shot fires the piercing fan instead; the
        // TRI_VOLLEY cooldown only begins now that a volley actually left the
        // bow (toggling alone never starts it)
        UUID shooterUuid = player.getUniqueId();
        if (Boolean.TRUE.equals(VOLLEY_ARMED.remove(shooterUuid))) {
            CooldownSink sink = VOLLEY_SINKS.remove(shooterUuid);
            HudOverlayService.getInstance().hide(player, "tri:volley");
            fireVolley(player, shooterRpg, baseDir, world, eye, drawSpeed);
            if (sink != null)
                sink.startCooldown();
            return;
        }

        double baseDamage = 0;
        if (shooterRpg != null) {
            baseDamage = shooterRpg.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                    System.currentTimeMillis()) * shooterRpg.getProjectileDamageMultiplier();
        }

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
            pdc.set(HOMING_KEY, PersistentDataType.BOOLEAN, true);
            pdc.set(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, baseDamage);
            pdc.set(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN, false);
            startTrail(arrow);
            startHoming(arrow, player, shooterRpg);
        }
    }

    /**
     * Fires the Scatter Volley fan: {@link #VOLLEY_ARROWS} arrows spread ±28°
     * around the shot direction at the bow's draw speed. Piercing is left to
     * vanilla via {@code setPierceLevel} — each arrow passes through up to
     * {@link #VOLLEY_PIERCE} foes, with per-entity RPG damage flowing through
     * CombatListener via {@link BowArrowManager#ARROW_DAMAGE_KEY}.
     */
    private static void fireVolley(Player player, RPGEntity shooterRpg, Vector baseDir, World world, Location eye,
            float drawSpeed) {
        double baseDamage = 0;
        if (shooterRpg != null) {
            baseDamage = shooterRpg.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                    System.currentTimeMillis()) * shooterRpg.getProjectileDamageMultiplier() * VOLLEY_DAMAGE_SCALE;
        }

        world.playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 0.6f, 1.4f);
        world.spawnParticle(Particle.CRIT, eye.clone().add(baseDir.clone().multiply(0.6)), 12, 0.15, 0.15, 0.15, 0.2);

        for (int i = 0; i < VOLLEY_ARROWS; i++) {
            int offsetIndex = i - VOLLEY_ARROWS / 2; // -2..2
            double yawDeg = offsetIndex * VOLLEY_SPREAD_DEG + (Math.random() - 0.5) * 6.0;
            double pitchJitter = (Math.random() - 0.5) * 4.0;
            Vector dir = rotateYawPitch(baseDir.clone(), yawDeg, pitchJitter).normalize();
            double speed = drawSpeed + (Math.random() - 0.5) * 0.4;
            Arrow arrow = world.spawnArrow(eye.clone().add(dir.clone().multiply(0.3)), dir, (float) speed,
                    VOLLEY_SPREAD);
            arrow.setShooter(player);
            arrow.setCritical(false);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setGlowing(false);
            // vanilla pierce: pass through up to VOLLEY_PIERCE entities, no
            // custom hit bookkeeping needed
            arrow.setPierceLevel(VOLLEY_PIERCE);
            var pdc = arrow.getPersistentDataContainer();
            pdc.set(VOLLEY_KEY, PersistentDataType.BOOLEAN, true);
            pdc.set(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, baseDamage);
            startTrail(arrow);
            despawnAfter(arrow, VOLLEY_LIFETIME_TICKS);
        }
    }

    private static void despawnAfter(Arrow arrow, int ticks) {
        Plugin plugin = DMain.getInstance();
        if (plugin == null || !plugin.isEnabled())
            return;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (arrow.isValid() && !arrow.isDead())
                    arrow.remove();
            }
        }.runTaskLater(plugin, ticks);
    }

    private static void startTrail(Arrow arrow) {
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
        for (Entity e : w.getNearbyEntities(loc, HOMING_RADIUS, HOMING_RADIUS, HOMING_RADIUS)) {
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

    private static Vector rotateYawPitch(Vector dir, double yawDeg, double pitchDeg) {
        Vector yawed = rotateYaw(dir, yawDeg);
        // pitch: small vertical offset around the right vector
        yawed.setY(yawed.getY() + Math.tan(Math.toRadians(pitchDeg)) * 0.1);
        return yawed;
    }

    // ---- Projectile hit: consume homing arrows ----

    /**
     * Global handler (registered once in DMain, independent of any holder's ability
     * bindings): players routinely swap away from the bow while arrows are still in
     * flight, which unbinds per-holder subscriptions — a bind-scoped hit handler
     * would then never run, leaving homing arrows to steer (and visibly circle)
     * until their expiry. Shooter context is resolved from the arrow itself
     * instead.
     *
     * <p>
     * Volley arrows need no handling here: their pierce is vanilla
     * ({@code setPierceLevel}) and their RPG damage flows through CombatListener.
     */
    public static void onGlobalProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow))
            return;
        boolean isHoming = Boolean.TRUE
                .equals(arrow.getPersistentDataContainer().get(HOMING_KEY, PersistentDataType.BOOLEAN));
        if (!isHoming)
            return;

        // homing arrows are consumed on any hit (block or entity); vanilla
        // damage still processes so the standard impact sound plays
        cancelHoming(arrow.getUniqueId());
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
                if (Boolean.TRUE.equals(VOLLEY_ARMED.get(uuid)))
                    HudOverlayService.getInstance().show(p, "tri:volley", TriHomingHudFormatter.formatVolleyReady(),
                            0, 10);
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
        // session end: the armed volley does not survive relogin
        VOLLEY_ARMED.remove(ctx.getHolder().getUuid());
        VOLLEY_SINKS.remove(ctx.getHolder().getUuid());
        hideHudForHolder();
    }

    public static boolean isTriBow(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        return ITEM_ID.equals(id);
    }
}
