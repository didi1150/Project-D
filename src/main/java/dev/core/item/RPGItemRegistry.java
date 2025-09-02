package dev.core.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RPGItemRegistry {

	private static RPGItemRegistry instance;

	private Map<String, RPGItem> allItems;

	private RPGItemRegistry() {
		this.allItems = new HashMap<String, RPGItem>();
	}

	public static RPGItemRegistry getInstance() {
		if (instance == null) {
			instance = new RPGItemRegistry();
		}
		return instance;
	}

	private void addItem(RPGItem item) {
		allItems.put(item.getId(), item);
	}

	public Optional<RPGItem> getItem(String id) {
		if (!allItems.containsKey(id)) {
			return Optional.empty();
		}
		return Optional.of(allItems.get(id));
	}

}
