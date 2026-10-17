package dev.bukkit.status.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import dev.bukkit.status.StatusEffectBehavior;
import dev.bukkit.status.StatusEffectContext;
import dev.core.entity.RPGEntity;
import dev.core.status.StatusEffectType;

/**
 * Absorption shield status effect: absorbs all damage before real HP is hit.
 * Visualised with vanilla absorption hearts (players) and the vanilla glowing
 * outline on all entities.
 */
public final class AbsorptionStatusEffectBehavior implements StatusEffectBehavior {

    private final Map<UUID, AbsorptionState> states = new ConcurrentHashMap<>();

    @Override
    public void onApply(StatusEffectContext ctx) {
        RPGEntity rpg = ctx.getRpgEntity();
        LivingEntity living = ctx.getLivingEntity();
        UUID uuid = rpg.getUuid();

        // Each absorption application is an independent stack: add its potency to
        // the live total instead of replacing the amount already granted.
        AbsorptionState state = states.computeIfAbsent(uuid, key -> {
            AbsorptionState created = new AbsorptionState();
            created.previousGlowing = living.isGlowing();
            return created;
        });
        state.stackCount++;
        rpg.setAbsorptionAmount(rpg.getAbsorptionAmount() + ctx.getEffect().getPotency());

        // Vanilla glowing supplies the shield outline. No scoreboard team is used:
        // the staff targeting indicator owns its own client-only teams and two
        // team systems fighting over one entity causes client protocol errors.
        if (!living.isGlowing()) {
            living.setGlowing(true);
        }

        // Sync vanilla absorption hearts for players.
        if (living instanceof Player player) {
            syncVanillaAbsorption(player, rpg.getAbsorptionAmount());
        }
    }

    @Override
    public void onTick(StatusEffectContext ctx, long now) {
        RPGEntity rpg = ctx.getRpgEntity();
        LivingEntity living = ctx.getLivingEntity();
        UUID uuid = rpg.getUuid();

        AbsorptionState state = states.get(uuid);
        if (state == null || state.stackCount <= 0) {
            return; // all absorption stacks already ended during this tick
        }
        if (!living.isValid() || living.isDead()) {
            return;
        }

        // Defensive sync: ensure the vanilla shield outline stays active.
        if (!living.isGlowing()) {
            living.setGlowing(true);
        }

        // Sync vanilla absorption hearts for players every tick (absorption may
        // have been consumed by damage since the last tick).
        if (living instanceof Player player) {
            syncVanillaAbsorption(player, rpg.getAbsorptionAmount());
        }

        // If absorption has been fully consumed by damage, end every stack.
        if (rpg.getAbsorptionAmount() <= 0) {
            ctx.getRpgEntity().getStatusEffectManager().remove(ctx.getRpgEntity(), StatusEffectType.ABSORPTION);
        }
    }

    @Override
    public void onEnd(StatusEffectContext ctx) {
        RPGEntity rpg = ctx.getRpgEntity();
        LivingEntity living = ctx.getLivingEntity();
        UUID uuid = rpg.getUuid();

        AbsorptionState state = states.get(uuid);
        if (state != null) {
            state.stackCount--;

            // Remove only this stack's contribution; damage already consumed is
            // reflected in the live total, so never restore more than remains.
            double remaining = Math.max(0, rpg.getAbsorptionAmount() - ctx.getEffect().getPotency());
            rpg.setAbsorptionAmount(remaining);

            if (state.stackCount > 0) {
                // Other absorption stacks are still active: keep the glow and the
                // vanilla hearts synced to the reduced total.
                if (living != null && living.isValid() && !living.isDead()) {
                    if (!living.isGlowing()) {
                        living.setGlowing(true);
                    }
                    if (living instanceof Player player) {
                        syncVanillaAbsorption(player, remaining);
                    }
                }
                return;
            }
            states.remove(uuid);
        }

        // Last stack ended (or the effect was removed without an applied stack):
        // clear the shield completely.
        rpg.setAbsorptionAmount(0);

        if (living != null && living.isValid() && !living.isDead()) {
            // Restore previous glow state.
            living.setGlowing(state != null && state.previousGlowing);

            // Clear vanilla absorption hearts for players.
            if (living instanceof Player player) {
                syncVanillaAbsorption(player, 0);
            }
        }
    }

    /**
     * Converts the RPG absorption amount to vanilla absorption hearts using the
     * same scaling formula as health, then syncs it to the player.
     */
    private void syncVanillaAbsorption(Player player, double absorptionAmount) {
        if (absorptionAmount <= 0) {
            player.setAbsorptionAmount(0);
            return;
        }
        double vanillaAbsorptionHearts = calculateVanillaHearts(absorptionAmount);
        player.setAbsorptionAmount((float) (vanillaAbsorptionHearts * 2)); // half-hearts
    }

    /**
     * Same health-to-vanilla scaling as {@code BukkitStatManager.calculateVanillaHearts}.
     * 100 RPG HP = 10 vanilla hearts; above 100 uses a softer curve, capped at 20.
     */
    private double calculateVanillaHearts(double rpgAmount) {
        if (rpgAmount <= 100) {
            return rpgAmount / 10.0;
        }
        double hearts = (100 + (rpgAmount - 100) * 2) / 10.0;
        return Math.min(hearts, 20.0);
    }

    private static class AbsorptionState {
        boolean previousGlowing;
        int stackCount;
    }
}
