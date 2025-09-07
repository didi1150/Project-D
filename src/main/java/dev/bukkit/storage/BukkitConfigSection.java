package dev.bukkit.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;

import dev.core.storage.config.ConfigSection;

public class BukkitConfigSection implements ConfigSection {

	private final ConfigurationSection handle;
	private final boolean autoCreate;

	public BukkitConfigSection(ConfigurationSection handle) {
		this(handle, true);
	}

	public BukkitConfigSection(ConfigurationSection handle, boolean autoCreate) {
		this.handle = handle;
		this.autoCreate = autoCreate;
	}

	@Override
	public String getString(String path, String def) {
		if (handle == null)
			return def;
		return handle.getString(path, def);
	}

	@Override
	public int getInt(String path, int def) {
		if (handle == null)
			return def;
		return handle.getInt(path, def);
	}

	@Override
	public double getDouble(String path, double def) {
		if (handle == null)
			return def;
		return handle.getDouble(path, def);
	}

	@Override
	public boolean getBoolean(String path, boolean def) {
		if (handle == null)
			return def;
		return handle.getBoolean(path, def);
	}

	@Override
	public List<String> getStringList(String path) {
		if (handle == null) {
			return Collections.emptyList();
		}

		List<String> list = handle.getStringList(path);

		// Auto-create empty list if null and autoCreate is enabled
		if ((list == null || list.isEmpty()) && autoCreate && !handle.contains(path)) {
			handle.set(path, new ArrayList<String>());
			return new ArrayList<>();
		}

		return list == null ? Collections.emptyList() : list;
	}

	@Override
	public ConfigSection getSection(String path) {
		if (handle == null)
			return null;

		ConfigurationSection sec = handle.getConfigurationSection(path);

		// Auto-create section if null and autoCreate is enabled
		if (sec == null && autoCreate) {
			sec = handle.createSection(path);
		}

		return sec == null ? null : new BukkitConfigSection(sec, autoCreate);
	}

	/**
	 * Returns a list of ConfigSection wrappers for a list-of-maps stored at 'path'.
	 * Auto-creates an empty list if the path doesn't exist and autoCreate is
	 * enabled.
	 */
	@Override
	public List<ConfigSection> getSectionList(String path) {
		if (handle == null)
			return Collections.emptyList();

		// If the path is actually a configuration section containing numbered keys (map
		// of subsections)
		ConfigurationSection possibleSection = handle.getConfigurationSection(path);
		if (possibleSection != null) {
			// Treat subsections (map) -> return each child as a section
			List<ConfigSection> out = new ArrayList<>();
			for (String key : possibleSection.getKeys(false)) {
				ConfigurationSection child = possibleSection.getConfigurationSection(key);
				if (child != null) {
					out.add(new BukkitConfigSection(child, autoCreate));
				}
			}
			return out;
		}

		// If the path is a list of maps (common YAML pattern), fetch it as map-list and
		// wrap
		List<Map<?, ?>> mapList = handle.getMapList(path);

		// Auto-create empty list if null/empty and autoCreate is enabled
		if ((mapList == null || mapList.isEmpty()) && autoCreate && !handle.contains(path)) {
			handle.set(path, new ArrayList<Map<String, Object>>());
			return new ArrayList<>();
		}

		if (mapList == null || mapList.isEmpty()) {
			return Collections.emptyList();
		}

		List<ConfigSection> out = new ArrayList<>(mapList.size());
		for (Map<?, ?> map : mapList) {
			// Create a non-mutating wrapper around the map
			out.add(new MapConfigSection(map));
		}
		return out;
	}

	@Override
	public void set(String path, Object value) {
		if (handle == null)
			return;
		handle.set(path, value);
	}

	@Override
	public Set<String> getKeys() {
		if (handle == null)
			return Collections.emptySet();
		return handle.getKeys(false);
	}

	/**
	 * Creates a new section at the specified path and returns it. This is useful
	 * for programmatically building configuration structures.
	 * 
	 * @param path The path where to create the section
	 * @return A new ConfigSection wrapper for the created section
	 */
	public ConfigSection createSection(String path) {
		if (handle == null)
			return null;
		ConfigurationSection newSection = handle.createSection(path);
		return new BukkitConfigSection(newSection, autoCreate);
	}

	/**
	 * Creates a new section in a list at the specified path. If the path doesn't
	 * exist, creates a new list. If the path exists but isn't a list, converts it
	 * to a list.
	 * 
	 * @param path The path where the list should be
	 * @return A new ConfigSection that can be configured and will be added to the
	 *         list
	 */
	public ConfigSection createSectionInList(String path) {
		if (handle == null)
			return null;

		// Get current value at path
		Object current = handle.get(path);
		List<Map<String, Object>> mapList;

		if (current instanceof List<?>) {
			// Already a list, cast it
			try {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> existingList = (List<Map<String, Object>>) current;
				mapList = existingList;
			} catch (ClassCastException e) {
				// List exists but contains non-map elements, create new list
				mapList = new ArrayList<>();
				handle.set(path, mapList);
			}
		} else {
			// Path doesn't exist or isn't a list, create new list
			mapList = new ArrayList<>();
			handle.set(path, mapList);
		}

		// Create new map entry for the list
		Map<String, Object> newEntry = new HashMap<>();
		mapList.add(newEntry);

		// Return a writable map-backed section
		return new WritableMapConfigSection(newEntry);
	}

	/**
	 * Ensures a path exists by creating it if it doesn't. For simple values, sets
	 * them to a default. For sections, creates empty sections. For lists, creates
	 * empty lists.
	 * 
	 * @param path         The path to ensure exists
	 * @param defaultValue The default value to set if the path doesn't exist
	 */
	public void ensurePath(String path, Object defaultValue) {
		if (handle == null)
			return;

		if (!handle.contains(path)) {
			handle.set(path, defaultValue);
		}
	}

	/**
	 * Check if auto-creation is enabled for this section
	 * 
	 * @return true if auto-creation is enabled
	 */
	public boolean isAutoCreateEnabled() {
		return autoCreate;
	}

	// --------------------------
	// Internal wrapper for map-backed section-list entries (read-only)
	// --------------------------
	private static class MapConfigSection implements ConfigSection {
		protected final Map<?, ?> map;

		MapConfigSection(Map<?, ?> map) {
			this.map = map;
		}

		@Override
		public String getString(String path, String def) {
			Object v = map.get(path);
			return v == null ? def : v.toString();
		}

		@Override
		public int getInt(String path, int def) {
			Object v = map.get(path);
			if (v instanceof Number)
				return ((Number) v).intValue();
			try {
				return v == null ? def : Integer.parseInt(v.toString());
			} catch (Exception ex) {
				return def;
			}
		}

		@Override
		public double getDouble(String path, double def) {
			Object v = map.get(path);
			if (v instanceof Number)
				return ((Number) v).doubleValue();
			try {
				return v == null ? def : Double.parseDouble(v.toString());
			} catch (Exception ex) {
				return def;
			}
		}

		@Override
		public boolean getBoolean(String path, boolean def) {
			Object v = map.get(path);
			if (v instanceof Boolean)
				return (Boolean) v;
			if (v == null)
				return def;
			return Boolean.parseBoolean(v.toString());
		}

		@Override
		public List<String> getStringList(String path) {
			Object v = map.get(path);
			if (v instanceof List<?>) {
				List<?> raw = (List<?>) v;
				List<String> out = new ArrayList<>();
				for (Object o : raw) {
					out.add(o == null ? null : o.toString());
				}
				return out;
			}
			return Collections.emptyList();
		}

		@Override
		public ConfigSection getSection(String path) {
			Object v = map.get(path);
			if (v instanceof Map<?, ?>) {
				return new MapConfigSection((Map<?, ?>) v);
			}
			return null;
		}

		@Override
		public List<ConfigSection> getSectionList(String path) {
			Object v = map.get(path);
			if (v instanceof List<?>) {
				List<?> raw = (List<?>) v;
				List<ConfigSection> out = new ArrayList<>();
				for (Object o : raw) {
					if (o instanceof Map<?, ?>)
						out.add(new MapConfigSection((Map<?, ?>) o));
				}
				return out;
			}
			return Collections.emptyList();
		}

		@Override
		public void set(String path, Object value) {
			throw new UnsupportedOperationException("Map-backed ConfigSection is read-only");
		}

		@Override
		public Set<String> getKeys() {
			// Keys at top-level of this map
			@SuppressWarnings("unchecked")
			Set<String> keys = (Set<String>) (map != null ? map.keySet() : Collections.emptySet());
			return keys;
		}
	}

	// --------------------------
	// Writable wrapper for map-backed section-list entries
	// --------------------------
	private static class WritableMapConfigSection extends MapConfigSection {
		private final Map<String, Object> writableMap;

		WritableMapConfigSection(Map<String, Object> map) {
			super(map);
			this.writableMap = map;
		}

		@Override
		public void set(String path, Object value) {
			writableMap.put(path, value);
		}

		@Override
		public ConfigSection getSection(String path) {
			Object v = writableMap.get(path);
			if (v instanceof Map<?, ?>) {
				@SuppressWarnings("unchecked")
				Map<String, Object> subMap = (Map<String, Object>) v;
				return new WritableMapConfigSection(subMap);
			}

			// Auto-create section if it doesn't exist
			Map<String, Object> newSection = new HashMap<>();
			writableMap.put(path, newSection);
			return new WritableMapConfigSection(newSection);
		}
	}
}