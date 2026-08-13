package dev.core.storage.config;

public interface ConfigProvider {
	ConfigSection getRoot();

	ConfigSection getSection(String path);

	void save();
}
