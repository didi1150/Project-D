package dev.bukkit.ability;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.summon.SummonedMobRPGEntity;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.SummonRegistry;

/**
 * Soul Recall — left-click on the Soul Tome: every active summon of the caster
 * is teleported to the caster's position. Instant; only starts the cooldown
 * when at least one summon was actually moved, so an empty field costs nothing.
 */
public class BukkitSoulRecallEffect extends Effect {

    public BukkitSoulRecallEffect(String cooldownKey) {
        super(null, 1, true, cooldownKey);
    }

    private static Player resolvePlayer(RPGEntity caster) {
        if (caster instanceof BukkitPlayerEntity playerEntity) {
            return playerEntity.getPlayer().orElse(null);
        }
        return null;
    }

    @Override
    public void cast(RPGEntity caster, CooldownSink cooldownSink) {
        Player player = resolvePlayer(caster);
        if (player == null) {
            return;
        }

        int recalled = 0;
        for (var summonId : SummonRegistry.getInstance().getSummons(player.getUniqueId())) {
            var optSummon = EntityManager.getInstance().getEntity(summonId);
            if (optSummon.isEmpty() || !(optSummon.get() instanceof SummonedMobRPGEntity summon)) {
                continue;
            }
            org.bukkit.entity.LivingEntity vanilla = summon.getVanilla();
            if (vanilla == null || !vanilla.isValid() || vanilla.isDead()) {
                continue;
            }
            Location to = player.getLocation();
            vanilla.teleport(to);
            if (vanilla instanceof org.bukkit.entity.Mob mob) {
                mob.setTarget(null); // drop whatever the summon was chasing; re-acquire on the next tick
            }
            recalled++;
        }

        if (recalled == 0) {
            player.sendMessage(ChatColor.GRAY + "You have no active summons to recall.");
            return; // no cooldown spent on an empty cast
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        player.sendMessage(ChatColor.GREEN + "Recalled " + recalled + " summon"
                + (recalled == 1 ? "" : "s") + " to your side.");
        cooldownSink.startCooldown();
    }

    @Override
    public void cancel() {
    }
}