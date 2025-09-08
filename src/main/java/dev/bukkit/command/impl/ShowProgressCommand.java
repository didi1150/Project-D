package dev.bukkit.command.impl;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.storage.database.PlayerClassProgression;

public class ShowProgressCommand implements TabExecutor {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {

        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
            if (optional.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Could not find profile");
            } else {
                BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();

                // Display active class
                RPGClassType activeClass = playerEntity.getPlayerProgression().getActiveClass();
                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                player.sendMessage(ChatColor.YELLOW + "Active Class: " + ChatColor.GREEN
                        + (activeClass != null ? activeClass.name() : "None"));
                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");

                // Display all class progressions
                for (Entry<RPGClassType, PlayerClassProgression> entry : playerEntity.getPlayerProgression()
                        .getAllProgressions().entrySet()) {
                    RPGClassType classType = entry.getKey();
                    PlayerClassProgression progression = entry.getValue();

                    // Highlight active class
                    String classPrefix = classType.equals(activeClass) ? ChatColor.AQUA + "★ " : ChatColor.GRAY + "  ";

                    player.sendMessage(classPrefix + ChatColor.WHITE + classType.name() + ":");
                    player.sendMessage(ChatColor.GRAY + "  Level: " + ChatColor.YELLOW + progression.getLevel());
                    player.sendMessage(ChatColor.GRAY + "  XP: " + ChatColor.GREEN + progression.getXp());
                    player.sendMessage(ChatColor.GRAY + "  Usable Items: " + ChatColor.LIGHT_PURPLE
                            + progression.getUsableItems());
                    player.sendMessage(""); // Empty line for spacing
                }

                player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                player.sendMessage(ChatColor.GRAY + "★ = Active Class");
            }
        }
        return true;
    }

}
