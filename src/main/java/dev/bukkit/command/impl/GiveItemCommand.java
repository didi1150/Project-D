package dev.bukkit.command.impl;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.core.entity.EntityManager;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.item.RPGItem;

public class GiveItemCommand implements TabExecutor {

    public GiveItemCommand() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 1) {
                giveItemToPlayer(player, player, args[0], sender);
            } else if (args.length == 2) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("Player not found");
                } else {
                    giveItemToPlayer(target, player, args[1], sender);
                }
            }
        } else if (sender instanceof ConsoleCommandSender) {
            if (args.length != 2) {
                sender.sendMessage("Incorrect Syntax! /giveItem <player> <item>");
            } else {
                Player player = Bukkit.getPlayer(args[0]);
                if (player == null) {
                    sender.sendMessage("Player not found");
                } else {
                    giveItemToPlayer(player, null, args[1], sender);
                }
            }
        }
        return false;
    }

    /**
     * Helper method to give an item to a player and sync inventory.
     *
     * @param recipient The player receiving the item
     * @param executor The player executing the command (null if console)
     * @param itemId The ID of the item to give
     * @param sender The command sender for feedback
     */
    private void giveItemToPlayer(Player recipient, Player executor, String itemId, CommandSender sender) {
        EntityManager.getInstance().getEntity(recipient.getUniqueId()).ifPresent(entity -> {
            RPGItemRegistry.getInstance().getItem(itemId).ifPresentOrElse(
                item -> {
                    recipient.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item));
                    sender.sendMessage("Success! Player " + recipient.getName() + " received " + item.getName());
                    BukkitInventorySync.syncInventory(entity, recipient);
                },
                () -> sender.sendMessage("This item does not exist")
            );
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();
        List<String> itemIds = RPGItemRegistry.getInstance().allItems().values().stream()
            .map(RPGItem::getId)
            .toList();

        if (sender instanceof Player) {
            if (args.length >= 1) {
                StringUtil.copyPartialMatches(args[args.length - 1], itemIds, completions);
            }
        } else if (sender instanceof ConsoleCommandSender) {
            if (args.length == 1) {
                // Complete player names
                List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
                StringUtil.copyPartialMatches(args[0], playerNames, completions);
            } else if (args.length == 2) {
                // Complete item IDs
                StringUtil.copyPartialMatches(args[1], itemIds, completions);
            }
        }

        return completions;
    }
}
