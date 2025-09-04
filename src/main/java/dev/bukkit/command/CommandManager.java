package dev.bukkit.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import dev.bukkit.command.impl.GiveItemCommand;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {

	private static CommandManager instance;

	public static CommandManager getInstance() {
		if (instance == null)
			instance = new CommandManager();
		return instance;
	}

	private final Map<String, CommandExecutor> commandMap;

	private CommandManager() {
		commandMap = new HashMap<>();
		commandMap.put("giveItem", new GiveItemCommand());
	}

	public void registerCommands(JavaPlugin javaPlugin) {
		commandMap.forEach((name, commandExecutor) -> {
			javaPlugin.getCommand(name).setExecutor(commandExecutor);
		});
	}

	public void createCommand(String name, CommandExecutor commandExecutor) {
		if (commandMap.containsKey(name))
			throw new IllegalArgumentException("Command already present");
		commandMap.put(name, commandExecutor);
	}

}
