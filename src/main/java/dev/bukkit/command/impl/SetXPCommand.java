package dev.bukkit.command.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;

public class SetXPCommand implements TabExecutor {

    private ClassProgressionService classProgressionService;

    public SetXPCommand(ClassProgressionService classProgressionService) {
        this.classProgressionService = classProgressionService;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            if (args.length == 1) {
                List<String> completions = new ArrayList<>();

                List<String> classTypes = Arrays.asList(RPGClassType.values()).stream()
                        .filter(classType -> classType != RPGClassType.NONE).map(classType -> classType.name())
                        .toList();

                StringUtil.copyPartialMatches(args[0], classTypes, completions);
                return completions;
            }
        }

        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 2) {
                Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                if (optional.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Could not find profile");
                } else {
                    BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                    try {
                        RPGClassType targetClass = RPGClassType.valueOf(args[0]);
                        int newXp = Integer.valueOf(args[1]);

                        playerEntity.getPlayerProgression().getProgression(targetClass).setXp(newXp);
                        classProgressionService.saveClassProgression(playerEntity.getUuid(),
                                playerEntity.getPlayerProgression().getProgression(targetClass));

                    } catch (Exception e) {
                        player.sendMessage("/setXp <class> <xp>");
                    }
                }
            }
        }
        return false;
    }

}
