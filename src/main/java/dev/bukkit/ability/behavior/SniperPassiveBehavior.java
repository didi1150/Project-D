package dev.bukkit.ability.behavior;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

import dev.bukkit.DMain;
import dev.bukkit.entity.MobRPGEntity;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.mob.SniperDamage;
import dev.core.event.EventAction;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;

public class SniperPassiveBehavior implements AbilityBehavior {

    public static final String BEHAVIOR_ID = "sniper-skeleton";

    public static final NamespacedKey ARROW_KEY = new NamespacedKey("project_d", "sniper_arrow");
    public static final NamespacedKey BASE_DAMAGE_KEY = new NamespacedKey("project_d", "sniper_base_damage");
    public static final NamespacedKey ORIGIN_X_KEY = new NamespacedKey("project_d", "sniper_origin_x");
    public static final NamespacedKey ORIGIN_Y_KEY = new NamespacedKey("project_d", "sniper_origin_y");
    public static final NamespacedKey ORIGIN_Z_KEY = new NamespacedKey("project_d", "sniper_origin_z");
    public static final NamespacedKey GROWTH_KEY = new NamespacedKey("project_d", "sniper_growth");
    public static final NamespacedKey MAX_MULTIPLIER_KEY = new NamespacedKey("project_d", "sniper_max_multiplier");
    public static final NamespacedKey PIERCE_KEY = new NamespacedKey("project_d", "sniper_pierce");

    private static final Map<UUID, HitState> ARROW_STATES = new ConcurrentHashMap<>();

    private ActiveAbility ctx;

    private static final class HitState {
        private final UUID shooterId;
        private final Set<UUID> touched = ConcurrentHashMap.newKeySet();
        private int enemyHits;

        private HitState(UUID shooterId) {
            this.shooterId = shooterId;
        }
    }

    public SniperPassiveBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(
                new EventAction<>(this::onProjectileHit, ProjectileHitEvent.class, EventAction.HIGHEST_PRIORITY));
        ctx.getSubscriptions().subscribe(
                new EventAction<>(this::onShootBow, EntityShootBowEvent.class, EventAction.HIGHEST_PRIORITY));
    }

    @Override
    public void onDeactivate(ActiveAbility ctx) {
        UUID holderId = ctx.getHolder().getUuid();
        Iterator<Map.Entry<UUID, HitState>> iterator = ARROW_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, HitState> entry = iterator.next();
            if (!holderId.equals(entry.getValue().shooterId)) {
                continue;
            }
            iterator.remove();
            if (Bukkit.getServer() != null) {
                Entity arrow = Bukkit.getEntity(entry.getKey());
                if (arrow != null) {
                    arrow.remove();
                }
            }
        }
    }

    private void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof MobRPGEntity mob)) {
            return;
        }
        if (!mob.getUuid().equals(ctx.getHolder().getUuid())) {
            return;
        }
        if (!BEHAVIOR_ID.equals(mob.getDefinition().getBehaviorId())) {
            return;
        }
        event.setCancelled(true);
    }

    private void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!Boolean.TRUE.equals(arrow.getPersistentDataContainer().get(ARROW_KEY, PersistentDataType.BOOLEAN))) {
            return;
        }
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) {
            return;
        }
        if (!shooter.getUniqueId().equals(ctx.getHolder().getUuid())) {
            return;
        }

        if (event.getHitEntity() == null) {
            event.setCancelled(true);
            removeArrow(arrow);
            return;
        }

        event.setCancelled(true);
        if (!(event.getHitEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target.getUniqueId().equals(shooter.getUniqueId())
                || EntityManager.getInstance().isGhost(target.getUniqueId())
                || target.isDead()) {
            return;
        }

        HitState state = ARROW_STATES.computeIfAbsent(arrow.getUniqueId(),
                id -> new HitState(shooter.getUniqueId()));
        if (!state.touched.add(target.getUniqueId())) {
            return;
        }
        if (!CombatRelation.isEnemy(ctx.getHolder(), target)) {
            return;
        }

        double baseDamage = getDouble(arrow, BASE_DAMAGE_KEY, 0.0);
        double growth = getDouble(arrow, GROWTH_KEY, 0.0);
        double maxMultiplier = getDouble(arrow, MAX_MULTIPLIER_KEY, 1.0);
        int pierceCount = Math.max(1, getInt(arrow, PIERCE_KEY, 1));
        double distance = distanceFromOrigin(arrow, target);
        double damage = SniperDamage.damage(baseDamage, distance, growth, maxMultiplier);
        if (damage <= 0) {
            return;
        }

        RPGDamageResult result = DamageUtils.damageEntity(target, damage, ctx.getHolder(), DamageType.PHYSICAL);
        if (result.getResult() == DamageResult.DENY) {
            return;
        }
        state.enemyHits++;
        showPhysicalIndicator(target, result.getDamage(), result.getResult());
        playHitSound(target, result.getResult());

        if (state.enemyHits >= pierceCount) {
            removeArrow(arrow);
        }
    }

    private static double getDouble(Arrow arrow, NamespacedKey key, double fallback) {
        Double value = arrow.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        return value == null ? fallback : value;
    }

    private static int getInt(Arrow arrow, NamespacedKey key, int fallback) {
        Integer value = arrow.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? fallback : value;
    }

    private static double distanceFromOrigin(Arrow arrow, LivingEntity target) {
        Double x = arrow.getPersistentDataContainer().get(ORIGIN_X_KEY, PersistentDataType.DOUBLE);
        Double y = arrow.getPersistentDataContainer().get(ORIGIN_Y_KEY, PersistentDataType.DOUBLE);
        Double z = arrow.getPersistentDataContainer().get(ORIGIN_Z_KEY, PersistentDataType.DOUBLE);
        if (x == null || y == null || z == null) {
            return 0.0;
        }
        return target.getLocation().toVector().distance(new org.bukkit.util.Vector(x, y, z));
    }

    private void removeArrow(Arrow arrow) {
        ARROW_STATES.remove(arrow.getUniqueId());
        if (arrow.isValid()) {
            arrow.remove();
        }
    }

    public static void clearArrowState(UUID arrowId) {
        ARROW_STATES.remove(arrowId);
    }

    private void showPhysicalIndicator(LivingEntity target, double damage, DamageResult result) {
        if (damage <= 0) {
            return;
        }
        DMain plugin = DMain.getInstance();
        CombatListener listener = plugin == null ? null : plugin.getCombatListener();
        if (listener != null) {
            listener.showPhysicalDamage(target.getLocation(), damage, result);
        }
    }

    private void playHitSound(LivingEntity target, DamageResult result) {
        DMain plugin = DMain.getInstance();
        CombatListener listener = plugin == null ? null : plugin.getCombatListener();
        if (listener != null) {
            listener.playProjectileHitSound(target, result);
        }
    }
}
