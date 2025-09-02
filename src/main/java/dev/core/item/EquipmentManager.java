package dev.core.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.entity.RPGEntity;
import dev.core.event.Event;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.stat.StatModifier;
import dev.core.stat.StatType;

public class EquipmentManager {

	private Map<EquipmentSlot, RPGItem> equippedActiveItems;
	private List<RPGItem> inventoryPassiveItems; // Items in inventory that provide passive stats
	private RPGEntity holder;

	// Track applied stat modifiers for easy removal
	private Map<EquipmentSlot, List<StatModifier>> appliedActiveStats;
	private Map<RPGItem, List<StatModifier>> appliedPassiveStats;

	/**
	 * Ability ID, EventAction ID
	 */

	private Map<String, String> registeredAutoAbilities;

	private EventBusInterface eventBus;
	private EffectManagerInterface effectManager;

	public EquipmentManager(RPGEntity holder, EventBusInterface eventBusInterface,
			EffectManagerInterface effectManagerInterface) {
		this.eventBus = eventBusInterface;
		this.effectManager = effectManagerInterface;
		this.equippedActiveItems = new HashMap<>();
		this.inventoryPassiveItems = new ArrayList<>();
		this.holder = holder;
		this.appliedActiveStats = new HashMap<>();
		this.appliedPassiveStats = new HashMap<>();
		this.registeredAutoAbilities = new HashMap<String, String>();
	}

	/**
	 * Equip an item to a specific slot
	 */
	public void equipItem(EquipmentSlot slot, RPGItem item) {
		// Unequip current item if present
		if (equippedActiveItems.containsKey(slot)) {
			unequipItem(slot);
		}

		// Remove item from passive inventory if it was there
		if (inventoryPassiveItems.contains(item)) {
			removeFromInventory(item);
		}

		// Equip the new item
		equippedActiveItems.put(slot, item);

		// Apply active stats from the equipped item
		applyActiveStats(slot, item);

		// Register automatic abilities for this equipped item
		registerAutomaticAbilities(item);
	}

	/**
	 * Unequip an item from a specific slot
	 */
	public void unequipItem(EquipmentSlot slot) {
		RPGItem currentItem = equippedActiveItems.get(slot);
		if (currentItem == null) {
			return; // Nothing to unequip
		}

		// Remove active stats
		removeActiveStats(slot);

		// Unregister automatic abilities
		unregisterAutomaticAbilities(currentItem);

		// Remove from equipped items
		equippedActiveItems.remove(slot);

		// Optionally add back to inventory for passive stats
		addToInventory(currentItem);
	}

	/**
	 * Add an item to inventory (provides passive stats)
	 */
	public void addToInventory(RPGItem item) {
		if (!inventoryPassiveItems.contains(item)) {
			inventoryPassiveItems.add(item);
			applyPassiveStats(item);
		}
	}

	/**
	 * Remove an item from inventory
	 */
	public void removeFromInventory(RPGItem item) {
		if (inventoryPassiveItems.remove(item)) {
			removePassiveStats(item);
		}
	}

	/**
	 * Trigger manual abilities based on player action
	 */
	public void triggerAbility(AbilityAction abilityAction) {
		// Check equipped items for manual abilities
		for (Map.Entry<EquipmentSlot, RPGItem> entry : equippedActiveItems.entrySet()) {
			RPGItem item = entry.getValue();

			// Filter manual abilities that match the action
			List<Ability> manualAbilities = getManualAbilities(item, abilityAction);

			for (Ability ability : manualAbilities) {
				triggerSingleAbility(ability, effectManager);
			}
		}
	}

	/**
	 * Get all automatic abilities from equipped and inventory items
	 */
	public List<Ability> getAutomaticAbilities() {
		List<Ability> automaticAbilities = new ArrayList<>();

		// Check equipped items
		for (RPGItem item : equippedActiveItems.values()) {
			automaticAbilities.addAll(getAutomaticAbilities(item));
		}

		// Check inventory items
		for (RPGItem item : inventoryPassiveItems) {
			automaticAbilities.addAll(getAutomaticAbilities(item));
		}

		return automaticAbilities;
	}

	/**
	 * Get equipped item in specific slot
	 */
	public RPGItem getEquippedItem(EquipmentSlot slot) {
		return equippedActiveItems.get(slot);
	}

	/**
	 * Get all equipped items
	 */
	public Map<EquipmentSlot, RPGItem> getAllEquippedItems() {
		return new HashMap<>(equippedActiveItems);
	}

	/**
	 * Get all inventory items
	 */
	public List<RPGItem> getInventoryItems() {
		return new ArrayList<>(inventoryPassiveItems);
	}

	// ========================================================= PRIVATE HELPER
	// METHODS ======================================

	private void applyActiveStats(EquipmentSlot slot, RPGItem item) {
		List<StatModifier> activeStats = item.getActiveStats();
		if (activeStats.isEmpty()) {
			return;
		}

		// Apply stats to the holder
		for (StatModifier statModifier : activeStats) {
			holder.getStatManager().addStatModifier(statModifier);
		}

		// Track applied stats for removal
		appliedActiveStats.put(slot, new ArrayList<StatModifier>(activeStats));
	}

	private void removeActiveStats(EquipmentSlot slot) {
		List<StatModifier> statsToRemove = appliedActiveStats.get(slot);
		if (statsToRemove == null) {
			return;
		}

		// Remove stats from holder

		for (StatModifier statModifier : statsToRemove) {
			holder.getStatManager().removeStatModifier(statModifier);
		}

		// Clear tracking
		appliedActiveStats.remove(slot);
	}

	private void applyPassiveStats(RPGItem item) {
		List<StatModifier> passiveStats = item.getPassiveStats();
		if (passiveStats.isEmpty()) {
			return;
		}

		// Apply stats to the holder
		for (StatModifier statModifier : passiveStats) {
			holder.getStatManager().addStatModifier(statModifier);
		}

		// Track applied stats for removal
		appliedPassiveStats.put(item, new ArrayList<>(passiveStats));
	}

	private void removePassiveStats(RPGItem item) {
		List<StatModifier> statsToRemove = appliedPassiveStats.get(item);
		if (statsToRemove == null) {
			return;
		}

		// Remove stats from holder
		for (StatModifier statModifier : statsToRemove) {
			holder.getStatManager().removeStatModifier(statModifier);
		}

		// Clear tracking
		appliedPassiveStats.remove(item);
	}

	private List<Ability> getManualAbilities(RPGItem item, AbilityAction action) {
		return item.getAbilities().stream().filter(ability -> ability.getTriggerType() == AbilityTriggerType.MANUAL)
				.filter(ability -> abilityMatchesAction(ability, action)).collect(java.util.stream.Collectors.toList());
	}

	private List<Ability> getAutomaticAbilities(RPGItem item) {
		return item.getAbilities().stream().filter(ability -> ability.getTriggerType() == AbilityTriggerType.AUTOMATIC)
				.collect(java.util.stream.Collectors.toList());
	}

	private boolean abilityMatchesAction(Ability ability, AbilityAction action) {
		return ability.getAction() == action;
	}

	private void registerAutomaticAbilities(RPGItem item) {
		List<Ability> autoAbilities = getAutomaticAbilities(item);
		for (Ability ability : autoAbilities) {
			EventAction<? extends Event> eventAction = new EventAction<>(t -> {
				triggerSingleAbility(ability, effectManager);
			}, ability.getTriggerEvent().getClass());
			registeredAutoAbilities.put(ability.getId(), eventAction.getId());
			eventBus.subscribe(eventAction);
		}
	}

	private void unregisterAutomaticAbilities(RPGItem item) {
		// Unregister automatic abilities when item is unequipped
		List<Ability> autoAbilities = getAutomaticAbilities(item);
		for (Ability ability : autoAbilities) {
			eventBus.unsubscribe(registeredAutoAbilities.get(ability.getId()));
		}
	}

	private void triggerSingleAbility(Ability ability, EffectManagerInterface effectManagerInterface) {
		if (!effectManagerInterface.canActivate(holder, ability)) {
			return;
		}

		Effect castedEffect = effectManagerInterface.cast(holder, ability);
		if (castedEffect.getCancelEvent() != null) {
			EventAction<? extends Event> eventAction = new EventAction<>(t -> {
				castedEffect.cancel();
			}, castedEffect.getCancelEvent().getClass());
			eventBus.subscribeOnce(eventAction);
		}

	}

	// ========================================== UTILITY
	// =========================================================================

	/**
	 * Get total stat value including all bonuses from equipped and inventory items
	 */
	public double getTotalStatValue(String statName) {
		double baseValue = holder.getStatManager().getCurrentValue(StatType.valueOf(statName),
				System.currentTimeMillis());
		double totalModifiers = 0;

		// Add active stat bonuses from equipped items
		for (List<StatModifier> activeStats : appliedActiveStats.values()) {
			for (StatModifier statModifier : activeStats) {
				if (statModifier.statType == StatType.valueOf(statName)) {
					totalModifiers += statModifier.amount;
				}
			}
		}

		// Add passive stat bonuses from inventory items
		for (List<StatModifier> activeStats : appliedPassiveStats.values()) {
			for (StatModifier statModifier : activeStats) {
				if (statModifier.statType == StatType.valueOf(statName)) {
					totalModifiers += statModifier.amount;
				}
			}
		}

		return baseValue + totalModifiers;
	}

	/**
	 * Check if a specific item is equipped
	 */
	public boolean isEquipped(RPGItem item) {
		return equippedActiveItems.containsValue(item);
	}

	/**
	 * Check if a specific item is in inventory
	 */
	public boolean isInInventory(RPGItem item) {
		return inventoryPassiveItems.contains(item);
	}

	/**
	 * Get the slot where an item is equipped (null if not equipped)
	 */
	public EquipmentSlot getEquippedSlot(RPGItem item) {
		for (Map.Entry<EquipmentSlot, RPGItem> entry : equippedActiveItems.entrySet()) {
			if (entry.getValue().equals(item)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public void tick(long now) {
		// TODO: Update items in inventory
	}

}
