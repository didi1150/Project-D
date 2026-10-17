package dev.bukkit.ability;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import dev.bukkit.ability.behavior.SupportStaffBehavior;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.hud.StaffFeedback;
import dev.bukkit.utils.CombatRelation;
import dev.bukkit.utils.StaffTargeting;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityHealEvent.HealReason;
import dev.core.stat.StatType;
import dev.core.status.StatusEffectType;
import dev.core.utils.LegacyColors;

/**
 * RIGHT-click effect for the Utility Staff. The action depends on the staff's
 * current mode:
 * <ul>
 * <li><b>Mode 1 (Mending Touch)</b>: Heals a targeted ally (3s cooldown)</li>
 * <li><b>Mode 2 (Aegis Ward)</b>: Grants absorption to a targeted ally (2s
 * cooldown)</li>
 * <li><b>Mode 3 (Tempest Gust)</b>: Pushes all nearby enemies outward (10s
 * cooldown)</li>
 * </ul>
 * Cooldowns are per-mode: {@code BukkitEffectManager} appends the current mode
 * to the staff's cooldown key before {@code canActivate} checks it. Every
 * attempt reports success or failure through the HUD overlay (chat fallback
 * when the HUD is disabled).
 */
public class BukkitSupportStaffUseEffect extends Effect {

    private static final double HEAL_BASE = 20.0;
    private static final long HEAL_COOLDOWN_MS = 3000;

    private static final double SHIELD_BASE_POTENCY = 20.0;
    private static final long SHIELD_DURATION_MS = 5000;
    private static final long SHIELD_COOLDOWN_MS = 2000;

    private static final double PUSH_RADIUS = 8.0;
    private static final double PUSH_STRENGTH = 1.2;
    private static final long PUSH_COOLDOWN_MS = 10000;

    public BukkitSupportStaffUseEffect(String cooldownKey) {
        super(null, -1, false, cooldownKey);
    }

    /** The staff cast is instantaneous; do not keep it in the active-effect list. */
    @Override
    public boolean hasExpired(long now) {
        return true;
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        if (!(caster instanceof BukkitPlayerEntity playerEntity))
            return;
        Optional<Player> optPlayer = playerEntity.getPlayer();
        if (optPlayer.isEmpty())
            return;
        Player player = optPlayer.get();
        if (player.isDead() || !player.isOnline())
            return;

        SupportStaffBehavior state = SupportStaffBehavior.forHolder(caster.getUuid());
        if (state == null)
            return;

        switch (state.getCurrentMode()) {
        case 1 -> handleHeal(caster, player, cooldownSink);
        case 2 -> handleShield(caster, player, cooldownSink);
        case 3 -> handlePushback(caster, player, cooldownSink);
        }
    }

    @Override
    public void cancel() {
    }

    private void handleHeal(RPGEntity caster, Player player, CooldownSink cooldownSink) {
        Entity targetBukkit = StaffTargeting.raycastAlly(player);
        if (targetBukkit == null) {
            fail(player, LegacyColors.RED + "Mending Touch: no ally in sight.");
            return;
        }

        RPGEntity target = EntityManager.getInstance().getEntity(targetBukkit.getUniqueId()).orElse(null);
        if (target == null) {
            fail(player, LegacyColors.RED + "Mending Touch: that ally cannot be healed.");
            return;
        }
        if (!target.isAlive()) {
            fail(player, LegacyColors.RED + "Mending Touch: that ally is already down.");
            return;
        }
        if (target.getHealth() >= target.getMaxHealth()) {
            fail(player, LegacyColors.YELLOW + "Mending Touch: " + targetBukkit.getName()
                    + " is already at full health.");
            return;
        }

        double before = target.getHealth();
        caster.healRPGEntity(caster, target, HEAL_BASE, HealReason.SPELL);
        double healed = target.getHealth() - before;
        if (healed <= 0.001) {
            fail(player, LegacyColors.YELLOW + "Mending Touch: healing had no effect.");
            return;
        }

        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                targetBukkit.getLocation().add(0, targetBukkit.getHeight() + 0.3, 0), 5, 0.3, 0.3, 0.3, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.3f, 1.8f);
        cooldownSink.startCooldown(HEAL_COOLDOWN_MS);
        success(player, LegacyColors.GREEN + "Mending Touch: healed " + targetBukkit.getName() + " for "
                + String.format("%.0f", healed) + " HP.");
    }

    private void handleShield(RPGEntity caster, Player player, CooldownSink cooldownSink) {
        Entity targetBukkit = StaffTargeting.raycastAlly(player);
        if (targetBukkit == null) {
            fail(player, LegacyColors.RED + "Aegis Ward: no ally in sight.");
            return;
        }

        RPGEntity target = EntityManager.getInstance().getEntity(targetBukkit.getUniqueId()).orElse(null);
        if (target == null) {
            fail(player, LegacyColors.RED + "Aegis Ward: that ally cannot be shielded.");
            return;
        }
        if (!target.isAlive()) {
            fail(player, LegacyColors.RED + "Aegis Ward: that ally is already down.");
            return;
        }

        double healPower = caster.getStatEngineAdapter().getCurrentValue(StatType.HEAL_AND_SHIELD_POWER,
                System.currentTimeMillis());
        double potency = SHIELD_BASE_POTENCY * (1.0 + healPower / 100.0);
        target.getStatusEffectManager().applyStacking(target, StatusEffectType.ABSORPTION, SHIELD_DURATION_MS, true,
                potency);

        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                targetBukkit.getLocation().add(0, targetBukkit.getHeight() + 0.3, 0), 8, 0.3, 0.3, 0.3, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.5f, 1.2f);
        cooldownSink.startCooldown(SHIELD_COOLDOWN_MS);
        success(player, LegacyColors.GREEN + "Aegis Ward: shielded " + targetBukkit.getName() + " for "
                + (SHIELD_DURATION_MS / 1000) + "s.");
    }

    private void handlePushback(RPGEntity caster, Player player, CooldownSink cooldownSink) {
        World world = player.getWorld();
        Location center = player.getLocation();
        int pushed = 0;

        for (Entity entity : world.getNearbyEntities(center, PUSH_RADIUS, PUSH_RADIUS, PUSH_RADIUS)) {
            if (!(entity instanceof LivingEntity living))
                continue;
            if (living.getUniqueId().equals(caster.getUuid()))
                continue;
            if (living.isDead() || !living.isValid())
                continue;
            if (EntityManager.getInstance().isGhost(living.getUniqueId()))
                continue;
            if (!CombatRelation.isEnemy(caster, entity))
                continue;

            Vector toEntity = living.getLocation().toVector().subtract(center.toVector());
            toEntity.setY(0);
            double dist = toEntity.length();
            if (dist < 0.1 || dist > PUSH_RADIUS)
                continue;

            double falloff = 1.0 - (dist / PUSH_RADIUS);
            Vector push = toEntity.normalize().multiply(PUSH_STRENGTH * falloff);
            push.setY(0.35);
            living.setVelocity(push);
            world.spawnParticle(Particle.CLOUD, living.getLocation().add(0, living.getHeight() * 0.5, 0), 3, 0.2, 0.2,
                    0.2, 0.01);
            pushed++;
        }

        if (pushed == 0) {
            fail(player, LegacyColors.YELLOW + "Tempest Gust: no enemies in range.");
            return;
        }

        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
        cooldownSink.startCooldown(PUSH_COOLDOWN_MS);
        success(player, LegacyColors.AQUA + "Tempest Gust: pushed " + pushed
                + (pushed == 1 ? " enemy." : " enemies."));
    }

    private static void success(Player player, String message) {
        StaffFeedback.show(player, message);
    }

    private static void fail(Player player, String message) {
        StaffFeedback.show(player, message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.9f);
    }
}
