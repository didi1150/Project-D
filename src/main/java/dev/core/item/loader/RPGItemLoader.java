package dev.core.item.loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.ModifierStackPolicy;
import dev.core.stat.ModifierType;
import dev.core.stat.StatModifier;
import dev.core.stat.StatTarget;
import dev.core.stat.StatType;
import dev.core.storage.config.ConfigProvider;
import dev.core.storage.config.ConfigSection;

public class RPGItemLoader {

    public static Map<String, RPGItem> loadAll(ConfigProvider provider) {
        Map<String, RPGItem> items = new HashMap<>();
        ConfigSection root = provider.getRoot().getSection("items");

        for (String id : root.getKeys()) {
            RPGItem item = load(id, root.getSection(id));
            items.put(id, item);
        }

        return items;
    }

    public static RPGItem load(String id, ConfigSection section) {
        String name = section.getString("name", id);
        String material = section.getString("material", "STONE");
        EquipmentSlot slot = EquipmentSlot.valueOf(section.getString("slot", "MAIN_HAND"));

        List<StatModifier> passive = new ArrayList<>();
        for (ConfigSection s : section.getSectionList("passive-stats")) {
            double amount = s.getDouble("amount", 0);
            ModifierStackPolicy policy = ModifierStackPolicy.valueOf(s.getString("policy", "STACK"));
            ModifierType modifierType = ModifierType.valueOf(s.getString("modifierType", "FLAT"));
            StatType statType = StatType.valueOf(s.getString("statType", StatType.ATTACK_DAMAGE.name()));
            StatTarget statTarget = StatTarget.valueOf(s.getString("statTarget", "BOTH"));

            passive.add(new StatModifier(amount, policy, modifierType, statType, id, -1, System.currentTimeMillis(),
                    statTarget));
        }

        List<StatModifier> active = new ArrayList<>();
        for (ConfigSection s : section.getSectionList("active-stats")) {
            double amount = s.getDouble("amount", 0);
            ModifierStackPolicy policy = ModifierStackPolicy.valueOf(s.getString("policy", "STACK"));
            ModifierType modifierType = ModifierType.valueOf(s.getString("modifierType", "FLAT"));
            StatType statType = StatType.valueOf(s.getString("statType", StatType.ATTACK_DAMAGE.name()));
            StatTarget statTarget = StatTarget.valueOf(s.getString("statTarget", "BOTH"));

            active.add(new StatModifier(amount, policy, modifierType, statType, id, -1, System.currentTimeMillis(),
                    statTarget));
        }

        List<Ability> abilities = new ArrayList<>();
        for (String abilityId : section.getStringList("abilities")) {
            AbilityRegistry.get(abilityId).ifPresent(abilities::add);
        }

        return RPGItem.builder(id, name, slot).withMaterial(material).withPassiveStats(passive).withActiveStats(active)
                .withAbilities(abilities).build();
    }

    public static void saveAll(ConfigProvider provider, Map<String, RPGItem> items) {
        ConfigSection root = provider.getRoot().getSection("items");

        for (RPGItem item : items.values()) {
            ConfigSection section = root.getSection(item.getId());
            section.set("name", item.getName());
            section.set("material", item.getMaterial());
            section.set("slot", item.getEquipmentSlot().name());

            List<Map<String, Object>> passive = new ArrayList<>();
            for (StatModifier sm : item.getPassiveStats()) {
                Map<String, Object> map = new HashMap<>();
                map.put("amount", sm.amount);
                map.put("policy", sm.stackPolicy.name());
                map.put("modifierType", sm.modifierType.name());
                map.put("statType", sm.statType.name());
                map.put("statTarget", sm.statTarget.name());
                passive.add(map);
            }
            section.set("passive-stats", passive);

            List<Map<String, Object>> active = new ArrayList<>();
            for (StatModifier sm : item.getActiveStats()) {
                Map<String, Object> map = new HashMap<>();
                map.put("amount", sm.amount);
                map.put("policy", sm.stackPolicy.name());
                map.put("modifierType", sm.modifierType.name());
                map.put("statType", sm.statType.name());
                map.put("statTarget", sm.statTarget.name());
                active.add(map);
            }
            section.set("active-stats", active);

            List<String> abilityIds = item.getAbilities().stream().map(Ability::getId).toList();
            section.set("abilities", abilityIds);
        }

        provider.save();
    }

    public static void saveItem(ConfigProvider provider, RPGItem item) {
        ConfigSection root = provider.getRoot().getSection("items");

        ConfigSection section = root.getSection(item.getId());
        section.set("name", item.getName());
        section.set("material", item.getMaterial());
        section.set("slot", item.getEquipmentSlot().name());

        List<Map<String, Object>> passive = new ArrayList<>();
        for (StatModifier sm : item.getPassiveStats()) {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", sm.amount);
            map.put("policy", sm.stackPolicy.name());
            map.put("modifierType", sm.statType.name());
            map.put("statType", sm.statType.name());
            map.put("statTarget", sm.statTarget.name());
            passive.add(map);
        }
        section.set("passive-stats", passive);

        List<Map<String, Object>> active = new ArrayList<>();
        for (StatModifier sm : item.getActiveStats()) {
            Map<String, Object> map = new HashMap<>();
            map.put("amount", sm.amount);
            map.put("policy", sm.stackPolicy.name());
            map.put("modifierType", sm.statType.name());
            map.put("statType", sm.statType.name());
            map.put("statTarget", sm.statTarget.name());
            active.add(map);
        }
        section.set("active-stats", active);

        List<String> abilityIds = item.getAbilities().stream().map(Ability::getId).toList();
        section.set("abilities", abilityIds);
        provider.save();
    }
}
