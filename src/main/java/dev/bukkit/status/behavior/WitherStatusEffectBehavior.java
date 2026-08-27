package dev.bukkit.status.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import dev.bukkit.status.StatusEffectBehavior;
import dev.bukkit.status.StatusEffectContext;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;

/**
 * Wither damage-over-time status effect. Applies the vanilla WITHER potion
 * effect for the dark-hearts visual and icon, but overrides the actual damage
 * with a custom formula that scales with the caster's HEAL_AND_SHIELD_POWER.
 * Damage is dealt once per second (20 ticks).
 */
public final class WitherStatusEffectBehavior implements StatusEffectBehavior {

    private static final int DAMAGE_INTERVAL_TICKS = 20;

    private final Map<UUID, WitherState> states = new ConcurrentHashMap<>();

    @Override
    public void onApply(StatusEffectContext ctx) {
        LivingEntity living = ctx.getLivingEntity();
        UUID victimId = ctx.getRpgEntity().getUuid();

        // Apply vanilla WITHER for visual (dark hearts, icon)
        living.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER,
                100, // short duration, refreshed by re-apply or status effect tick
                0, // amplifier 0 = Wither I visual
                false, // ambient
                false, // show particles (we spawn our own)
                true // show icon
        ));

        // Resolve caster from the effect's casterUuid
        UUID casterUuid = ctx.getEffect().getCasterUuid();

        WitherState state = new WitherState();
        state.casterUuid = casterUuid;
        state.baseDamage = ctx.getEffect().getPotency();
        state.nextDamageTick = DAMAGE_INTERVAL_TICKS;
        states.put(victimId, state);
    }

    @Override
    public void onTick(StatusEffectContext ctx, long now) {
        UUID victimId = ctx.getRpgEntity().getUuid();
        WitherState state = states.get(victimId);
        if (state == null) return;

        LivingEntity living = ctx.getLivingEntity();
        if (!living.isValid() || living.isDead()) {
            states.remove(victimId);
            return;
        }

        state.nextDamageTick--;
        if (state.nextDamageTick > 0) return;
        state.nextDamageTick = DAMAGE_INTERVAL_TICKS;

        // Refresh the vanilla WITHER visual
        living.removePotionEffect(PotionEffectType.WITHER);
        living.addPotionEffect(new PotionEffect(
                PotionEffectType.WITHER, 100, 0, false, false, true));

        // Resolve caster for stat scaling
        double healPower = 0;
        if (state.casterUuid != null) {
            RPGEntity caster = EntityManager.getInstance().getEntity(state.casterUuid).orElse(null);
            if (caster != null) {
                healPower = caster.getStatEngineAdapter().getCurrentValue(
                        StatType.HEAL_AND_SHIELD_POWER, now);
            }
        }

        double scaledDamage = state.baseDamage * (1.0 + healPower / 100.0);

        // Deal damage
        double newHealth = living.getHealth() - scaledDamage;
        if (newHealth <= 0) {
            living.setHealth(0);
        } else {
            living.setHealth(newHealth);
        }

        // Visual: wither particles
        living.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                living.getLocation().clone().add(0, living.getHeight() * 0.5, 0),
                3, 0.2, 0.2, 0.2, 0.02);

        // Sound: quiet wither hurt
        living.getWorld().playSound(living.getLocation(), Sound.ENTITY_WITHER_HURT, 0.4f, 1.5f);
    }

    @Override
    public void onEnd(StatusEffectContext ctx) {
        UUID victimId = ctx.getRpgEntity().getUuid();
        states.remove(victimId);

        LivingEntity living = ctx.getLivingEntity();
        if (living != null && living.isValid() && !living.isDead()) {
            living.removePotionEffect(PotionEffectType.WITHER);
        }
    }

    private static class WitherState {
        UUID casterUuid;
        double baseDamage;
        int nextDamageTick;
    }
}
