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

public class GiveItemCommand implements TabExecutor {

    public GiveItemCommand() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (args.length == 1) {
                EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                    String id = args[0];
                    RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
                        player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item));
                        sender.sendMessage("Success! You received " + item.getName());
                    }, () -> {
                        player.sendMessage("This item does not exist");
                    });
                    BukkitInventorySync.syncInventory(p, player);
                });
            } else if (args.length == 2) {
                String playerName = args[0];
                String itemId = args[1];

                Player target = Bukkit.getPlayer(playerName);
                if (target == null) {
                    sender.sendMessage("Player not found");
                } else {
                    EntityManager.getInstance().getEntity(target.getUniqueId()).ifPresent(p -> {
                        if (args.length == 1) {
                            RPGItemRegistry.getInstance().getItem(itemId).ifPresentOrElse(item -> {
                                target.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item));
                                sender.sendMessage(
                                        "Success! Player " + target.getName() + " received " + item.getName());
                            }, () -> {
                                sender.sendMessage("This item does not exist");
                            });
                            BukkitInventorySync.syncInventory(p, target);
                        }
                    });
                }
            }

        } else if (sender instanceof ConsoleCommandSender) {
            if (args.length != 2) {
                sender.sendMessage("Incorrect Syntax! /giveItem <player> <item>");
            } else {
                String playerName = args[0];
                String itemId = args[1];

                Player player = Bukkit.getPlayer(playerName);
                if (player == null) {
                    sender.sendMessage("Player not found");
                } else {
                    EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
                        if (args.length == 1) {
                            RPGItemRegistry.getInstance().getItem(itemId).ifPresentOrElse(item -> {
                                player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item));
                                sender.sendMessage(
                                        "Success! Player " + player.getName() + " received " + item.getName());
                            }, () -> {
                                sender.sendMessage("This item does not exist");
                            });
                            BukkitInventorySync.syncInventory(p, player);
                        }
                    });
                }
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            if (args.length >= 1) {
                List<String> completions = new ArrayList<>();

                List<String> subCommandNames = RPGItemRegistry.getInstance().allItems().entrySet().stream()
                        .map(entry -> entry.getValue().getId()).toList();

                StringUtil.copyPartialMatches(args[0], subCommandNames, completions);
                return completions;
            }
        }

        else if (sender instanceof ConsoleCommandSender) {
            if (args.length == 2) {
                List<String> completions = new ArrayList<>();

                List<String> subCommandNames = RPGItemRegistry.getInstance().allItems().entrySet().stream()
                        .map(entry -> entry.getValue().getId()).toList();

                StringUtil.copyPartialMatches(args[1], subCommandNames, completions);
                return completions;
            }
        }
        return null;
    }

}
