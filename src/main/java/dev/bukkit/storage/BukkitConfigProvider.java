package dev.bukkit.storage;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public class BukkitConfigProvider implements ConfigProvider {

    private final String fileName;
    private final File file;
    private final FileConfiguration config;

    public BukkitConfigProvider(Plugin plugin, String fileName) {
        this.fileName = fileName;
        this.file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            System.out.println("Did not find " + fileName + ", copying default " + fileName + "...");
            try {
                plugin.saveResource(fileName, true);
                System.out.println("Successfully copied default " + fileName);
            } catch (IllegalArgumentException e) {
                System.out.println("Did not find default " + fileName + ", creating empty file...");
                try {
                    file.createNewFile();
                } catch (IOException e2) {
                    throw new RuntimeException("Could not create config file: " + fileName, e);
                }
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /** Reloads the underlying yaml from disk, replacing current in-memory values. */
    public void reload() {
        try {
            // clear and load anew so removed keys disappear
            FileConfiguration reloaded = YamlConfiguration.loadConfiguration(file);
            // copy reloaded keys into existing config instance so existing section wrappers stay valid
            for (String key : new java.util.HashSet<>(config.getKeys(true))) {
                config.set(key, null);
            }
            for (String key : reloaded.getKeys(true)) {
                config.set(key, reloaded.get(key));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload config file: " + fileName, e);
        }
    }

    @Override
    public ConfigSection getRoot() {
        return new BukkitConfigSection(config);
    }

    @Override
    public ConfigSection getSection(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            section = config.createSection(path);
        }
        return new BukkitConfigSection(section);
    }

    @Override
    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config file: " + fileName, e);
        }
    }

    public FileConfiguration getHandle() {
        return config;
    }

    public String getFileName() {
        return fileName;
    }

}
