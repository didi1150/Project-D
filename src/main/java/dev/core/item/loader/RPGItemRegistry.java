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

        // Example usage of builder pattern:
        // addItem(RPGItem.builder("bonemerang", "Bonemerang", "BONE", EquipmentSlot.MAIN_HAND)
        //     .addAbility(new SwingBoneAbility())
        //     .addActiveStat(StatModifier.builder(20, StatModifierType.FLAT, StatType.ATTACK_DAMAGE, "BONEMERANG")
        //         .priority(10)
        //         .build())
        //     .build());
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

    public Map<String, RPGItem> allItems() {
        return new HashMap<String, RPGItem>(allItems);
    }

}
