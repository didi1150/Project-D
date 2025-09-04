package dev.bukkit.command.impl;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.ItemStackAdapter;
import dev.core.entity.EntityManager;
import dev.core.item.RPGItemRegistry;

public class GiveItemCommand implements CommandExecutor {

	public GiveItemCommand() {
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (sender instanceof Player player) {
			EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
				if (args.length == 1) {
					String id = args[0];
					RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
						player.getInventory().addItem(ItemStackAdapter.toItemStack(item, Material.BONE));
					}, () -> {
						player.sendMessage("This item does not exist");
					});
					BukkitInventorySync.syncInventory(p, player);
				}
			});

		}
		return false;
	}

}
