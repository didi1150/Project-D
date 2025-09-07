package dev.core.storage.config;

import java.util.List;
import java.util.Set;

public interface ConfigSection {
	String getString(String path, String def);

	int getInt(String path, int def);

	double getDouble(String path, double def);

	boolean getBoolean(String path, boolean def);

	List<String> getStringList(String path);

	ConfigSection getSection(String path);

	List<ConfigSection> getSectionList(String path);

	void set(String path, Object value);

	Set<String> getKeys();
}
