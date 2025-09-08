package dev.bukkit.command;

import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import dev.bukkit.command.impl.GiveItemCommand;
import dev.bukkit.command.impl.SaveItemCommand;
import dev.bukkit.command.impl.SelectActiveCommand;
import dev.bukkit.command.impl.SetXPCommand;
import dev.bukkit.command.impl.ShowProgressCommand;
import dev.bukkit.command.impl.ShowStatsCommand;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.core.storage.config.ConfigProvider;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {

    private static CommandManager instance;

    public static CommandManager getInstance(ConfigProvider configProvider,
            ClassProgressionService classProgressionService) {
        if (instance == null)
            instance = new CommandManager(configProvider, classProgressionService);
        return instance;
    }

    private final Map<String, CommandExecutor> commandMap;

    private CommandManager(ConfigProvider configProvider, ClassProgressionService classProgressionService) {
        commandMap = new HashMap<>();
        commandMap.put("giveItem", new GiveItemCommand());
        commandMap.put("saveItem", new SaveItemCommand(configProvider));
        commandMap.put("showProgress", new ShowProgressCommand());
        commandMap.put("selectActive", new SelectActiveCommand(classProgressionService));
        commandMap.put("showStats", new ShowStatsCommand());
        commandMap.put("setXp", new SetXPCommand(classProgressionService));
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
