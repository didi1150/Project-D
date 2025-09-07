package dev.core.item.loader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;

public class RPGItemRegistry {

	private static RPGItemRegistry instance;

	private final Map<String, RPGItem> allItems;
	private final Map<String, RPGItemSet> allItemSets;

	private RPGItemRegistry() {
		this.allItems = new HashMap<String, RPGItem>();
		this.allItemSets = new HashMap<String, RPGItemSet>();

//		addItem(new RPGItem("test_particles", "Particle Test", EquipmentSlot.MAIN_HAND, Arrays.asList(),
//				Arrays.asList(), Arrays.asList(new ParticleTestAbility())));
//		addItem(RPGItem.builder("BONEMERANG", "Bonemerang", EquipmentSlot.MAIN_HAND)
//				.withAbilities(Arrays.asList(new SwingBoneAbility())).withActiveStats(Arrays.asList(new StatModifier(20,
//						ModifierType.FLAT, StatType.ATTACK_DAMAGE, "BONE_SWING", System.currentTimeMillis())))
//				.withMaterial("BONE").build());
	}

	public static RPGItemRegistry getInstance() {
		if (instance == null) {
			instance = new RPGItemRegistry();
		}
		return instance;
	}

	public void addAll(Map<String, RPGItem> items) {
		this.allItems.putAll(items);
	}

	public Optional<RPGItem> getItem(String id) {
		if (!allItems.containsKey(id)) {
			return Optional.empty();
		}
		return Optional.of(allItems.get(id));
	}

	public Optional<RPGItemSet> getItemSet(String id) {
		if (!allItemSets.containsKey(id)) {
			return Optional.empty();
		}
		return Optional.of(allItemSets.get(id));
	}

}
