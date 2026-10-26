package dev.bukkit.status.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;

import dev.bukkit.DMain;
import dev.bukkit.event.bukkitListeners.CombatListener;
import dev.bukkit.status.StatusEffectBehavior;
import dev.bukkit.status.StatusEffectContext;
import dev.bukkit.utils.DamageUtils;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGDamageResult;
import dev.core.entity.RPGEntity;
import dev.core.event.impl.RPGEntityDamageEvent.DamageResult;
import dev.core.event.impl.RPGEntityDamageEvent.DamageType;
import dev.core.stat.StatType;

public final class WitherStatusEffectBehavior implements StatusEffectBehavior {

    private static final int DAMAGE_INTERVAL_TICKS = 20;

    private final Map<UUID, WitherState> states = new ConcurrentHashMap<>();

    @Override
    public void onApply(StatusEffectContext ctx) {
        WitherState state = new WitherState();
        state.casterUuid = ctx.getEffect().getCasterUuid();
        state.baseDamage = ctx.getEffect().getPotency();
        state.nextDamageTick = DAMAGE_INTERVAL_TICKS;
        states.put(ctx.getRpgEntity().getUuid(), state);
    }

    @Override
    public void onTick(StatusEffectContext ctx, long now) {
        UUID victimId = ctx.getRpgEntity().getUuid();
        WitherState state = states.get(victimId);
        if (state == null) {
            return;
        }

        LivingEntity living = ctx.getLivingEntity();
        if (!living.isValid() || living.isDead()) {
            states.remove(victimId);
            return;
        }

        state.nextDamageTick--;
        if (state.nextDamageTick > 0) {
            return;
        }
        state.nextDamageTick = DAMAGE_INTERVAL_TICKS;

        RPGEntity caster = null;
        double healPower = 0;
        if (state.casterUuid != null) {
            caster = EntityManager.getInstance().getEntity(state.casterUuid).orElse(null);
            if (caster != null) {
                healPower = caster.getStatEngineAdapter().getCurrentValue(StatType.HEAL_AND_SHIELD_POWER, now);
            }
        }

        double scaledDamage = state.baseDamage * (1.0 + healPower / 100.0);
        RPGDamageResult result = DamageUtils.damageEntity(living, scaledDamage, caster, DamageType.MAGIC);
        if (result.getResult() != DamageResult.DENY) {
            showMagicIndicator(living, result.getDamage(), result.getResult());
        }
        if (!living.isValid() || living.isDead()) {
            return;
        }

        living.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                living.getLocation().clone().add(0, living.getHeight() * 0.5, 0),
                3, 0.2, 0.2, 0.2, 0.02);
    }

    @Override
    public void onEnd(StatusEffectContext ctx) {
        states.remove(ctx.getRpgEntity().getUuid());
    }

    private void showMagicIndicator(LivingEntity target, double damage, DamageResult result) {
        if (damage <= 0) {
            return;
        }
        DMain plugin = DMain.getInstance();
        CombatListener listener = plugin == null ? null : plugin.getCombatListener();
        if (listener != null) {
            listener.showMagicDamage(target.getLocation(), damage, result);
        }
    }

    private static class WitherState {
        UUID casterUuid;
        double baseDamage;
        int nextDamageTick;
    }
}
