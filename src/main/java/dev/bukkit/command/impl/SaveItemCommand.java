package dev.bukkit.command.impl;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.core.entity.EntityManager;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemLoader;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.storage.config.ConfigProvider;

public class SaveItemCommand implements CommandExecutor {

	private ConfigProvider configProvider;

	public SaveItemCommand(ConfigProvider configProvider) {
		this.configProvider = configProvider;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (sender instanceof Player player) {
			EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(p -> {
				if (args.length == 0) {
					RPGItem item = p.getEquipmentManager().getEquippedItem(EquipmentSlot.MAIN_HAND);
					if (item == null) {
						player.sendMessage("You need to hold the item in your main hand");
					} else {
						RPGItemLoader.saveItem(configProvider, item);
						player.sendMessage(ChatColor.YELLOW + item.getId() + ChatColor.GREEN
								+ " has been successfully saved to the config.");
					}
				}
				if (args.length == 1) {
					String id = args[0];
					RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(item -> {
						player.getInventory().addItem(BukkitItemStackAdapter.toItemStack(item, p));
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
