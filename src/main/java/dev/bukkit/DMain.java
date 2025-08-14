package dev.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class DMain extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getConsoleSender().sendMessage("Dmain started.");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
