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

    /** Force reload of a provider from disk (used for live /hud reload). */
    public ConfigProvider reloadProvider(String fileName) {
        ConfigProvider existing = providers.get(fileName);
        if (existing instanceof BukkitConfigProvider bcp) {
            bcp.reload();
            return bcp;
        }
        // not yet loaded -> load normally
        return getProvider(fileName);
    }

    public void saveAll() {
        for (ConfigProvider provider : providers.values()) {
            provider.save();
        }
    }

}
