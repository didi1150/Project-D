package dev.bukkit.ability;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.summon.SoulFragment;
import dev.bukkit.summon.SoulTome;
import dev.bukkit.summon.SummonStats;
import dev.bukkit.summon.SummonedMobRPGEntity;
import dev.core.ability.CooldownSink;
import dev.core.ability.Effect;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.SummonRegistry;
import dev.core.entity.rpgclass.RPGClassType;

/**
 * Soul Store — shift-right-click on the Soul Tome: active summons of the
 * caster are dismissed oldest-first (queue/FIFO) and their captured souls are
 * stored back into the tome. Summons beyond the tome's remaining capacity
 * stay out — the newest ones fight on — so no soul is ever lost. Teleports
 * nothing. The cooldown only starts when at least one summon was actually
 * stored back, so an empty field costs nothing.
 */
public class BukkitSoulStoreEffect extends Effect {

    public BukkitSoulStoreEffect(String cooldownKey) {
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
        ItemStack tome = SoulTome.findTome(player);
        if (tome == null) {
            player.sendMessage(ChatColor.GRAY + "You need a Soul Tome in your inventory to store summons.");
            return;
        }

        int level = DMain.getInstance().getProgressionService()
                .getProgression(player.getUniqueId(), RPGClassType.SUPPORT).getLevel();
        int capacity = SummonStats.capacityForLevel(level);

        // Dismiss oldest-first (queue/FIFO): the registry lists summons in
        // spawn order, so each stored soul re-enters the tome the same way it
        // would have been popped; summons past the remaining capacity stay out.
        int stored = 0;
        for (var summonId : SummonRegistry.getInstance().getSummons(player.getUniqueId())) {
            var optSummon = EntityManager.getInstance().getEntity(summonId);
            if (optSummon.isEmpty() || !(optSummon.get() instanceof SummonedMobRPGEntity summon)) {
                continue;
            }
            SoulFragment fragment = summon.getSoulFragment();
            if (fragment == null) {
                continue;
            }
            if (SoulTome.countSouls(tome) >= capacity) {
                continue; // no free slot: leave the summon out rather than lose the soul
            }
            if (!SoulTome.addSoul(tome, fragment, capacity)) {
                continue;
            }
            summon.despawn();
            stored++;
        }

        int leftOut = SummonRegistry.getInstance().getSummons(player.getUniqueId()).size();
        if (stored == 0) {
            if (leftOut == 0) {
                player.sendMessage(ChatColor.GRAY + "You have no active summons to store.");
            } else {
                player.sendMessage(ChatColor.RED + "Your tome is already full (" + SoulTome.countSouls(tome) + "/"
                        + capacity + "): no summon was stored.");
            }
            return; // no cooldown spent on an empty cast
        }

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 1.4f);
        player.sendMessage(ChatColor.GREEN + "Stored " + stored + " summon"
                + (stored == 1 ? "" : "s") + " back into your tome (" + SoulTome.countSouls(tome) + "/" + capacity
                + " souls held).");
        if (leftOut > 0) {
            player.sendMessage(ChatColor.GRAY + "Your tome is full: " + leftOut + " summon"
                    + (leftOut == 1 ? " was" : "s were") + " left fighting.");
        }
        cooldownSink.startCooldown();
    }

    @Override
    public void cancel() {
    }
}