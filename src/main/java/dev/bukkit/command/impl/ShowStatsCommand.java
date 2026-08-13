package dev.bukkit.command.impl;

import java.util.Map.Entry;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.stat.Stat;
import dev.core.stat.StatType;

public class ShowStatsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
            if (optional.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Could not find profile");
            } else {
                BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();

                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                for (Entry<StatType, Stat> entry : playerEntity.getStatManager().getStats().entrySet()) {
                    StatType type = entry.getKey();
                    player.sendMessage(BukkitTextColorAdapter.colored(type.getColor(),
                            type.formatValue(entry.getValue().getCurrent(System.currentTimeMillis()), false)));

                }
                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
            }
        }
        return false;
    }

}
