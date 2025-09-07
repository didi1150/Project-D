package dev.bukkit.storage;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import dev.core.storage.config.ConfigProvider;

public class BukkitConfigManager {

	private final Plugin plugin;
    private final Map<String, ConfigProvider> providers = new HashMap<>();

    public BukkitConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public ConfigProvider getProvider(String fileName) {
        return providers.computeIfAbsent(fileName, name -> new BukkitConfigProvider(plugin, name));
    }

    public void saveAll() {
        for (ConfigProvider provider : providers.values()) {
            provider.save();
        }
    }

}
