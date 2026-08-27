package dev.bukkit.status.behavior;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import dev.bukkit.status.StatusEffectBehavior;
import dev.bukkit.status.StatusEffectContext;
import dev.core.entity.RPGEntity;

/**
 * Absorption shield status effect: absorbs all damage before real HP is hit.
 * Visualised with vanilla golden absorption hearts (players) and a yellow
 * glowing outline on all entities via scoreboard team coloring.
 */
public final class AbsorptionStatusEffectBehavior implements StatusEffectBehavior {

    private static final String TEAM_NAME = "rpg_absorption_glow";

    private final Map<UUID, AbsorptionState> states = new ConcurrentHashMap<>();

    @Override
    public void onApply(StatusEffectContext ctx) {
        RPGEntity rpg = ctx.getRpgEntity();
        LivingEntity living = ctx.getLivingEntity();
        UUID uuid = rpg.getUuid();

        // Set the RPG entity's absorption amount from the effect potency.
        rpg.setAbsorptionAmount(ctx.getEffect().getPotency());

        AbsorptionState state = new AbsorptionState();
        state.previousGlowing = living.isGlowing();
        states.put(uuid, state);

        // Enable yellow glow via scoreboard team.
        living.setGlowing(true);
        addToYellowTeam(living);

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

        if (!living.isValid() || living.isDead()) {
            states.remove(uuid);
            return;
        }

        // Defensive sync: ensure glow stays active and yellow.
        if (!living.isGlowing()) {
            living.setGlowing(true);
        }
        ensureInYellowTeam(living);

        // Sync vanilla absorption hearts for players every tick (absorption may
        // have been consumed by damage since the last tick).
        if (living instanceof Player player) {
            syncVanillaAbsorption(player, rpg.getAbsorptionAmount());
        }

        // If absorption has been fully consumed by damage, end the effect.
        if (rpg.getAbsorptionAmount() <= 0) {
            ctx.getRpgEntity().getStatusEffectManager().remove(ctx.getRpgEntity(),
                    dev.core.status.StatusEffectType.ABSORPTION);
        }
    }

    @Override
    public void onEnd(StatusEffectContext ctx) {
        RPGEntity rpg = ctx.getRpgEntity();
        LivingEntity living = ctx.getLivingEntity();
        UUID uuid = rpg.getUuid();

        // Clear absorption amount.
        rpg.setAbsorptionAmount(0);

        AbsorptionState state = states.remove(uuid);

        if (living != null && living.isValid() && !living.isDead()) {
            // Remove from yellow team.
            removeFromYellowTeam(living);

            // Restore previous glow state.
            living.setGlowing(state != null && state.previousGlowing);

            // Clear vanilla absorption hearts for players.
            if (living instanceof Player player) {
                player.setAbsorptionAmount(0);
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

    // =========================- Team glow ==========================

    private static Team getOrCreateYellowTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.setColor(ChatColor.YELLOW);
        }
        return team;
    }

    private static void addToYellowTeam(LivingEntity living) {
        Team team = getOrCreateYellowTeam();
        team.addEntry(living.getUniqueId().toString());
    }

    private static void ensureInYellowTeam(LivingEntity living) {
        Team team = getOrCreateYellowTeam();
        String entry = living.getUniqueId().toString();
        if (!team.hasEntry(entry)) {
            team.addEntry(entry);
        }
    }

    private static void removeFromYellowTeam(LivingEntity living) {
        Team team = getOrCreateYellowTeam();
        team.removeEntry(living.getUniqueId().toString());
    }

    private static class AbsorptionState {
        boolean previousGlowing;
    }
}
