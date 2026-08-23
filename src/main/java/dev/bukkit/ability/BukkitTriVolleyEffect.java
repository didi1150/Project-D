package dev.bukkit.ability;

import java.util.List;

import dev.bukkit.ability.behavior.TriVolleyBehavior;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.utils.ManaDiscountUtils;
import dev.core.ability.ActiveAbility;
import dev.core.ability.ActiveAbilityRegistry;
import dev.core.ability.CostEntry;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.ability.impl.TriVolleyAbility;
import dev.core.entity.RPGEntity;
import dev.core.stat.StatType;

/**
 * Trinity Bow — Scatter Volley toggle. Left-click arms the ability (paying the
 * configured cost); left-click again cancels the pending volley for free — the
 * effect refunds the just-paid cast costs in that case. The NEXT bow shot while
 * armed fires a fan of 5 piercing arrows (vanilla {@code Arrow#setPierceLevel})
 * instead of the passive homing fan, and only THEN does the TRI_VOLLEY cooldown
 * start — see {@link TriVolleyBehavior#toggleVolley}.
 */
public class BukkitTriVolleyEffect extends Effect {

    public BukkitTriVolleyEffect(String cooldownKey) {
        super(null, 0L, false, cooldownKey);
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        if (!(caster instanceof BukkitPlayerEntity playerEntity)) {
            return;
        }
        ActiveAbility aa = ActiveAbilityRegistry.getInstance().get(caster, TriVolleyAbility.ID).orElse(null);
        if (aa == null || !(aa.getBehavior() instanceof TriVolleyBehavior beh)) {
            return;
        }
        boolean[] armed = { false };
        playerEntity.getPlayer().ifPresent(p -> armed[0] = beh.toggleVolley(p, cooldownSink));
        // Disarming is free: the pipeline charged the cast cost before this
        // effect ran, so give it straight back when the toggle turned OFF.
        if (!armed[0]) {
            refundCastCosts(caster, aa.getAbility().getCost().getCosts());
        }
    }

    /**
     * Mirrors the effect manager's charge resolution (formula against the
     * caster, mana-discount applied) and credits the amounts back instead of
     * deducting them.
     */
    private static void refundCastCosts(RPGEntity caster, List<CostEntry> costs) {
        for (CostEntry cost : costs) {
            double base = cost.resolve(caster);
            double amount = ManaDiscountUtils.discountedCost(caster, cost.mode().getResourceType(), base);
            try {
                caster.getStatManager().modifyStat(StatType.valueOf(cost.mode().getResourceType()), amount);
            } catch (IllegalArgumentException ignored) {
                // unknown resource — nothing was deducted for it either
            }
        }
    }

    @Override
    public void cancel() {
        // Toggle — nothing to clean up.
    }
}
