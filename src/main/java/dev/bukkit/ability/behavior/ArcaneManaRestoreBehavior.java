package dev.bukkit.ability.behavior;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.DamageUtils;
import dev.core.ability.AbilityBehavior;
import dev.core.ability.ActiveAbility;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.stat.StatType;

/**
 * Per-holder behavior for {@code ARCANE_MANA_RESTORE} passive: mana is
 * restored via a per-{@link ActiveAbility} subscription instead of global
 * polling.
 */
public class ArcaneManaRestoreBehavior implements AbilityBehavior {

    private static final double MANA_RESTORE_PERCENT = 0.05;

    private ActiveAbility ctx;

    public ArcaneManaRestoreBehavior(ActiveAbility ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onActivate(ActiveAbility ctx) {
        this.ctx = ctx;
        ctx.getSubscriptions().subscribe(
                new EventAction<>(this::onDamage, EntityDamageByEntityEvent.class, EventAction.HIGHEST_PRIORITY));
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        // Same sentinel handling as ArcaneCleaveBehavior: hits on RPG-managed
        // victims arrive stamped with DamageUtils.RPG_HANDLED_ENTITY once the
        // RPG pipeline applied the real damage — count them as real swings.
        if (!DamageUtils.isChargeableHit(event)) return;
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return;

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (!(damager instanceof Player player) || !(victim instanceof LivingEntity)) return;
        if (player.isDead() || !player.isOnline()) return;
        if (victim instanceof Player && CombatRelation.isPlayerTeam(victim)) return;

        RPGEntity holder = ctx.getHolder();
        if (!holder.getUuid().equals(player.getUniqueId())) return;
        if (!holder.isAlive() || EntityManager.getInstance().isGhost(player.getUniqueId())) return;

        handleManaRestore(player, holder);
    }

    static void handleManaRestore(Player player, RPGEntity holder) {
        long now = System.currentTimeMillis();
        double maxMana = holder.getStatEngineAdapter().getCurrentValue(StatType.MANA_MAX, now);
        if (maxMana <= 0) return;
        double restore = maxMana * MANA_RESTORE_PERCENT;
        double cur = holder.getMana();
        double next = Math.min(cur + restore, maxMana);
        if (next > cur + 0.01) {
            holder.setMana(next);
            try {
                World w = player.getWorld();
                Location eye = player.getEyeLocation();
                w.spawnParticle(Particle.ENCHANT, eye.clone().add(0, -0.3, 0), 12, 0.3, 0.3, 0.3, 0.4);
                w.spawnParticle(Particle.DUST, player.getLocation().clone().add(0, 1.0, 0), 6, 0.2, 0.4, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(0x55FFFF), 1.0f));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
            } catch (Exception ignored) {}
        }
    }
}
