package dev.bukkit;

import dev.bukkit.command.CommandManager;
import dev.bukkit.event.EventListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class DMain extends JavaPlugin {

	@Override
	public void onEnable() {
		// Plugin startup logic
		Bukkit.getConsoleSender().sendMessage("Dmain started.");

		CommandManager.getInstance().registerCommands(this);
		Bukkit.getPluginManager().registerEvents(new EventListener(), this);
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}
}
