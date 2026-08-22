package dev.bukkit.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import dev.bukkit.ability.BukkitTriVolleyEffect;
import dev.bukkit.hud.HudOverlayService;
import dev.bukkit.hud.TriHomingHudFormatter;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

/**
 * Trinity Bow — right-draw shoots 3 homing arrows (nearest enemy within 10
 * blocks, same mob allowed, no falloff, consumed), left-click volley is handled
 * by {@link dev.bukkit.ability.BukkitTriVolleyEffect}.
 */
@EventSubscriber
public class TriHomingBowManager {

    public static final String ITEM_ID = "TRI_HOMING_BOW";

    public static final NamespacedKey HOMING_KEY = new NamespacedKey("project_d", "tri_homing");
    public static final NamespacedKey VOLLEY_KEY = new NamespacedKey("project_d", "tri_volley");

    private static final int HOMING_ARROWS = 3;
    private static final double HOMING_RADIUS = 10.0;
    private static final double HOMING_STRENGTH = 0.22;
    private static final int HOMING_TICKS = 60;
    private static final float HOMING_SPEED = 2.3f; // fallback if vanilla velocity unavailable
    private static final float HOMING_SPREAD = 4.0f;

    private static final Color TRI_COLOR = Color.fromRGB(0xD946FF);
    private static final Map<UUID, BukkitTask> HOMING_TASKS = new HashMap<>();

    private static TriHomingBowManager instance;
    private static Plugin plugin;
//    private static boolean running;
    private static BukkitTask heldPollTask;

    public TriHomingBowManager() {
        if (instance == null)
            instance = this;
    }

    public TriHomingBowManager(Plugin p) {
        plugin = p;
//        running = true;
        instance = this;
    }

    public static TriHomingBowManager getInstance() {
        if (instance == null)
            instance = new TriHomingBowManager();
        return instance;
    }

    public void start(Plugin p) {
        plugin = p;
//        running = true;
        if (instance == null || instance != this)
            instance = this;
        startHeldPoll();
    }

    public void stop() {
//        running = false;
        stopHeldPoll();
        for (BukkitTask t : HOMING_TASKS.values()) {
            try {
                t.cancel();
            } catch (Exception ignored) {
            }
        }
        HOMING_TASKS.clear();
        try {
            for (Player pl : Bukkit.getOnlinePlayers())
                hideHud(pl);
        } catch (Exception ignored) {
        }
    }

    private void startHeldPoll() {
        stopHeldPoll();
        if (plugin == null)
            return;
        heldPollTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    boolean holds = isTriBow(p.getInventory().getItemInMainHand());
                    if (holds)
                        refreshHud(p);
                    else if (hasTriHud(p))
                        hideHud(p);
                } catch (Exception ignored) {
                }
            }
        }, 20L, 5L);
    }

    private void stopHeldPoll() {
        if (heldPollTask != null) {
            try {
                heldPollTask.cancel();
            } catch (Exception ignored) {
            }
            heldPollTask = null;
        }
    }

    private boolean hasTriHud(Player p) {
        try {
            return HudOverlayService.getInstance().getActiveCount(p) > 0
                    && isTriBow(p.getInventory().getItemInMainHand());
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Shoot 3 homing ----

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Arrow vanilla))
            return;
        ItemStack bow = event.getBow();
        LivingEntity entity = event.getEntity();
        if (bow == null)
            bow = event.getBow();
        if (!isTriBow(bow))
            return;

        // cancel vanilla single arrow
        event.setCancelled(true);
        vanilla.remove();

        // spawn 3 homing arrows fanned slightly to avoid merge mid-air
        Location eye = event.getEntity().getEyeLocation();
        Vector baseDir = eye.getDirection().normalize();
        World world = eye.getWorld();
        if (world == null)
            return;

        // capture damage at cast time
        double baseDamage = 0;
        var rpgOpt = EntityManager.getInstance().getEntity(entity.getUniqueId());
        if (rpgOpt.isPresent()) {
            RPGEntity rpg = rpgOpt.get();
            baseDamage = rpg.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE, System.currentTimeMillis())
                    * rpg.getProjectileDamageMultiplier();
        }

        // Use vanilla arrow velocity directly — matches bow draw (max at full draw, min
        // at tap)
        float drawSpeed = (float) vanilla.getVelocity().length();
        if (drawSpeed < 0.1f) {
            // fallback if velocity not yet populated (should not happen)
            try {
                drawSpeed = event.getForce() * 3.0f;
            } catch (Exception ignored) {
            }
        }
        if (drawSpeed < 0.1f)
            drawSpeed = HOMING_SPEED;
        // pitch mirrors draw (derive force from vanilla speed)
        float forcePitch = Math.max(0f, Math.min(1f, drawSpeed / 3.0f));
        float pitch = 0.9f + forcePitch * 0.6f; // 0.9 .. 1.5

        world.playSound(entity.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.95f, pitch);
        world.spawnParticle(Particle.ELECTRIC_SPARK, eye, 8, 0.2, 0.2, 0.2, 0.05);
        world.spawnParticle(Particle.WITCH, eye.clone().add(0, -0.2, 0), 10, 0.3, 0.3, 0.3, 0.02);

        for (int i = 0; i < HOMING_ARROWS; i++) {
            int offIdx = i - HOMING_ARROWS / 2; // -1,0,1
            double yawDeg = offIdx * 4.0 + (i == 1 ? 0 : (Math.random() - 0.5) * 2.0);
            Vector dir = rotateYaw(baseDir.clone(), yawDeg).normalize();
            Location spawn = eye.clone().add(dir.clone().multiply(0.4));
            Arrow arrow = world.spawnArrow(spawn, dir, drawSpeed, HOMING_SPREAD);
            arrow.setShooter(entity);
            arrow.setCritical(false);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setGlowing(false);
            var pdc = arrow.getPersistentDataContainer();
            pdc.set(HOMING_KEY, PersistentDataType.BOOLEAN, true);
            pdc.set(BowArrowManager.ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, baseDamage);
            pdc.set(BowArrowManager.BOUNCE_KEY, PersistentDataType.BOOLEAN, false);
            startTrail(arrow);
            startHoming(arrow, entity);
        }

        refreshHud(entity);
    }

    private void startTrail(Arrow arrow) {
        if (plugin == null)
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

    private void startHoming(Arrow arrow, LivingEntity shooter) {
        if (plugin == null)
            return;
        UUID arrowId = arrow.getUniqueId();
        RPGEntity shooterRpg = EntityManager.getInstance().getEntity(shooter.getUniqueId()).orElse(null);
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
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
                        Vector to = target.getEyeLocation().toVector().subtract(arrow.getLocation().toVector())
                                .normalize().multiply(speed);
                        Vector blended = cur.clone().multiply(1.0 - HOMING_STRENGTH)
                                .add(to.clone().multiply(HOMING_STRENGTH));
                        // keep speed magnitude
                        blended.normalize().multiply(speed);
                        arrow.setVelocity(blended);
                        arrow.setTicksLived(1); // prevent vanilla gravity from stacking
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        HOMING_TASKS.put(arrowId, task);
    }

    private LivingEntity nearestEnemy(Location loc, LivingEntity shooter, RPGEntity shooterRpg) {
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
                // line-of-sight optional? not required per spec; keep simple nearest
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

    // ---- Projectile hit — pierce for volley, consume homing ----

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow))
            return;
        boolean isHoming = Boolean.TRUE
                .equals(arrow.getPersistentDataContainer().get(HOMING_KEY, PersistentDataType.BOOLEAN));
        boolean isVolley = Boolean.TRUE
                .equals(arrow.getPersistentDataContainer().get(VOLLEY_KEY, PersistentDataType.BOOLEAN));
        if (!isHoming && !isVolley)
            return;

        // homing arrows are consumed on any hit (block or entity)
        if (isHoming) {
            // cancel bounce, ensure remove after damage handled by CombatListener
            BukkitTask t = HOMING_TASKS.remove(arrow.getUniqueId());
            if (t != null)
                try {
                    t.cancel();
                } catch (Exception ignored) {
                }
            BukkitTriVolleyEffect.cleanupPierce(arrow);
            // let vanilla damage process, then remove shortly
            if (event.getHitEntity() != null) {
                // keep arrow for one tick so damage processes, then remove
                if (plugin != null)
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (arrow.isValid())
                                arrow.remove();
                        }
                    }.runTaskLater(plugin, 1L);
            } else {
                // block hit: remove immediately
                arrow.remove();
            }
            return;
        }

        // volley pierce: allow piercing up to 5 entities
        if (isVolley && event.getHitEntity() instanceof LivingEntity hit) {
            // check if we've already pierced this entity? delegate to effect's map
            boolean shouldContinue = BukkitTriVolleyEffect.handlePierceHit(arrow, hit);
            if (shouldContinue) {
                event.setCancelled(true);
                // push slightly forward to avoid immediate re-hit
                if (event.getEntity().getShooter() instanceof LivingEntity entity) {
                    Optional<RPGEntity> rpgOpt = EntityManager.getInstance().getEntity(entity.getUniqueId());
                    RPGEntity rpg = rpgOpt.get();
                    double baseDamage = rpg.getStatEngineAdapter().getCurrentValue(StatType.ATTACK_DAMAGE,
                            System.currentTimeMillis()) * rpg.getProjectileDamageMultiplier();
                    DamageUtils.damageEntity(event.getHitEntity(), baseDamage, rpg, DamageType.PHYSICAL);
                }
                Vector vel = arrow.getVelocity().clone().normalize().multiply(0.3);
                arrow.setVelocity(vel);
                // keep arrow alive
                return;
            } else {
                // pierce cap reached -> let it die
                BukkitTriVolleyEffect.cleanupPierce(arrow);
            }
        } else if (isVolley && event.getHitBlock() != null) {
            // volley hits block -> remove
            BukkitTriVolleyEffect.cleanupPierce(arrow);
            arrow.remove();
        }
    }

    // ---- HUD & held detection ----

    private static void refreshHud(LivingEntity entity) {
        if (!(entity instanceof Player p)) {
            return;
        }
        if (p == null || !isTriBow(p.getInventory().getItemInMainHand())) {
            hideHud(p);
            return;
        }
        HudOverlayService hud = HudOverlayService.getInstance();
        hud.show(p, "tri:ready", TriHomingHudFormatter.formatReady(), 0, 30);
        // volley cooldown hint could be added as second line if needed
    }

    private static void hideHud(Player p) {
        if (p == null)
            return;
        HudOverlayService hud = HudOverlayService.getInstance();
        hud.hide(p, "tri:ready");
        hud.hide(p, "tri:volley");
    }

    @Subscribe
    public void onQuit(PlayerQuitEvent e) {
        hideHud(e.getPlayer());
        try {
            BukkitTriVolleyEffect.cleanupPierce(null);
        } catch (Exception ignored) {
        }
    }

    @Subscribe
    public void onDeath(PlayerDeathEvent e) {
        if (e.getEntity() instanceof Player p)
            hideHud(p);
    }

    @Subscribe
    public void onSwap(PlayerItemHeldEvent e) {
        if (plugin == null)
            return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            ItemStack ni = p.getInventory().getItem(e.getNewSlot());
            if (isTriBow(ni))
                refreshHud(p);
            else
                hideHud(p);
        });
    }

    @Subscribe
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (plugin == null)
            return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            if (isTriBow(p.getInventory().getItemInMainHand()))
                refreshHud(p);
            else
                hideHud(p);
        });
    }

    @Subscribe
    public void onInvClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p))
            return;
        if (plugin == null)
            return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isTriBow(p.getInventory().getItemInMainHand()))
                refreshHud(p);
            else
                hideHud(p);
        });
    }

    @Subscribe
    public void onJoin(PlayerJoinEvent e) {
        Player j = e.getPlayer();
        try {
            HudOverlayService.getInstance().hideAllFrom(j);
        } catch (Exception ignored) {
        }
        if (plugin != null)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isTriBow(j.getInventory().getItemInMainHand()))
                    refreshHud(j);
            }, 5L);
    }

    // ---- Helpers ----

    public static boolean isTriBow(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        String id = BukkitItemStackAdapter.getRpgItemId(stack);
        return ITEM_ID.equals(id);
    }
}
