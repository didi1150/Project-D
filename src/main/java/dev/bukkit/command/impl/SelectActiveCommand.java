package dev.bukkit.command.impl;

import java.util.List;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;

public class SelectActiveCommand implements TabExecutor {

    private ClassProgressionService classProgressionService;

    public SelectActiveCommand(ClassProgressionService classProgressionService) {
        this.classProgressionService = classProgressionService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 1) {
                Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                if (optional.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Could not find profile");
                } else {
                    BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();

                    playerEntity.getPlayerProgression().setActiveClass(RPGClassType.valueOf(args[0]),
                            playerEntity.getStatManager());
                    classProgressionService.setActiveClass(playerEntity.getPlayerProgression());
                }
            }
        }
        return false;
    }

}
