package dev.bukkit.storage;

import java.io.File;
import java.io.IOException;

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
//			try {
//				file.createNewFile();
//			} catch (IOException e) {
//				throw new RuntimeException("Could not create config file: " + fileName, e);
//			}
			System.out.println("Did not find " + fileName + ", copying default " + fileName + "...");
			plugin.saveResource(fileName, false);
			System.out.println("Successfully copied default " + fileName);
		}

		this.config = YamlConfiguration.loadConfiguration(file);
	}

	@Override
	public ConfigSection getRoot() {
		return new BukkitConfigSection(config);
	}

	@Override
	public ConfigSection getSection(String path) {
		return new BukkitConfigSection(config.getConfigurationSection(path));
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
