package dev.core.item.equipment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import dev.core.ability.Ability;
import dev.core.ability.AbilityAction;
import dev.core.ability.AbilityTriggerType;
import dev.core.ability.Effect;
import dev.core.ability.EffectManagerInterface;
import dev.core.ability.SetBonus;
import dev.core.entity.RPGEntity;
import dev.core.event.Event;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;
import dev.core.stat.StatModifier;
import dev.core.stat.StatType;

public class EquipmentManager {

	private final Map<EquipmentSlot, RPGItem> equippedActiveItems;
	private final List<RPGItem> inventoryPassiveItems; // Items in inventory that provide passive stats
	private final RPGEntity holder;

	// Track applied stat modifiers for easy removal
	private final Map<EquipmentSlot, List<StatModifier>> appliedActiveStats;
	private final Map<RPGItem, List<StatModifier>> appliedPassiveStats;

	private final Map<RPGItemSet, Integer> activeSetCounts;
	private final Map<RPGItemSet, SetBonus> appliedBonuses;

	/**
	 * Ability ID, EventAction ID
	 */

	private final Map<String, String> registeredAutoAbilities;
	private final List<Ability> temporaryAbilities;

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
		this.temporaryAbilities = new ArrayList<Ability>();
		this.activeSetCounts = new HashMap<RPGItemSet, Integer>();
		this.appliedBonuses = new HashMap<RPGItemSet, SetBonus>();
	}

	/**
	 * Equip an item to a specific slot
	 */
	public void equipItem(EquipmentSlot slot, RPGItem item) {
		// Unequip current item if present
		if (equippedActiveItems.containsKey(slot)) {
			unequipItem(slot);
		}

		if (item == null) {
			return;
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

		recalcSets();
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

		// add back to inventory for passive stats
		addToInventory(currentItem);

		recalcSets();
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

	public void addTemporaryAbility(Ability ability) {
		if (!temporaryAbilities.contains(ability)) {
			temporaryAbilities.add(ability);

			if (ability.getTriggerType() == AbilityTriggerType.AUTOMATIC) {
				EventAction<? extends Event> eventAction = new EventAction<>(event -> {
					triggerSingleAbility(ability, effectManager);
				}, ability.getTriggerEvent().getClass());
				registeredAutoAbilities.put(ability.getId(), eventAction.getId());
				eventBus.subscribe(eventAction);
			}
		}
	}

	public void removeTemporaryAbility(Ability ability) {
		temporaryAbilities.remove(ability);
		if (ability.getTriggerType() == AbilityTriggerType.AUTOMATIC) {
			if (registeredAutoAbilities.containsKey(ability.getId())) {
				eventBus.unsubscribe(registeredAutoAbilities.get(ability.getId()));
			}
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

		// Trigger set abilities
		for (Ability setAbility : temporaryAbilities) {
			if (setAbility.getTriggerType() == AbilityTriggerType.MANUAL && setAbility.getAction() == abilityAction) {
				triggerSingleAbility(setAbility, effectManager);
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

	private void recalcSets() {
		// 1. Count pieces per set
		Map<RPGItemSet, Integer> counts = new HashMap<>();
		for (RPGItem item : equippedActiveItems.values()) {
			item.getItemSet().ifPresent(set -> {
				counts.put(set, counts.getOrDefault(set, 0) + 1);
			});
		}

		// 2. Remove bonuses that no longer apply
		for (Iterator<Map.Entry<RPGItemSet, SetBonus>> it = appliedBonuses.entrySet().iterator(); it.hasNext();) {
			Map.Entry<RPGItemSet, SetBonus> entry = it.next();
			RPGItemSet set = entry.getKey();
			SetBonus bonus = entry.getValue();

			int currentCount = counts.getOrDefault(set, 0);
			if (set.getBonusForPieces(currentCount).orElse(null) != bonus) {
				bonus.remove(holder); // remove stats + abilities
				it.remove();
			}
		}

		// 3. Apply new bonuses
		for (Map.Entry<RPGItemSet, Integer> entry : counts.entrySet()) {
			RPGItemSet set = entry.getKey();
			int pieceCount = entry.getValue();

			set.getBonusForPieces(pieceCount).ifPresent(bonus -> {
				if (!appliedBonuses.containsKey(set)) {
					bonus.apply(holder); // apply stats + abilities
					appliedBonuses.put(set, bonus);
				}
			});
		}

		// 4. Update active counts
		activeSetCounts.clear();
		activeSetCounts.putAll(counts);
	}

	private void applyActiveStats(EquipmentSlot slot, RPGItem item) {
		List<StatModifier> activeStats = item.getActiveStats();
//		System.out.println("Applied stats from slot " + slot.name() + " (" + activeStats.size() + ")");
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
