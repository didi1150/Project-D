package dev.core.item;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.core.ability.impl.ParticleTestAbility;

public class RPGItemRegistry {

	private static RPGItemRegistry instance;

	private final Map<String, RPGItem> allItems;
	private final Map<String, RPGItemSet> allItemSets;

	private RPGItemRegistry() {
		this.allItems = new HashMap<String, RPGItem>();
		this.allItemSets = new HashMap<String, RPGItemSet>();

		addItem(new RPGItem("test_particles", "Particle Test", EquipmentSlot.MAIN_HAND, Arrays.asList(),
				Arrays.asList(), Arrays.asList(new ParticleTestAbility())));
	}

	public static RPGItemRegistry getInstance() {
		if (instance == null) {
			instance = new RPGItemRegistry();
		}
		return instance;
	}

	private void addItem(RPGItem item) {
		if (allItems.containsKey(item.getId())) {
			return;
		}
		allItems.put(item.getId(), item);
	}

	public Optional<RPGItem> getItem(String id) {
		if (!allItems.containsKey(id)) {
			return Optional.empty();
		}
		return Optional.of(allItems.get(id));
	}

	private void addItemSet(RPGItemSet itemSet) {
		if (allItemSets.containsKey(itemSet.getId())) {
			return;
		}
		allItemSets.put(itemSet.getId(), itemSet);
	}

	public Optional<RPGItemSet> getItemSet(String id) {
		if (!allItemSets.containsKey(id)) {
			return Optional.empty();
		}
		return Optional.of(allItemSets.get(id));
	}

}
