package dev.core.item.loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.core.ability.Ability;
import dev.core.ability.AbilityRegistry;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.RPGItem;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.stat.StatTarget;
import dev.core.stat.StatType;
import dev.core.stat.modifier.ModifierStackPolicy;
import dev.core.stat.modifier.StatModifierType;
import dev.core.stat.modifier.StatModifier;
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
            StatModifierType statModifierType = StatModifierType
                    .valueOf(s.getString("statModifierType", s.getString("modifierType", "FLAT")));
            StatType statType = StatType.valueOf(s.getString("statType", StatType.ATTACK_DAMAGE.name()));
            StatTarget statTarget = StatTarget.valueOf(s.getString("statTarget", "BOTH"));
            int priority = s.getInt("priority", 0);

            passive.add(StatModifier.builder(amount, statModifierType, statType, id)
                .stackPolicy(policy)
                .statTarget(statTarget)
                .priority(priority)
                .build());
        }

        List<StatModifier> active = new ArrayList<>();
        for (ConfigSection s : section.getSectionList("active-stats")) {
            double amount = s.getDouble("amount", 0);
            ModifierStackPolicy policy = ModifierStackPolicy.valueOf(s.getString("policy", "STACK"));
            StatModifierType statModifierType = StatModifierType
                    .valueOf(s.getString("statModifierType", s.getString("modifierType", "FLAT")));
            StatType statType = StatType.valueOf(s.getString("statType", StatType.ATTACK_DAMAGE.name()));
            StatTarget statTarget = StatTarget.valueOf(s.getString("statTarget", "BOTH"));
            int priority = s.getInt("priority", 0);

            active.add(StatModifier.builder(amount, statModifierType, statType, id)
                .stackPolicy(policy)
                .statTarget(statTarget)
                .priority(priority)
                .build());
        }

        List<Ability> abilities = new ArrayList<>();
        for (String abilityId : section.getStringList("abilities")) {
            AbilityRegistry.getOrWarn(abilityId, "item " + id).ifPresent(abilities::add);
        }

        RPGClassType classType = RPGClassType.valueOf(section.getString("classType", RPGClassType.NONE.name()));
        int unlockLevel = section.getInt("unlockLevel", 0);

        List<RPGClassType> allowedClasses = new ArrayList<>();
        for (String className : section.getStringList("allowed-classes")) {
            try {
                allowedClasses.add(RPGClassType.valueOf(className.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                System.out.println("Unknown allowed class '" + className + "' for item " + id + "; ignored.");
            }
        }
        boolean mobOnly = section.getBoolean("mob-only", false);

        return RPGItem.builder(id, name, slot).withMaterial(material).withPassiveStats(passive).withActiveStats(active)
                .withAbilities(abilities).withRpgClassType(classType).withUnlockLevel(unlockLevel)
                .withAllowedClasses(allowedClasses).mobOnly(mobOnly).build();
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
                map.put("statModifierType", sm.statModifierType.name());
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
                map.put("statModifierType", sm.statModifierType.name());
                map.put("statType", sm.statType.name());
                map.put("statTarget", sm.statTarget.name());
                active.add(map);
            }
            section.set("active-stats", active);

            List<String> abilityIds = item.getAbilities().stream().map(Ability::getId).toList();
            section.set("abilities", abilityIds);

            section.set("classType", item.getRpgClassType().name());
            section.set("unlockLevel", item.getUnlockLevel());
            if (!item.getAllowedClasses().isEmpty()) {
                section.set("allowed-classes", item.getAllowedClasses().stream().map(Enum::name).toList());
            }
            if (item.isMobOnly()) {
                section.set("mob-only", true);
            }
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
            map.put("statModifierType", sm.statModifierType.name());
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
            map.put("statModifierType", sm.statModifierType.name());
            map.put("statType", sm.statType.name());
            map.put("statTarget", sm.statTarget.name());
            active.add(map);
        }
        section.set("active-stats", active);

        List<String> abilityIds = item.getAbilities().stream().map(Ability::getId).toList();
        section.set("abilities", abilityIds);
        
        section.set("classType", item.getRpgClassType().name());
        section.set("unlockLevel", item.getUnlockLevel());
        if (!item.getAllowedClasses().isEmpty()) {
            section.set("allowed-classes", item.getAllowedClasses().stream().map(Enum::name).toList());
        }
        if (item.isMobOnly()) {
            section.set("mob-only", true);
        }
        provider.save();
    }
}
